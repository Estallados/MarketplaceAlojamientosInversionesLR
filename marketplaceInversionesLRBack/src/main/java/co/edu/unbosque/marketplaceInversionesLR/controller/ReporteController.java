package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.service.ReporteService;
import co.edu.unbosque.marketplaceInversionesLR.util.ArchivoReporte;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST del dashboard (HU-04) y los reportes (HU-05). Ruta base:
 * {@code /admin}. Solo para administradores.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Dashboard y reportes", description = "Indicadores y exportación en PDF o Excel")
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

	/**
	 * Historial de exportaciones: quién exportó, qué, en qué formato y cuándo
	 * (CA-027). Ruta: {@code GET /admin/reportes/historial}.
	 *
	 * @param pagina  número de página (desde 0)
	 * @param tamanio elementos por página
	 * @return 200 con la página de exportaciones
	 */
	@Operation(summary = "Historial de exportaciones (quién, qué, formato y cuándo)")
	@ApiResponse(responseCode = "200", description = "Página de exportaciones con quién las hizo")
	@GetMapping("/reportes/historial")
	public ResponseEntity<Object> historial(@RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(reporteService.historial(RespuestaUtil.pagina(pagina, tamanio,
				Sort.by("fecha").descending().and(Sort.by("idRegistro").descending()))));
	}

	/**
	 * Exporta un reporte en PDF o Excel y registra quién lo pidió y cuándo
	 * (CA-027). Ruta: {@code GET /admin/reportes/{tipo}?formato=pdf|excel}.
	 *
	 * @param tipo           usuarios, alojamientos, reservas, ingresos o demanda
	 * @param formato        pdf o excel
	 * @param authentication administrador autenticado
	 * @return 200 con el archivo para descargar; 400 si el tipo o el formato no
	 *         existen
	 */
	@Operation(summary = "Exportar un reporte", description = "tipo: usuarios, alojamientos, reservas, ingresos o "
			+ "demanda; formato: pdf o excel.")
	@ApiResponse(responseCode = "200", description = "Archivo PDF o Excel")
	@ApiResponse(responseCode = "400", description = ReporteService.REPORTE_INVALIDO)
	@GetMapping("/reportes/{tipo}")
	public ResponseEntity<Object> exportar(@PathVariable String tipo,
			@RequestParam(defaultValue = "pdf") String formato, Authentication authentication) {
		Resultado<ArchivoReporte> resultado = reporteService.exportar(tipo, formato,
				RespuestaUtil.idUsuario(authentication));
		if (!resultado.esExito()) {
			return RespuestaUtil.desde(resultado);
		}
		ArchivoReporte archivo = resultado.getDato();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(archivo.getNombreArchivo()).build().toString())
				.contentType(MediaType.parseMediaType(archivo.getTipoContenido())).body(archivo.getContenido());
	}
}
