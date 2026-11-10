package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.unbosque.marketplaceInversionesLR.dto.EventoAuditoriaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
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
 * <p>
 * Los eventos no se modifican ni se borran (RNF-007): el repositorio solo
 * permite insertar y consultar.
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

	/** Un usuario intentó iniciar sesión. */
	public static final String LOGIN = "LOGIN";

	/** Un usuario pidió recuperar su contraseña. */
	public static final String RECUPERACION_SOLICITADA = "RECUPERACION_SOLICITADA";

	/** Un usuario restableció su contraseña con el token de recuperación. */
	public static final String CONTRASENIA_RESTABLECIDA = "CONTRASENIA_RESTABLECIDA";

	/** Un usuario reactivó su cuenta. */
	public static final String CUENTA_REACTIVADA = "CUENTA_REACTIVADA";

	/** Se activó, confirmó o desactivó el segundo factor. */
	public static final String SEGUNDO_FACTOR = "SEGUNDO_FACTOR";

	/** Se creó un administrador. */
	public static final String CREACION_ADMINISTRADOR = "CREACION_ADMINISTRADOR";

	/** Un usuario actualizó su perfil. */
	public static final String PERFIL_ACTUALIZADO = "PERFIL_ACTUALIZADO";

	/** Un usuario desactivó su cuenta. */
	public static final String CUENTA_DESACTIVADA = "CUENTA_DESACTIVADA";

	/** Un anfitrión creó una publicación de alojamiento. */
	public static final String ALOJAMIENTO_CREADO = "ALOJAMIENTO_CREADO";

	/** Se modificaron los datos de un alojamiento. */
	public static final String ALOJAMIENTO_ACTUALIZADO = "ALOJAMIENTO_ACTUALIZADO";

	/** Un alojamiento cambió de estado (revisión, aprobación, pausa, etc.). */
	public static final String ALOJAMIENTO_CAMBIO_ESTADO = "ALOJAMIENTO_CAMBIO_ESTADO";

	/** Se subió, eliminó o marcó como principal una imagen de un alojamiento. */
	public static final String IMAGEN_ALOJAMIENTO = "IMAGEN_ALOJAMIENTO";

	/** Se agregó, modificó o eliminó un servicio adicional de un alojamiento. */
	public static final String SERVICIO_ADICIONAL = "SERVICIO_ADICIONAL";

	/** Se registró o modificó un periodo de disponibilidad. */
	public static final String DISPONIBILIDAD = "DISPONIBILIDAD";

	/** Se cambiaron los porcentajes globales de limpieza y servicio. */
	public static final String TARIFA_ACTUALIZADA = "TARIFA_ACTUALIZADA";

	/** Un huésped creó una reserva. */
	public static final String RESERVA_CREADA = "RESERVA_CREADA";

	/** Se procesó el pago de una reserva. */
	public static final String PAGO = "PAGO";

	/** Se canceló una reserva. */
	public static final String CANCELACION = "CANCELACION";

	/** Un huésped publicó una reseña. */
	public static final String RESENA_CREADA = "RESENA_CREADA";

	/** Un administrador moderó una reseña. */
	public static final String RESENA_MODERADA = "RESENA_MODERADA";

	/** Un administrador restringió a un usuario. */
	public static final String USUARIO_RESTRINGIDO = "USUARIO_RESTRINGIDO";

	/** Un administrador levantó la restricción de un usuario. */
	public static final String USUARIO_LIBERADO = "USUARIO_LIBERADO";

	/** Se exportó un reporte. */
	public static final String REPORTE_EXPORTADO = "REPORTE_EXPORTADO";

	/** Se rechazó una petición por falta de permisos (403). */
	public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";

	/** Ocurrió un error inesperado del servidor (500). */
	public static final String ERROR_INTERNO = "ERROR_INTERNO";

	/** Entidad auditada: usuario. */
	public static final String ENTIDAD_USUARIO = "USUARIO";

	/** Entidad auditada: alojamiento. */
	public static final String ENTIDAD_ALOJAMIENTO = "ALOJAMIENTO";

	/** Entidad auditada: periodo de disponibilidad. */
	public static final String ENTIDAD_PERIODO = "PERIODO_DISPONIBILIDAD";

	/** Entidad auditada: configuración de tarifas. */
	public static final String ENTIDAD_CONFIGURACION = "CONFIGURACION_TARIFA";

	/** Entidad auditada: reserva. */
	public static final String ENTIDAD_RESERVA = "RESERVA";

	/** Entidad auditada: reseña. */
	public static final String ENTIDAD_RESENA = "RESENA";

	/** Entidad auditada: reporte. */
	public static final String ENTIDAD_REPORTE = "REPORTE";

	/** Tamaño máximo de una página de consulta. */
	public static final int MAX_TAMANIO_PAGINA = 100;

	/** Mensaje cuando el evento consultado no existe. */
	public static final String EVENTO_NO_EXISTE = "El evento de auditoría no existe.";

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
	 * Consulta los eventos guardados, del más reciente al más antiguo (CA-139).
	 * Cada filtro en {@code null} no se aplica. La ruta de consulta para el
	 * administrador es parte de HU-13.
	 *
	 * @param accion  acción exacta, por ejemplo LOGIN
	 * @param idActor usuario que hizo la acción
	 * @param desde   fecha y hora mínima
	 * @param hasta   fecha y hora máxima
	 * @param pagina  número de página, desde 0
	 * @param tamanio eventos por página, entre 1 y {@link #MAX_TAMANIO_PAGINA}
	 * @return página de eventos
	 */
	public Page<EventoAuditoria> consultar(String accion, Long idActor, LocalDateTime desde, LocalDateTime hasta,
			int pagina, int tamanio) {
		int tamanioValido = Math.min(Math.max(tamanio, 1), MAX_TAMANIO_PAGINA);
		Pageable pageable = PageRequest.of(Math.max(pagina, 0), tamanioValido,
				Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC, "idEvento")));
		String accionFiltro = accion == null || accion.isBlank() ? null : accion.trim();
		return auditoriaRepo.buscar(accionFiltro, idActor, desde, hasta, pageable);
	}

	/**
	 * Detalle de un evento (CA-138, CA-139).
	 *
	 * @param idEvento id del evento
	 * @return el evento, si existe
	 */
	public Optional<EventoAuditoria> detalle(Long idEvento) {
		return idEvento == null ? Optional.empty() : auditoriaRepo.findById(idEvento);
	}

	/**
	 * Consulta de la auditoría para el administrador (HU-13, CA-062). Cada filtro
	 * en {@code null} no se aplica.
	 *
	 * @param accion  acción exacta, sin distinguir mayúsculas
	 * @param idActor usuario que hizo la acción
	 * @param desde   fecha y hora mínima
	 * @param hasta   fecha y hora máxima
	 * @param pagina  página y orden
	 * @return página de eventos
	 */
	public PaginaDTO<EventoAuditoriaDTO> consultar(String accion, Long idActor, LocalDateTime desde,
			LocalDateTime hasta, Pageable pagina) {
		String filtroAccion = accion == null || accion.isBlank() ? null : accion.trim().toUpperCase();
		return PaginaDTO.de(auditoriaRepo.buscar(filtroAccion, idActor, desde, hasta, pagina),
				AuditoriaService::convertirDTO);
	}

	/**
	 * Detalle de un evento de auditoría (HU-13, CA-062).
	 *
	 * @param idEvento evento a consultar
	 * @return el evento; o NO_ENCONTRADO si no existe
	 */
	public Resultado<EventoAuditoriaDTO> obtener(Long idEvento) {
		return auditoriaRepo.findById(idEvento).map(e -> Resultado.exito(convertirDTO(e)))
				.orElseGet(() -> Resultado.error(Resultado.NO_ENCONTRADO, EVENTO_NO_EXISTE));
	}

	/**
	 * Convierte un evento a DTO.
	 *
	 * @param evento evento guardado
	 * @return evento para la API
	 */
	private static EventoAuditoriaDTO convertirDTO(EventoAuditoria evento) {
		EventoAuditoriaDTO dto = new EventoAuditoriaDTO();
		dto.setIdEvento(evento.getIdEvento());
		dto.setAccion(evento.getAccion());
		dto.setFecha(evento.getFecha());
		dto.setDetalle(evento.getDetalle());
		dto.setIdUsuario(evento.getIdUsuario());
		dto.setEntidad(evento.getEntidad());
		dto.setIdEntidad(evento.getIdEntidad());
		dto.setResultado(evento.getResultado());
		return dto;
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
