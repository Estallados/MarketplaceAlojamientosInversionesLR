package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConfiguracionTarifaDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.TarifaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Administración. Ruta base {@code /admin}; sus rutas exigen el rol ADMIN,
 * salvo {@code /admin/tarifas}, que también admite ANFITRION (HU-24; ver
 * {@code SecurityConfig}). Contrato:
 * {@code docs/openapi/administracion-reportes.yaml}.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Tarifas", description = "Porcentajes globales de limpieza y servicio")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

	/** Porcentajes de limpieza y servicio. */
	private final TarifaService tarifaService;

	/**
	 * Crea el controlador.
	 *
	 * @param tarifaService servicio de tarifas
	 */
	public AdminController(TarifaService tarifaService) {
		this.tarifaService = tarifaService;
	}

	/**
	 * Porcentajes globales vigentes de limpieza y servicio (HU-24, RF-023). Ruta:
	 * {@code GET /admin/tarifas}.
	 *
	 * @return 200 con los porcentajes vigentes, o los de por defecto con
	 *         {@code porDefecto=true}
	 */
	@Operation(summary = "Porcentajes globales vigentes de limpieza y servicio (ADMIN o ANFITRION)")
	@ApiResponse(responseCode = "200", description = "Porcentajes vigentes de limpieza y servicio")
	@GetMapping("/tarifas")
	public ResponseEntity<Object> tarifas() {
		return ResponseEntity.ok(tarifaService.vigente());
	}

	/**
	 * Cambia los porcentajes globales; aplican a las cotizaciones nuevas
	 * (CA-084). Ruta: {@code PUT /admin/tarifas}.
	 *
	 * @param datos          porcentajes nuevos
	 * @param authentication administrador o anfitrión autenticado
	 * @return 200 con la configuración nueva; 400 si algún porcentaje falta o
	 *         no está entre 0 y 100
	 */
	@Operation(summary = "Cambiar los porcentajes globales (ADMIN o ANFITRION; aplican a cotizaciones nuevas)")
	@ApiResponse(responseCode = "200", description = "Configuración nueva; aplica a las cotizaciones siguientes")
	@ApiResponse(responseCode = "400", description = "Los porcentajes de limpieza y servicio son obligatorios y deben "
			+ "estar entre 0 y 100.")
	@PutMapping("/tarifas")
	public ResponseEntity<Object> actualizarTarifas(@RequestBody ConfiguracionTarifaDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(tarifaService.actualizar(datos, RespuestaUtil.idUsuario(authentication)));
	}
}
