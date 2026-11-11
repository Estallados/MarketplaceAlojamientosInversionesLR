package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConteoPorEstadoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DashboardDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.ReporteService;

/**
 * Pruebas unitarias de {@link ReporteController} (HU-04).
 */
@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {

	@Mock
	private ReporteService reporteService;

	@Test
	@DisplayName("CA-022 Consultar el dashboard responde 200 con los indicadores del servicio")
	void dashboard_CA022() {
		DashboardDTO indicadores = new DashboardDTO(3, new ConteoPorEstadoDTO(1, Map.of("ACTIVA", 1L), null),
				new ConteoPorEstadoDTO(0, Map.of(), 0L), BigDecimal.ZERO, List.of());
		when(reporteService.dashboard(5)).thenReturn(indicadores);

		ResponseEntity<Object> respuesta = new ReporteController(reporteService).dashboard(5);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(indicadores);
	}

	@Test
	@DisplayName("CA-022 El dashboard es solo para administradores")
	void soloAdministrador_CA022() {
		assertThat(ReporteController.class.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
	}
}
