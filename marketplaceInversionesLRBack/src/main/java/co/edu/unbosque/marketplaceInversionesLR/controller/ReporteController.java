package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST del dashboard administrativo (HU-04). Ruta base:
 * {@code /admin}. Solo para administradores.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard", description = "Indicadores de gestión del sistema")
public class ReporteController {

	/** Servicio de reportes. */
	private final ReporteService reporteService;

	/**
	 * Crea el controlador.
	 *
	 * @param reporteService servicio de reportes
	 */
	public ReporteController(ReporteService reporteService) {
		this.reporteService = reporteService;
	}

	/**
	 * Indicadores del dashboard (CA-022): usuarios, publicaciones y reservas
	 * por estado, ingresos estimados y ciudades con más reservas. Ruta:
	 * {@code GET /admin/dashboard}.
	 *
	 * @param topCiudades cantidad de ciudades del ranking (entre 1 y
	 *                    {@value ReporteService#CIUDADES_MAXIMAS})
	 * @return 200 con los indicadores
	 */
	@Operation(summary = "Indicadores: usuarios, publicaciones y reservas por estado, ingresos estimados y "
			+ "ciudades con más reservas")
	@ApiResponse(responseCode = "200", description = "Indicadores del dashboard")
	@GetMapping("/dashboard")
	public ResponseEntity<Object> dashboard(@RequestParam(defaultValue = "5") int topCiudades) {
		return ResponseEntity.ok(reporteService.dashboard(topCiudades));
	}
}
