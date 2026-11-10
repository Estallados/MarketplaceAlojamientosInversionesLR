package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoIdempotencia;
import co.edu.unbosque.marketplaceInversionesLR.entity.RegistroIdempotencia;
import co.edu.unbosque.marketplaceInversionesLR.repository.RegistroIdempotenciaRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas unitarias de la idempotencia con {@code Idempotency-Key} (RNF-008,
 * CA-140, CA-141). La base de datos y las transacciones se simulan.
 */
@ExtendWith(MockitoExtension.class)
class IdempotenciaServiceTest {

	/** Usuario de prueba. */
	private static final Long USUARIO = 3L;

	/** Operación de prueba. */
	private static final String OPERACION = "CANCELAR_RESERVA";

	/** Clave de prueba. */
	private static final String CLAVE = "3f2b8c1e-7a44-4d0b-9e21-6c1f0a7d5e10";

	@Mock
	private RegistroIdempotenciaRepository registroRepo;

	@Mock
	private PlatformTransactionManager transactionManager;

	private IdempotenciaService servicio;

	/** Veces que se ejecutó la acción. */
	private final AtomicInteger ejecuciones = new AtomicInteger();

	@BeforeEach
	void preparar() {
		servicio = new IdempotenciaService(registroRepo, transactionManager, JsonMapper.builder().build());
	}

