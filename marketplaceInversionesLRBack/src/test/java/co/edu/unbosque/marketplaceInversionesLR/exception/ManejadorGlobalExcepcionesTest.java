package co.edu.unbosque.marketplaceInversionesLR.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

/**
 * Pruebas de las respuestas de error comunes de la API.
 */
class ManejadorGlobalExcepcionesTest {

	/** Manejador bajo prueba. */
	private final ManejadorGlobalExcepciones manejador = new ManejadorGlobalExcepciones();

	@Test
	@DisplayName("Cada error responde su código HTTP y un mensaje")
	void respuestasDeError() {
		assertError(manejador.cuerpoInvalido(), HttpStatus.BAD_REQUEST);
		assertError(manejador.tipoContenidoNoSoportado(new HttpMediaTypeNotSupportedException("text/plain")),
				HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertError(manejador.rutaNoEncontrada(), HttpStatus.NOT_FOUND);
		assertError(manejador.conflictoDeDatos(new DataIntegrityViolationException("duplicado")), HttpStatus.CONFLICT);
	}

	@Test
	@DisplayName("El 405 indica el método no permitido")
	void metodoNoPermitido() {
		ResponseEntity<Object> respuesta = manejador.metodoNoPermitido(new HttpRequestMethodNotSupportedException("DELETE"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", "Método DELETE no permitido en esta ruta."));
	}

	/**
	 * Verifica el código y que el cuerpo tenga el mensaje.
	 *
	 * @param respuesta respuesta del manejador
	 * @param esperado  código esperado
	 */
	private static void assertError(ResponseEntity<Object> respuesta, HttpStatus esperado) {
		assertThat(respuesta.getStatusCode()).isEqualTo(esperado);
		assertThat(respuesta.getBody()).isInstanceOf(Map.class);
		assertThat(((Map<?, ?>) respuesta.getBody()).get("message")).asString().isNotBlank();
	}
}
