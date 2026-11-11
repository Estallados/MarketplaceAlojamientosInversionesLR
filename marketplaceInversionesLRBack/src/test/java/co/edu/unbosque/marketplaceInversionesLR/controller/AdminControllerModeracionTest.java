package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.EventoAuditoriaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MotivoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.TarifaService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de las rutas de moderación de {@link AdminController}
 * (HU-32), con el servicio simulado. El flujo completo está en
 * {@link ModeracionPublicacionesTest}.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerModeracionTest {

	/** Administrador del token. */
	private static final Long ADMIN = 1L;

	@Mock
	private TarifaService tarifaService;

	@Mock
	private PublicacionService publicacionService;

	@Mock
	private AuditoriaService auditoriaService;

	private AdminController controlador;

	private Authentication autenticacion;

	@BeforeEach
	void preparar() {
		controlador = new AdminController(tarifaService, publicacionService, null, null, auditoriaService, null);
		Usuario admin = new Usuario();
		admin.setIdUsuario(ADMIN);
		autenticacion = new UsernamePasswordAuthenticationToken(admin, null, List.of());
	}

	@Test
	@DisplayName("CA-158 Aprobar usa el administrador del token")
	void aprueba_CA158() {
		AlojamientoDTO activa = new AlojamientoDTO();
		when(publicacionService.aprobar(20L, ADMIN)).thenReturn(Resultado.exito(activa));

		ResponseEntity<Object> respuesta = controlador.aprobar(20L, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(activa);
	}

	@Test
	@DisplayName("CA-195 Rechazar envía el motivo; un motivo inválido responde 400")
	void rechaza_CA195() {
		MotivoDTO datos = new MotivoDTO();
		datos.setMotivo("Fotos borrosas");
		when(publicacionService.rechazar(20L, ADMIN, "Fotos borrosas"))
				.thenReturn(Resultado.exito(new AlojamientoDTO()));
		when(publicacionService.rechazar(20L, ADMIN, null))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, PublicacionService.MOTIVO_INVALIDO));

		assertThat(controlador.rechazar(20L, datos, autenticacion).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(controlador.rechazar(20L, new MotivoDTO(), autenticacion).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("CA-062 La auditoría se consulta con los filtros, del evento más reciente al más antiguo")
	void consultaLaAuditoria_CA062() {
		PaginaDTO<EventoAuditoriaDTO> pagina = new PaginaDTO<>();
		when(auditoriaService.consultar("LOGIN", 7L, null, null,
				PageRequest.of(1, 5, Sort.by("fecha").descending().and(Sort.by("idEvento").descending()))))
				.thenReturn(pagina);

		ResponseEntity<Object> respuesta = controlador.auditoria("LOGIN", 7L, null, null, 1, 5);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(pagina);
	}

	@Test
	@DisplayName("CA-062 El detalle de un evento responde 200 o 404 si no existe")
	void detalleDelEvento_CA062() {
		EventoAuditoriaDTO evento = new EventoAuditoriaDTO();
		when(auditoriaService.obtener(9L)).thenReturn(Resultado.exito(evento));
		when(auditoriaService.obtener(99L))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, AuditoriaService.EVENTO_NO_EXISTE));

		assertThat(controlador.evento(9L).getBody()).isSameAs(evento);
		assertThat(controlador.evento(99L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

	}

	@Test
	@DisplayName("CA-054 Bloquear envía el motivo y el administrador del token; un motivo inválido responde 400")
	void bloquea_CA054() {
		MotivoDTO datos = new MotivoDTO();
		datos.setMotivo("Publicidad falsa");
		AlojamientoDTO bloqueada = new AlojamientoDTO();
		when(publicacionService.bloquear(20L, ADMIN, "Publicidad falsa")).thenReturn(Resultado.exito(bloqueada));
		when(publicacionService.bloquear(20L, ADMIN, null))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, PublicacionService.MOTIVO_BLOQUEO));

		ResponseEntity<Object> respuesta = controlador.bloquear(20L, datos, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(bloqueada);
		assertThat(controlador.bloquear(20L, new MotivoDTO(), autenticacion).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("Desbloquear usa el administrador del token; si no está bloqueado responde 409 y si no existe 404")
	void desbloquea() {
		AlojamientoDTO pausada = new AlojamientoDTO();
		when(publicacionService.desbloquear(20L, ADMIN)).thenReturn(Resultado.exito(pausada));
		when(publicacionService.desbloquear(21L, ADMIN))
				.thenReturn(Resultado.error(Resultado.ESTADO_INVALIDO, "No se puede pasar de ACTIVA a PAUSADA."));
		when(publicacionService.desbloquear(99L, ADMIN))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, "El alojamiento no existe."));

		ResponseEntity<Object> respuesta = controlador.desbloquear(20L, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(pausada);
		assertThat(controlador.desbloquear(21L, autenticacion).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(controlador.desbloquear(99L, autenticacion).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("La cola de revisión se pide con el estado y una página válida")
	void listaLaCola() {
		PaginaDTO<AlojamientoDTO> pagina = new PaginaDTO<>();
		when(publicacionService.listarPorEstado(eq(EstadoAlojamiento.PENDIENTE_REVISION), any(Pageable.class)))
				.thenReturn(pagina);

		ResponseEntity<Object> respuesta = controlador.alojamientos(EstadoAlojamiento.PENDIENTE_REVISION, 0, 20);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(pagina);
	}

	@Test
	@DisplayName("La página pedida se ajusta a los límites y ordena lo más reciente primero")
	void paginaDentroDeLimites() {
		Pageable normal = AdminController.pagina(2, 20);
		Pageable fuera = AdminController.pagina(-3, 0);
		Pageable enorme = AdminController.pagina(Integer.MAX_VALUE, 1000);

		assertThat(normal.getPageNumber()).isEqualTo(2);
		assertThat(normal.getPageSize()).isEqualTo(20);
		assertThat(normal.getSort().getOrderFor("fechaCreacion").isDescending()).isTrue();
		assertThat(fuera.getPageNumber()).isZero();
		assertThat(fuera.getPageSize()).isEqualTo(1);
		assertThat(enorme.getPageSize()).isEqualTo(AdminController.TAMANIO_MAXIMO);
		assertThat(enorme.getOffset()).isLessThan(Integer.MAX_VALUE);
	}
}
