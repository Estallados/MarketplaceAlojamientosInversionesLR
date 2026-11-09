package co.edu.unbosque.marketplaceInversionesLR.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Convierte los errores de la API en respuestas {@code {"message": "..."}},
 * para que el frontend las lea siempre igual (RNF-013, CA-149). Los accesos
 * denegados y los errores inesperados quedan además en la auditoría (HU-26).
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

	/** Mensaje del 403. */
	public static final String SIN_PERMISO = "No tienes permisos para esta operación";

	/** Mensaje del 500. */
	public static final String ERROR_INESPERADO = "Ocurrió un error inesperado. Intenta de nuevo.";

	/** Log de la clase. */
	private static final Logger LOG =LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

	/** Registra en la auditoría los accesos denegados y los errores inesperados. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el manejador.
	 *
	 * @param auditoriaService auditoría de los accesos denegados y los errores
	 */
	public ManejadorGlobalExcepciones(AuditoriaService auditoriaService) {
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Cuerpo que no es un JSON válido o con un valor no permitido, por ejemplo
	 * un rol que no existe.
	 *
	 * @return 400
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Object> cuerpoInvalido() {
		return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST,
				"El cuerpo de la petición no es un JSON válido o tiene un valor no permitido.");
	}

	/**
	 * Método HTTP no permitido en la ruta.
	 *
	 * @param e error de Spring
	 * @return 405
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Object> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
		return RespuestaUtil.mensaje(HttpStatus.METHOD_NOT_ALLOWED, "Método " + e.getMethod() + " no permitido en esta ruta.");
	}

	/**
	 * El cuerpo llegó con un {@code Content-Type} que la ruta no acepta. Se le
	 * dice al cliente cuál debe usar: multipart para las rutas de archivos y
	 * JSON para el resto.
	 *
	 * @param e excepción con los tipos que acepta la ruta
	 * @return 415 con el tipo esperado
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Object> tipoContenidoNoSoportado(HttpMediaTypeNotSupportedException e) {
		String esperado = e.getSupportedMediaTypes().stream().anyMatch(MediaType.MULTIPART_FORM_DATA::includes)
				? "multipart/form-data"
				: "application/json";
		return RespuestaUtil.mensaje(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo debe enviarse como " + esperado + ".");
	}

	/**
	 * Un archivo supera el tamaño máximo configurado para las subidas.
	 *
	 * @return 413 con el límite permitido
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Object> archivoMuyGrande() {
		return RespuestaUtil.mensaje(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño permitido (5 MB por imagen).");
	}

	/**
	 * No se pudo guardar una imagen en Cloudinary.
	 *
	 * @param e excepción con el mensaje para el usuario
	 * @return 503 con el mensaje de la excepción
	 */
	@ExceptionHandler(AlmacenamientoNoDisponibleException.class)
	public ResponseEntity<Object> almacenamientoNoDisponible(AlmacenamientoNoDisponibleException e) {
		return RespuestaUtil.mensaje(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
	}

	/**
	 * La petición multipart no trae un campo obligatorio.
	 *
	 * @param e excepción con el nombre del campo
	 * @return 400 indicando qué campo falta
	 */
	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<Object> parteFaltante(MissingServletRequestPartException e) {
		return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Falta el campo " + e.getRequestPartName() + " con los archivos.");
	}

	/**
	 * La petición de archivos no es un multipart válido.
	 *
	 * @return 400 con el formato esperado
	 */
	@ExceptionHandler(MultipartException.class)
	public ResponseEntity<Object> multipartInvalido() {
		return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Los archivos deben enviarse como multipart/form-data.");
	}

	/**
	 * Ruta inexistente.
	 *
	 * @return 404
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Object> rutaNoEncontrada() {
		return RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, "La ruta solicitada no existe.");
	}

	/**
	 * Violación de una restricción de la base de datos que no controló el
	 * servicio. Si es {@value ReservaService#RESTRICCION_CRUCE_RESERVAS} (dos reservas
	 * confirmadas sobre las mismas fechas, CA-072), lo dice así.
	 *
	 * @param e error de la base de datos
	 * @return 409
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Object> conflictoDeDatos(DataIntegrityViolationException e) {
		String causa = String.valueOf(e.getMostSpecificCause().getMessage());
		LOG.warn("Conflicto de datos: {}", causa);
		if (causa.contains(ReservaService.RESTRICCION_CRUCE_RESERVAS)) {
			return RespuestaUtil.mensaje(HttpStatus.CONFLICT, ReservaService.PERIODO_TOMADO);
		}
		return RespuestaUtil.mensaje(HttpStatus.CONFLICT, "El registro ya existe. Actualiza la página e intenta de nuevo.");
	}

	/**
	 * Un parámetro de la ruta o de la URL no se pudo convertir al tipo esperado
	 * (por ejemplo, un id que no es número o una fecha mal escrita).
	 *
	 * @param e excepción con el nombre del parámetro
	 * @return 400 indicando qué valor no es válido
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Object> tipoInvalido(MethodArgumentTypeMismatchException e) {
		return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "El valor de " + e.getName() + " no es válido.");
	}

	/**
	 * El cliente pidió un formato que la API no entrega (una cabecera
	 * {@code Accept} distinta de JSON). Se responde sin cuerpo, porque un JSON
	 * tampoco lo aceptaría.
	 *
	 * @return 406 sin cuerpo
	 */
	@ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
	public ResponseEntity<Void> formatoNoAceptable() {
		return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
	}

	/**
	 * El cliente cerró la conexión antes de recibir la respuesta. No hay a quién
	 * responder y no es un error del servidor, así que solo queda en el log de
	 * depuración.
	 *
	 * @param e       excepción de la conexión cerrada
	 * @param request petición que se estaba atendiendo
	 */
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void clienteDesconectado(AsyncRequestNotUsableException e, HttpServletRequest request) {
		LOG.debug("El cliente cerró la conexión en {} {}: {}", request.getMethod(), request.getRequestURI(),
				e.getMessage());
	}

	/**
	 * El usuario no tiene el rol que pide la ruta. Lo lanza {@code @PreAuthorize}
	 * y queda en la auditoría con el usuario que lo intentó (HU-26, CA-137).
	 *
	 * @param request petición rechazada
	 * @return 403 con el mensaje
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> accesoDenegado(HttpServletRequest request) {
		auditoriaService.registrar(idActorActual(), AuditoriaService.ACCESO_DENEGADO, null, null,
				ResultadoAuditoria.RECHAZADO, request.getMethod() + " " + request.getRequestURI());
		return RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, SIN_PERMISO);
	}

	/**
	 * Cualquier otro error. Si es una excepción de Spring que ya trae un estado
	 * 4xx (la culpa es de la petición), se respeta ese estado. Si no, es un
	 * error inesperado: queda en el log con su traza, en la auditoría (HU-26) y
	 * se responde un mensaje genérico sin detalles internos.
	 *
	 * @param e       excepción no controlada
	 * @param request petición que se estaba atendiendo
	 * @return el 4xx de la excepción o un 500 genérico
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> errorInesperado(Exception e, HttpServletRequest request) {
		if (e instanceof ErrorResponse respuesta && respuesta.getStatusCode().is4xxClientError()) {
			return RespuestaUtil.mensaje(HttpStatus.valueOf(respuesta.getStatusCode().value()),
					"La petición no es válida para esta ruta.");
		}
		LOG.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), e);
		auditoriaService.registrar(idActorActual(), AuditoriaService.ERROR_INTERNO, null, null,
				ResultadoAuditoria.ERROR,
				request.getMethod() + " " + request.getRequestURI() + ": " + e.getClass().getSimpleName());
		return RespuestaUtil.mensaje(HttpStatus.INTERNAL_SERVER_ERROR, ERROR_INESPERADO);
	}

	/**
	 * Usuario autenticado de la petición actual.
	 *
	 * @return su id, o {@code null} si no hay sesión
	 */
	private static Long idActorActual() {
		return RespuestaUtil.idUsuario(SecurityContextHolder.getContext().getAuthentication());
	}
}