	@Test
	@DisplayName("CA-140 La primera vez ejecuta la acción y guarda su respuesta")
	void primeraVez_CA140() {
		RegistroIdempotencia registro = registroGuardado();
		when(registroRepo.findById(registro.getIdRegistro())).thenReturn(Optional.of(registro));

		ResponseEntity<Object> respuesta = servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of("id", 15),
				this::accionExitosa);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("idCancelacion", 4));
		assertThat(ejecuciones.get()).isEqualTo(1);
		assertThat(registro.getEstado()).isEqualTo(EstadoIdempotencia.COMPLETADA);
		assertThat(registro.getCodigoRespuesta()).isEqualTo(200);
		assertThat(registro.getCuerpoRespuesta()).isEqualTo("{\"idCancelacion\":4}");
		assertThat(registro.getHuellaPeticion()).hasSize(64);
	}

	@Test
	@DisplayName("CA-140 CA-141 Con la misma clave y datos devuelve la respuesta guardada sin repetir la acción")
	void reintento_CA140_CA141() {
		RegistroIdempotencia registro = registroGuardado();
		when(registroRepo.findById(registro.getIdRegistro())).thenReturn(Optional.of(registro));
		servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of("id", 15), this::accionExitosa);
		when(registroRepo.findByIdUsuarioAndOperacionAndClave(USUARIO, OPERACION, CLAVE))
				.thenReturn(Optional.of(registro));

		ResponseEntity<Object> repetida = servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of("id", 15),
				this::accionExitosa);

		assertThat(ejecuciones.get()).isEqualTo(1);
		assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(repetida.getHeaders().getFirst(IdempotenciaService.CABECERA_REPETIDA)).isEqualTo("true");
		assertThat(repetida.getBody()).hasToString("{\"idCancelacion\":4}");
	}

	@Test
	@DisplayName("La misma clave con otros datos responde 422 y no ejecuta la acción")
	void otrosDatos() {
		RegistroIdempotencia registro = new RegistroIdempotencia();
		registro.setHuellaPeticion("otra-huella");
		registro.setEstado(EstadoIdempotencia.COMPLETADA);
		when(registroRepo.findByIdUsuarioAndOperacionAndClave(USUARIO, OPERACION, CLAVE))
				.thenReturn(Optional.of(registro));

		ResponseEntity<Object> respuesta = servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of("id", 16),
				this::accionExitosa);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", IdempotenciaService.CLAVE_REUTILIZADA));
		assertThat(ejecuciones.get()).isZero();
	}

	@Test
	@DisplayName("Si la primera petición con la clave sigue en curso responde 409")
	void enProceso() {
		RegistroIdempotencia registro = registroGuardado();
		registro.setEstado(EstadoIdempotencia.EN_PROCESO);
		RegistroIdempotencia enCurso = new RegistroIdempotencia();
		enCurso.setEstado(EstadoIdempotencia.EN_PROCESO);
		// La huella se calcula igual para los mismos datos: se toma de una ejecución real
		when(registroRepo.findById(registro.getIdRegistro())).thenReturn(Optional.of(registro));
		servicio.ejecutar(USUARIO, OPERACION, "otra-clave", Map.of("id", 15), this::accionExitosa);
		enCurso.setHuellaPeticion(registro.getHuellaPeticion());
		when(registroRepo.findByIdUsuarioAndOperacionAndClave(USUARIO, OPERACION, CLAVE))
				.thenReturn(Optional.of(enCurso));

		ResponseEntity<Object> respuesta = servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of("id", 15),
				this::accionExitosa);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", IdempotenciaService.EN_PROCESO));
	}

	@Test
	@DisplayName("Sin clave o con una de más de 100 caracteres responde 400 sin ejecutar nada")
	void claveInvalida() {
		for (String clave : new String[] { null, "", "  ", "x".repeat(101) }) {
			ResponseEntity<Object> respuesta = servicio.ejecutar(USUARIO, OPERACION, clave, Map.of(),
					this::accionExitosa);
			assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
			assertThat(respuesta.getBody()).isEqualTo(Map.of("message", IdempotenciaService.CLAVE_INVALIDA));
		}
		assertThat(ejecuciones.get()).isZero();
		verify(registroRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("Si dos peticiones registran la misma clave a la vez, la segunda responde 409")
	void registroSimultaneo() {
		when(registroRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_idempotencia"));

		ResponseEntity<Object> respuesta = servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of(), this::accionExitosa);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(ejecuciones.get()).isZero();
	}

	@Test
	@DisplayName("Si la acción falla con 5xx o con una excepción, la clave se libera para reintentar")
	void liberaAlFallar() {
		RegistroIdempotencia registro = registroGuardado();

		ResponseEntity<Object> error = servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of(),
				() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "error")));
		assertThatThrownBy(() -> servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of(), () -> {
			throw new IllegalStateException("falla");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		verify(registroRepo, org.mockito.Mockito.times(2)).deleteById(registro.getIdRegistro());
	}

	@Test
	@DisplayName("Una respuesta 4xx también se guarda: el reintento recibe el mismo error")
	void guardaErroresDelCliente() {
		RegistroIdempotencia registro = registroGuardado();
		when(registroRepo.findById(registro.getIdRegistro())).thenReturn(Optional.of(registro));

		servicio.ejecutar(USUARIO, OPERACION, CLAVE, Map.of(),
				() -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "No se puede")));

		assertThat(registro.getEstado()).isEqualTo(EstadoIdempotencia.COMPLETADA);
		assertThat(registro.getCodigoRespuesta()).isEqualTo(409);
		verify(registroRepo, never()).deleteById(any());
	}

	/**
	 * Acción de prueba: cuenta la ejecución y responde 200.
	 *
	 * @return respuesta exitosa
	 */
	private ResponseEntity<Object> accionExitosa() {
		ejecuciones.incrementAndGet();
		return ResponseEntity.ok(Map.of("idCancelacion", 4));
	}

	/**
	 * El repositorio guarda el registro nuevo con id 9 y lo devuelve.
	 *
	 * @return el registro que quedará guardado
	 */
	private RegistroIdempotencia registroGuardado() {
		RegistroIdempotencia registro = new RegistroIdempotencia();
		registro.setIdRegistro(9L);
		when(registroRepo.saveAndFlush(any())).thenAnswer(invocacion -> {
			RegistroIdempotencia nuevo = invocacion.getArgument(0);
			registro.setClave(nuevo.getClave());
			registro.setHuellaPeticion(nuevo.getHuellaPeticion());
			registro.setEstado(nuevo.getEstado());
			registro.setIdUsuario(nuevo.getIdUsuario());
			registro.setOperacion(nuevo.getOperacion());
			return registro;
		});
		return registro;
	}
}
