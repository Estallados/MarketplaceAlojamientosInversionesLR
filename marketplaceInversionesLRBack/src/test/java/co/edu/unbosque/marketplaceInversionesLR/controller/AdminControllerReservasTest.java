package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaAdminDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;

/**
 * Pruebas unitarias de {@code GET /admin/reservas} en {@link AdminController}
 * (HU-11, CA-112), con el servicio simulado.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerReservasTest {

	@Mock
	private ReservaService reservaService;

	private AdminController controlador;

	@BeforeEach
	void preparar() {
		controlador = new AdminController(null, null, null, reservaService, null, null);
	}

	@Test
	@DisplayName("CA-112 El listado pide las EN_REVISION, las más recientes primero y con el tamaño limitado")
	void enRevision_CA112() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		PaginaDTO<ReservaAdminDTO> resultado = new PaginaDTO<>();
		when(reservaService.listarTodas(eq(EstadoReserva.EN_REVISION), pagina.capture())).thenReturn(resultado);

		ResponseEntity<Object> respuesta = controlador.reservas(EstadoReserva.EN_REVISION, -1, 500);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(resultado);
		assertThat(pagina.getValue().getPageNumber()).isZero();
		assertThat(pagina.getValue().getPageSize()).isEqualTo(100);
		assertThat(pagina.getValue().getSort().getOrderFor("fechaCreacion").isDescending()).isTrue();
		assertThat(pagina.getValue().getSort().getOrderFor("idReserva").isDescending()).isTrue();
	}

	@Test
	@DisplayName("Sin estado pide todas las reservas")
	void todas() {
		PaginaDTO<ReservaAdminDTO> resultado = new PaginaDTO<>();
		when(reservaService.listarTodas(isNull(), any())).thenReturn(resultado);

		assertThat(controlador.reservas(null, 0, 20).getBody()).isSameAs(resultado);
	}
}
