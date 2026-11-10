package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import co.edu.unbosque.marketplaceInversionesLR.dto.EventoAuditoriaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
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

	@Test
	@DisplayName("CA-139 La consulta ordena del más reciente al más antiguo, limpia la acción y acota la página")
	void consultaConFiltros_CA139() {
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, JsonMapper.builder().build());
		LocalDateTime desde = LocalDateTime.now().minusDays(1);
		Page<EventoAuditoria> pagina = new PageImpl<>(List.of(new EventoAuditoria()));
		when(repositorio.buscar(any(), any(), any(), any(), any())).thenReturn(pagina);

		assertThat(servicio.consultar(" LOGIN ", 4L, desde, null, -2, 500)).isSameAs(pagina);

		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(repositorio).buscar(eq("LOGIN"), eq(4L), eq(desde), isNull(), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isZero();
		assertThat(pageable.getValue().getPageSize()).isEqualTo(AuditoriaService.MAX_TAMANIO_PAGINA);
		assertThat(pageable.getValue().getSort().getOrderFor("fecha").getDirection()).isEqualTo(Sort.Direction.DESC);

		servicio.consultar("  ", null, null, null, 1, 0);
		verify(repositorio).buscar(isNull(), isNull(), isNull(), isNull(),
				eq(PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC,
						"idEvento")))));
	}

	@Test
	@DisplayName("CA-138 El detalle de un evento se consulta por su id")
	void detalle_CA138() {
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, JsonMapper.builder().build());
		EventoAuditoria evento = new EventoAuditoria();
		when(repositorio.findById(9L)).thenReturn(Optional.of(evento));

		assertThat(servicio.detalle(9L)).containsSame(evento);
		assertThat(servicio.detalle(null)).isEmpty();
	}

	@Test
	@DisplayName("CA-062 La consulta del administrador muestra cada evento con su información y no distingue mayúsculas")
	void consultaDelAdministrador_CA062() {
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, JsonMapper.builder().build());
		Pageable paginacion = PageRequest.of(0, 50);
		LocalDateTime fecha = LocalDateTime.of(2026, 11, 10, 9, 30);
		when(repositorio.buscar("LOGIN", 7L, null, null, paginacion))
				.thenReturn(new PageImpl<>(List.of(evento(fecha))));

		PaginaDTO<EventoAuditoriaDTO> pagina = servicio.consultar(" login ", 7L, null, null, paginacion);

		assertThat(pagina.getTotalElementos()).isEqualTo(1);
		EventoAuditoriaDTO dto = pagina.getContenido().get(0);
		assertThat(dto.getAccion()).isEqualTo(AuditoriaService.LOGIN);
		assertThat(dto.getFecha()).isEqualTo(fecha);
		assertThat(dto.getDetalle()).isEqualTo("Google");
		assertThat(dto.getIdUsuario()).isEqualTo(7L);
		assertThat(dto.getEntidad()).isEqualTo(AuditoriaService.ENTIDAD_USUARIO);
		assertThat(dto.getIdEntidad()).isEqualTo(7L);
		assertThat(dto.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
	}

	@Test
	@DisplayName("CA-062 Sin acción no se filtra por acción")
	void consultaSinAccion_CA062() {
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, JsonMapper.builder().build());
		Pageable paginacion = PageRequest.of(0, 50);
		when(repositorio.buscar(null, null, null, null, paginacion)).thenReturn(new PageImpl<>(List.of()));

		assertThat(servicio.consultar("  ", null, null, null, paginacion).getContenido()).isEmpty();
		assertThat(servicio.consultar(null, null, null, null, paginacion).getContenido()).isEmpty();
	}

	@Test
	@DisplayName("CA-062 El detalle trae la información del evento; si no existe responde no encontrado")
	void obtieneElDetalle_CA062() {
		AuditoriaService servicio = new AuditoriaService(repositorio, transacciones, JsonMapper.builder().build());
		when(repositorio.findById(9L)).thenReturn(Optional.of(evento(LocalDateTime.of(2026, 11, 10, 9, 30))));
		when(repositorio.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.obtener(9L).getDato().getAccion()).isEqualTo(AuditoriaService.LOGIN);
		Resultado<EventoAuditoriaDTO> inexistente = servicio.obtener(99L);
		assertThat(inexistente.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(inexistente.getMensaje()).isEqualTo(AuditoriaService.EVENTO_NO_EXISTE);
	}

	private static EventoAuditoria evento(LocalDateTime fecha) {
		EventoAuditoria evento = new EventoAuditoria();
		evento.setAccion(AuditoriaService.LOGIN);
		evento.setFecha(fecha);
		evento.setDetalle("Google");
		evento.setIdUsuario(7L);
		evento.setEntidad(AuditoriaService.ENTIDAD_USUARIO);
		evento.setIdEntidad(7L);
		evento.setResultado(ResultadoAuditoria.EXITOSO);
		return evento;
	}
}
