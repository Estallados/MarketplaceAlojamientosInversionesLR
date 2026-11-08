package co.edu.unbosque.marketplaceInversionesLR.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;

/**
 * Pruebas de las respuestas de error de las rutas que reciben archivos.
 */
class ManejadorErroresArchivosTest {

	/** Manejador bajo prueba. */
	private final ManejadorGlobalExcepciones manejador = new ManejadorGlobalExcepciones(mock(AuditoriaService.class));

	@Test
	@DisplayName("El 415 indica multipart/form-data en las rutas de archivos y JSON en las demás")
	void tipoDeContenidoEsperado() {
		HttpMediaTypeNotSupportedException multipart = new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON,
				List.of(MediaType.MULTIPART_FORM_DATA));
		HttpMediaTypeNotSupportedException json = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN,
				List.of(MediaType.APPLICATION_JSON));

		assertMensaje(manejador.tipoContenidoNoSoportado(multipart), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"El cuerpo debe enviarse como multipart/form-data.");
		assertMensaje(manejador.tipoContenidoNoSoportado(json), HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				"El cuerpo debe enviarse como application/json.");
	}

	@Test
	@DisplayName("Archivo muy grande, almacenamiento caído, campo faltante y multipart inválido")
	void erroresDeArchivos() {
		assertMensaje(manejador.archivoMuyGrande(), HttpStatus.PAYLOAD_TOO_LARGE,
				"El archivo supera el tamaño permitido (5 MB por imagen).");
		assertMensaje(manejador.almacenamientoNoDisponible(new AlmacenamientoNoDisponibleException("Caído.")),
				HttpStatus.SERVICE_UNAVAILABLE, "Caído.");
		assertMensaje(manejador.parteFaltante(new MissingServletRequestPartException("archivos")),
				HttpStatus.BAD_REQUEST, "Falta el campo archivos con los archivos.");
		assertMensaje(manejador.multipartInvalido(), HttpStatus.BAD_REQUEST,
				"Los archivos deben enviarse como multipart/form-data.");
	}

	/**
	 * Verifica el código y el mensaje de la respuesta.
	 *
	 * @param respuesta respuesta del manejador
	 * @param estado    código esperado
	 * @param mensaje   mensaje esperado
	 */
	private static void assertMensaje(ResponseEntity<Object> respuesta, HttpStatus estado, String mensaje) {
		assertThat(respuesta.getStatusCode()).isEqualTo(estado);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", mensaje));
	}
}
