package co.edu.unbosque.marketplaceInversionesLR.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoIdempotencia;
import co.edu.unbosque.marketplaceInversionesLR.entity.RegistroIdempotencia;
import co.edu.unbosque.marketplaceInversionesLR.repository.RegistroIdempotenciaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Idempotencia con la cabecera {@code Idempotency-Key} (RNF-008, CA-140,
 * CA-141): si el cliente reenvía la misma petición, por un doble clic o un
 * reintento, la operación no se hace dos veces y se responde lo mismo que la
 * primera vez. Por ahora la usa la cancelación de reservas (HU-01).
 * <p>
 * Uso desde un controlador:
 * {@code return idempotenciaService.ejecutar(idUsuario, "CANCELAR_RESERVA", clave, peticion, () -> cancelar());}
 * </p>
 * <p>
 * Reglas, iguales a las del proyecto base: la primera vez con una clave se
 * ejecuta la acción y se guarda su respuesta. Con la misma clave y los mismos
 * datos se devuelve la respuesta guardada sin repetir la acción, con la
 * cabecera {@code Idempotent-Replayed: true}. Con la misma clave y otros datos
 * responde 422. Si la primera petición sigue en curso responde 409. Si la
 * acción falla con una excepción o con un 5xx, la clave se libera para poder
 * reintentar.
 * </p>
 */
@Service
public class IdempotenciaService {

	/** Nombre de la cabecera que manda el cliente. */
	public static final String CABECERA = "Idempotency-Key";

	/** Cabecera que se agrega cuando se devuelve una respuesta guardada. */
	public static final String CABECERA_REPETIDA = "Idempotent-Replayed";

	/** Largo máximo de la clave (columna de Neon). */
	public static final int MAX_CLAVE = 100;

	/** Mensaje del 400 cuando falta la clave o es muy larga. */
	public static final String CLAVE_INVALIDA = "La cabecera Idempotency-Key es obligatoria y debe tener entre 1 y "
			+ MAX_CLAVE + " caracteres.";

	/** Mensaje del 422 cuando la clave se reusa con otros datos. */
	public static final String CLAVE_REUTILIZADA = "Esta Idempotency-Key ya se usó con una petición diferente.";

	/** Mensaje del 409 cuando la primera petición con la clave sigue en curso. */
	public static final String EN_PROCESO = "La petición con esta Idempotency-Key todavía está en proceso.";

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(IdempotenciaService.class);

	/** Repositorio de los registros de idempotencia. */
	private final RegistroIdempotenciaRepository registroRepo;

	/** Transacción independiente (REQUIRES_NEW) para registrar y liberar las claves. */
	private final TransactionTemplate transaccionPropia;

	/** Convierte las peticiones y respuestas a JSON. */
	private final ObjectMapper objectMapper;

	/**
	 * Crea el servicio y prepara la transacción propia.
	 *
	 * @param registroRepo       repositorio de registros de idempotencia
	 * @param transactionManager administrador de transacciones de Spring
	 * @param objectMapper       conversor a JSON
	 */
	public IdempotenciaService(RegistroIdempotenciaRepository registroRepo,
			PlatformTransactionManager transactionManager, ObjectMapper objectMapper) {
		this.registroRepo = registroRepo;
		this.objectMapper = objectMapper;
		this.transaccionPropia = new TransactionTemplate(transactionManager);
		this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Ejecuta una acción como máximo una vez por clave.
	 *
	 * @param idUsuario usuario autenticado (sale del token JWT)
	 * @param operacion nombre de la operación, por ejemplo CANCELAR_RESERVA
	 * @param clave     valor de la cabecera Idempotency-Key
	 * @param peticion  datos de la petición; se usan para detectar la misma
	 *                  clave con otros datos
	 * @param accion    lo que hay que hacer la primera vez
	 * @return la respuesta de la acción, o la guardada si la clave ya se usó
	 */
	public ResponseEntity<Object> ejecutar(Long idUsuario, String operacion, String clave, Object peticion,
			Supplier<ResponseEntity<Object>> accion) {
		if (clave == null || clave.isBlank() || clave.length() > MAX_CLAVE) {
			return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, CLAVE_INVALIDA);
		}

		String huella = huella(peticion);
		Optional<RegistroIdempotencia> existente = registroRepo.findByIdUsuarioAndOperacionAndClave(idUsuario,
				operacion, clave);
		if (existente.isPresent()) {
			return responderExistente(existente.get(), huella);
		}

		RegistroIdempotencia registro;
		try {
			registro = transaccionPropia
					.execute(estado -> registroRepo.saveAndFlush(nuevoRegistro(idUsuario, operacion, clave, huella)));
		} catch (DataIntegrityViolationException e) {
			// Otra petición con la misma clave se registró en el mismo instante
			return RespuestaUtil.mensaje(HttpStatus.CONFLICT, EN_PROCESO);
		}

		ResponseEntity<Object> respuesta;
		try {
			respuesta = accion.get();
		} catch (RuntimeException e) {
			liberar(registro);
			throw e;
		}

		if (respuesta.getStatusCode().is5xxServerError()) {
			liberar(registro);
		} else {
			guardarRespuesta(registro, respuesta);
		}
		return respuesta;
	}

