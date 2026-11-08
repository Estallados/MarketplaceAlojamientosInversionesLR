package co.edu.unbosque.marketplaceInversionesLR.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;

/**
 * Convierte los errores de la API en respuestas {@code {"message": "..."}},
 * para que el frontend las lea siempre igual (RNF-013, CA-149).
 */
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(ManejadorGlobalExcepciones.class);

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
	 * servicio.
	 *
	 * @param e error de la base de datos
	 * @return 409
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Object> conflictoDeDatos(DataIntegrityViolationException e) {
		LOG.warn("Conflicto de datos: {}", e.getMostSpecificCause().getMessage());
		return RespuestaUtil.mensaje(HttpStatus.CONFLICT, "El registro ya existe. Actualiza la página e intenta de nuevo.");
	}
}
