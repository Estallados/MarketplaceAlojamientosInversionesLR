package co.edu.unbosque.marketplaceInversionesLR.util;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Respuestas con el formato de la API: {@code {"message": "..."}} (esquema
 * {@code Mensaje} de {@code docs/openapi}).
 */
public final class RespuestaUtil {

	private RespuestaUtil() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Respuesta con un mensaje para el usuario.
	 *
	 * @param status estado HTTP
	 * @param texto  mensaje
	 * @return respuesta {@code {"message": texto}}
	 */
	public static ResponseEntity<Object> mensaje(HttpStatus status, String texto) {
		return ResponseEntity.status(status).body(Map.of("message", texto));
	}
}
