package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del registro de auditoría con el repositorio y la transacción
 * simulados.
 */
class AuditoriaServiceTest {

	/** Repositorio simulado. */
	private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);

	/** Administrador de transacciones simulado. */
	private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);

	@Test
	@DisplayName("Guarda el evento con todos sus datos y recorta el detalle a 1000 caracteres")
	void guardaElEvento() {
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, JsonMapper.builder().build());

		servicio.registrar(7L, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO, 7L,
				ResultadoAuditoria.EXITOSO, "x".repeat(1200));

		ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
		verify(repositorio).save(captor.capture());
		EventoAuditoria evento = captor.getValue();
		assertThat(evento.getIdUsuario()).isEqualTo(7L);
		assertThat(evento.getAccion()).isEqualTo(AuditoriaService.REGISTRO_USUARIO);
		assertThat(evento.getEntidad()).isEqualTo(AuditoriaService.ENTIDAD_USUARIO);
		assertThat(evento.getIdEntidad()).isEqualTo(7L);
		assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
		assertThat(evento.getDetalle()).hasSize(1000);
		assertThat(evento.getFecha()).isNotNull();
	}

	@Test
	@DisplayName("Si falla el guardado o el log, la operación auditada no se interrumpe")
	void fallasNoInterrumpen() {
		ObjectMapper mapper = mock(ObjectMapper.class);
		when(mapper.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
		when(repositorio.save(any())).thenThrow(new IllegalStateException("sin base de datos"));
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, mapper);

		assertThatCode(() -> servicio.registrar(null, AuditoriaService.CORREO_VERIFICADO, null, null,
				ResultadoAuditoria.ERROR, null)).doesNotThrowAnyException();
	}
}