	/**
	 * Responde a una clave que ya se usó: 422 si los datos son otros, 409 si
	 * la petición sigue en curso, o la respuesta guardada si ya terminó.
	 *
	 * @param registro registro de la clave
	 * @param huella   huella de la petición actual
	 * @return respuesta para el cliente
	 */
	private ResponseEntity<Object> responderExistente(RegistroIdempotencia registro, String huella) {
		if (!registro.getHuellaPeticion().equals(huella)) {
			return RespuestaUtil.mensaje(HttpStatus.UNPROCESSABLE_ENTITY, CLAVE_REUTILIZADA);
		}
		if (registro.getEstado() == EstadoIdempotencia.EN_PROCESO) {
			return RespuestaUtil.mensaje(HttpStatus.CONFLICT, EN_PROCESO);
		}

		Object cuerpo = null;
		if (registro.getCuerpoRespuesta() != null) {
			try {
				cuerpo = objectMapper.readTree(registro.getCuerpoRespuesta());
			} catch (JacksonException e) {
				LOG.error("No se pudo leer la respuesta guardada del registro {}", registro.getIdRegistro(), e);
			}
		}
		return ResponseEntity.status(registro.getCodigoRespuesta()).header(CABECERA_REPETIDA, "true")
				.contentType(MediaType.APPLICATION_JSON).body(cuerpo);
	}

	/**
	 * Arma el registro de una clave nueva, en estado EN_PROCESO.
	 *
	 * @param idUsuario usuario
	 * @param operacion operación protegida
	 * @param clave     valor de la cabecera
	 * @param huella    huella de la petición
	 * @return registro sin guardar
	 */
	private static RegistroIdempotencia nuevoRegistro(Long idUsuario, String operacion, String clave, String huella) {
		RegistroIdempotencia registro = new RegistroIdempotencia();
		registro.setIdUsuario(idUsuario);
		registro.setOperacion(operacion);
		registro.setClave(clave);
		registro.setHuellaPeticion(huella);
		registro.setEstado(EstadoIdempotencia.EN_PROCESO);
		registro.setFechaCreacion(LocalDateTime.now());
		return registro;
	}

	/**
	 * Guarda la respuesta de la acción en el registro. Si no se puede guardar
	 * el cuerpo, igual marca el registro como completado, porque la acción ya
	 * se hizo.
	 *
	 * @param registro  registro de la clave
	 * @param respuesta respuesta de la acción
	 */
	private void guardarRespuesta(RegistroIdempotencia registro, ResponseEntity<Object> respuesta) {
		String cuerpo = null;
		try {
			cuerpo = respuesta.getBody() == null ? null : objectMapper.writeValueAsString(respuesta.getBody());
		} catch (JacksonException e) {
			LOG.error("No se pudo convertir la respuesta de la operación {}", registro.getOperacion(), e);
		}
		try {
			completar(registro, respuesta, cuerpo);
		} catch (DataAccessException e) {
			// La acción ya se hizo: no se convierte en error; se guarda el registro sin el cuerpo
			LOG.error("No se pudo guardar la respuesta de la operación {}", registro.getOperacion(), e);
			completar(registro, respuesta, null);
		}
	}

	/**
	 * Marca el registro como completado con el código y el cuerpo de la
	 * respuesta. Se actualiza la fila leída en esta transacción, no la copia de
	 * la transacción anterior.
	 *
	 * @param registro  registro de la clave
	 * @param respuesta respuesta de la acción
	 * @param cuerpo    cuerpo de la respuesta en JSON; puede ser {@code null}
	 */
	private void completar(RegistroIdempotencia registro, ResponseEntity<Object> respuesta, String cuerpo) {
		transaccionPropia
				.executeWithoutResult(estado -> registroRepo.findById(registro.getIdRegistro()).ifPresent(guardado -> {
					guardado.setEstado(EstadoIdempotencia.COMPLETADA);
					guardado.setCodigoRespuesta(respuesta.getStatusCode().value());
					guardado.setCuerpoRespuesta(cuerpo);
				}));
	}

	/**
	 * Borra el registro para que la clave se pueda volver a usar.
	 *
	 * @param registro registro a borrar
	 */
	private void liberar(RegistroIdempotencia registro) {
		transaccionPropia.executeWithoutResult(estado -> registroRepo.deleteById(registro.getIdRegistro()));
	}

	/**
	 * SHA-256 de los datos de la petición, para detectar si la clave se reusa
	 * con otros datos.
	 *
	 * @param peticion datos de la petición
	 * @return huella en hexadecimal (64 caracteres)
	 * @throws IllegalStateException si no se puede calcular
	 */
	private String huella(Object peticion) {
		try {
			byte[] datos = objectMapper.writeValueAsString(peticion).getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
		} catch (JacksonException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("No se pudo calcular la huella de la petición", e);
		}
	}
}
