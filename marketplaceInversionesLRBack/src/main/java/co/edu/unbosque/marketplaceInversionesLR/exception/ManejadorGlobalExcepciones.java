package co.edu.unbosque.marketplaceInversionesLR.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
	 * Tipo de contenido no soportado.
	 *
	 * @return 415
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Object> tipoContenidoNoSoportado() {
		return RespuestaUtil.mensaje(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El cuerpo debe enviarse como application/json.");
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
