package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Registro de auditoría (RNF-006). Cada evento se guarda en la tabla
 * {@code evento_auditoria} y además se escribe como una línea JSON en el log con la
 * marca {@code AUDITORIA}.
 * <p>
 * El guardado usa una transacción propia (REQUIRES_NEW): así el evento queda
 * aunque la operación auditada se revierta, y también se guardan las
 * operaciones rechazadas. Si la auditoría falla, la operación auditada no se
 * interrumpe; el evento sigue quedando en el log.
 * </p>
 */
@Service
public class AuditoriaService {

	/** Un usuario se registró. */
	public static final String REGISTRO_USUARIO = "REGISTRO_USUARIO";

	/** Se envió un código de verificación del correo. */
	public static final String CODIGO_VERIFICACION_ENVIADO = "CODIGO_VERIFICACION_ENVIADO";

	/** Un usuario confirmó su correo. */
	public static final String CORREO_VERIFICADO = "CORREO_VERIFICADO";

	/** Entidad auditada: usuario. */
	public static final String ENTIDAD_USUARIO = "USUARIO";

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(AuditoriaService.class);

	/** Marca de las líneas de auditoría (una línea JSON por evento), para filtrarlas en el log. */
	private static final Marker AUDITORIA = MarkerFactory.getMarker("AUDITORIA");

	/** Largo máximo del detalle (columna de 1000 caracteres). */
	private static final int MAX_DETALLE = 1000;

	/** Repositorio de solo inserción de la auditoría. */
	private final AuditoriaRepository auditoriaRepo;

	/** Transacción independiente para guardar el evento aunque la operación se revierta. */
	private final TransactionTemplate transaccionPropia;

	/** Convierte el evento a JSON para el log. */
	private final ObjectMapper objectMapper;

	/**
	 * Crea el servicio y prepara la transacción propia (REQUIRES_NEW).
	 *
	 * @param auditoriaRepo      repositorio de la auditoría
	 * @param transactionManager administrador de transacciones de Spring
	 * @param objectMapper       conversor a JSON
	 */
	public AuditoriaService(AuditoriaRepository auditoriaRepo, PlatformTransactionManager transactionManager,
			ObjectMapper objectMapper) {
		this.auditoriaRepo = auditoriaRepo;
		this.objectMapper = objectMapper;
		this.transaccionPropia = new TransactionTemplate(transactionManager);
		this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Registra un evento de auditoría.
	 *
	 * @param idActor   usuario que hizo la acción; null si no se conoce
	 * @param accion    acción auditada (constantes de esta clase)
	 * @param entidad   tipo de recurso afectado; puede ser null
	 * @param idEntidad id del recurso afectado; puede ser null
	 * @param resultado EXITOSO, RECHAZADO o ERROR
	 * @param detalle   descripción corta, sin contraseñas ni datos privados
	 */
	public void registrar(Long idActor, String accion, String entidad, Long idEntidad, ResultadoAuditoria resultado,
			String detalle) {
		EventoAuditoria evento = new EventoAuditoria();
		evento.setIdUsuario(idActor);
		evento.setAccion(accion);
		evento.setEntidad(entidad);
		evento.setIdEntidad(idEntidad);
		evento.setResultado(resultado);
		evento.setDetalle(recortar(detalle));
		evento.setFecha(LocalDateTime.now());

		escribirLog(evento);

		try {
			transaccionPropia.executeWithoutResult(estado -> auditoriaRepo.save(evento));
		} catch (RuntimeException e) {
			// La auditoría nunca debe tumbar la operación auditada; el evento ya quedó en el log
			LOG.error("No se pudo guardar el evento de auditoría {}", accion, e);
		}
	}

	/**
	 * Escribe el evento como una línea JSON en el log con la marca {@code AUDITORIA}.
	 *
	 * @param evento evento a escribir
	 */
	private void escribirLog(EventoAuditoria evento) {
		Map<String, Object> linea = new LinkedHashMap<>();
		linea.put("fecha", evento.getFecha().toString());
		linea.put("accion", evento.getAccion());
		linea.put("resultado", evento.getResultado());
		linea.put("idActor", evento.getIdUsuario());
		linea.put("entidad", evento.getEntidad());
		linea.put("idEntidad", evento.getIdEntidad());
		linea.put("detalle", evento.getDetalle());
		try {
			LOG.info(AUDITORIA, objectMapper.writeValueAsString(linea));
		} catch (JacksonException e) {
			LOG.error("No se pudo escribir el evento de auditoría {} en el log", evento.getAccion(), e);
		}
	}

	/**
	 * Corta el detalle a {@link #MAX_DETALLE} caracteres para que quepa en la
	 * columna.
	 *
	 * @param detalle detalle original; puede ser null
	 * @return el detalle, recortado si hacía falta
	 */
	private static String recortar(String detalle) {
		if (detalle == null || detalle.length() <= MAX_DETALLE) {
			return detalle;
		}
		return detalle.substring(0, MAX_DETALLE);
	}
}
