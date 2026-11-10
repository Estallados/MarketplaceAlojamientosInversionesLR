package co.edu.unbosque.marketplaceInversionesLR.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;

/**
 * Pruebas de las respuestas de error comunes de la API y de su registro en la
 * auditoría (HU-26).
 */
class ManejadorGlobalExcepcionesTest {

	/** Auditoría simulada. */
	private final AuditoriaService auditoriaService = mock(AuditoriaService.class);

	/** Manejador bajo prueba. */
	private final ManejadorGlobalExcepciones manejador = new ManejadorGlobalExcepciones(auditoriaService);

	@AfterEach
	void limpiarSesion() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("Cada error responde su código HTTP y un mensaje")
	void respuestasDeError() {
		assertError(manejador.cuerpoInvalido(), HttpStatus.BAD_REQUEST);
		assertError(manejador.tipoContenidoNoSoportado(new HttpMediaTypeNotSupportedException("text/plain")),
				HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertError(manejador.rutaNoEncontrada(), HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("CA-072 Si Neon rechaza dos reservas cruzadas, el 409 dice que otra reserva tomó el período")
	void cruceDeReservas_CA072() {
		ResponseEntity<Object> respuesta = manejador.conflictoDeDatos(new DataIntegrityViolationException("error",
				new IllegalStateException("violates exclusion constraint \"ex_reserva_sin_cruces\"")), peticion());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", ReservaService.PERIODO_TOMADO));
	}

	@Test
	@DisplayName("Un duplicado o un cruce de otra restricción de exclusión responde 409 sin auditarse como error")
	void duplicadoOCruce() {
		ResponseEntity<Object> duplicado = manejador.conflictoDeDatos(new DataIntegrityViolationException("error",
				new SQLException("duplicate key value violates unique constraint", "23505")), peticion());
		ResponseEntity<Object> cruce = manejador.conflictoDeDatos(new DataIntegrityViolationException("error",
				new SQLException("conflicting key value violates exclusion constraint", "23P01")), peticion());

		assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(duplicado.getBody()).isEqualTo(Map.of("message", ManejadorGlobalExcepciones.REGISTRO_EXISTE));
		assertThat(cruce.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		verify(auditoriaService, never()).registrar(any(), anyString(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("CA-136 Si un trigger de Neon rechaza la operación, no se esconde como duplicado: 500 y auditoría")
	void triggerDeNeonNoEsDuplicado_CA136() {
		ResponseEntity<Object> respuesta = manejador.conflictoDeDatos(new DataIntegrityViolationException("error",
				new SQLException("Una reserva nueva empieza en PENDIENTE_PAGO (recibido: CONFIRMADA)", "23514")),
				peticion());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", ManejadorGlobalExcepciones.ERROR_INESPERADO));
		verify(auditoriaService).registrar(isNull(), eq(AuditoriaService.ERROR_INTERNO), isNull(), isNull(),
				eq(ResultadoAuditoria.ERROR), eq("POST /reservas: DataIntegrityViolationException"));
	}

	/** Petición de ejemplo para los errores de datos. */
	private static MockHttpServletRequest peticion() {
		return new MockHttpServletRequest("POST", "/reservas");
	}

	@Test
	@DisplayName("El 405 indica el método no permitido")
	void metodoNoPermitido() {
		ResponseEntity<Object> respuesta = manejador.metodoNoPermitido(new HttpRequestMethodNotSupportedException("DELETE"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", "Método DELETE no permitido en esta ruta."));
	}

	@Test
	@DisplayName("Un parámetro con tipo inválido responde 400 con su nombre; un formato no aceptado responde 406")
	void parametroInvalidoYFormatoNoAceptable() {
		MethodArgumentTypeMismatchException e = mock(MethodArgumentTypeMismatchException.class);
		when(e.getName()).thenReturn("id");

		ResponseEntity<Object> respuesta = manejador.tipoInvalido(e);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", "El valor de id no es válido."));
		assertThat(manejador.formatoNoAceptable().getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
		assertThatCode(() -> manejador.clienteDesconectado(new AsyncRequestNotUsableException("cerrada"),
				new MockHttpServletRequest("GET", "/alojamientos/politicas"))).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("CA-137 Un acceso denegado queda en la auditoría con el usuario que lo intentó")
	void accesoDenegadoAuditadoConElUsuario_CA137() {
		iniciarSesion(15L);

		ResponseEntity<Object> respuesta = manejador.accesoDenegado(new MockHttpServletRequest("POST", "/alojamientos"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", ManejadorGlobalExcepciones.SIN_PERMISO));
		verify(auditoriaService).registrar(eq(15L), eq(AuditoriaService.ACCESO_DENEGADO), isNull(), isNull(),
				eq(ResultadoAuditoria.RECHAZADO), eq("POST /alojamientos"));
	}

	@Test
	@DisplayName("CA-136 Un error inesperado responde 500 genérico y queda en la auditoría como ERROR")
	void errorInesperadoAuditado_CA136() {
		ResponseEntity<Object> respuesta = manejador.errorInesperado(new IllegalStateException("detalle interno"),
				new MockHttpServletRequest("GET", "/admin/tarifas"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", ManejadorGlobalExcepciones.ERROR_INESPERADO));
		verify(auditoriaService).registrar(isNull(), eq(AuditoriaService.ERROR_INTERNO), isNull(), isNull(),
				eq(ResultadoAuditoria.ERROR), eq("GET /admin/tarifas: IllegalStateException"));
	}

	@Test
	@DisplayName("Una excepción de Spring con estado 4xx conserva su estado y no se audita como error")
	void errorDeLaPeticionConservaSuEstado() {
		ResponseEntity<Object> respuesta = manejador.errorInesperado(new ResponseStatusException(HttpStatus.BAD_REQUEST),
				new MockHttpServletRequest("GET", "/alojamientos"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verify(auditoriaService, never()).registrar(any(), anyString(), any(), any(), any(), any());
		assertThat(manejador.errorInesperado(new HttpMediaTypeNotAcceptableException("xml"),
				new MockHttpServletRequest("GET", "/alojamientos")).getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
	}

	/**
	 * Deja un usuario autenticado en el contexto de seguridad.
	 *
	 * @param idUsuario id del usuario
	 */
	private static void iniciarSesion(Long idUsuario) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(idUsuario);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
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
