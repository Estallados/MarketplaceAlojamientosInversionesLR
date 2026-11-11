package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConteoPorEstadoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DashboardDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.RegistroExportacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.ReporteService;
import co.edu.unbosque.marketplaceInversionesLR.util.ArchivoReporte;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de {@link ReporteController} (HU-04 y HU-05).
 */
@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {

	/** Administrador autenticado. */
	private static final Long ADMIN = 2L;

	@Mock
	private ReporteService reporteService;

	/** Sesión del administrador. */
	private static Authentication sesion() {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(ADMIN);
		return new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}

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

	@Test
	@DisplayName("CA-027 Exportar responde el archivo como descarga, a nombre del administrador del token")
	void exportar_CA027() {
		byte[] contenido = "%PDF-prueba".getBytes();
		when(reporteService.exportar("reservas", "pdf", ADMIN))
				.thenReturn(Resultado.exito(new ArchivoReporte("reporte-reservas.pdf", "application/pdf", contenido)));

		ResponseEntity<Object> respuesta = new ReporteController(reporteService).exportar("reservas", "pdf", sesion());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
		assertThat(respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
				.isEqualTo("attachment; filename=\"reporte-reservas.pdf\"");
		assertThat((byte[]) respuesta.getBody()).isEqualTo(contenido);
	}

	@Test
	@DisplayName("CA-027 Un tipo o formato que no existe responde 400")
	void exportarInvalido_CA027() {
		when(reporteService.exportar("clientes", "pdf", ADMIN))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, ReporteService.REPORTE_INVALIDO));

		ResponseEntity<Object> respuesta = new ReporteController(reporteService).exportar("clientes", "pdf", sesion());

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("CA-027 El historial pide las exportaciones de la más reciente a la más antigua")
	void historial_CA027() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		PaginaDTO<RegistroExportacionDTO> historial = new PaginaDTO<>();
		when(reporteService.historial(pagina.capture())).thenReturn(historial);

		ResponseEntity<Object> respuesta = new ReporteController(reporteService).historial(0, 20);

		assertThat(respuesta.getBody()).isSameAs(historial);
		assertThat(pagina.getValue().getSort().getOrderFor("fecha").isDescending()).isTrue();
		assertThat(pagina.getValue().getSort().getOrderFor("idRegistro").isDescending()).isTrue();
	}
}
