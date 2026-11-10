package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.NotificacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Notificaciones dentro de la plataforma (HU-02, RF-003). Ruta base:
 * {@code /notificaciones}; todas exigen token. Cada usuario ve, cuenta y marca
 * solo las suyas, porque el usuario sale siempre del token (CA-013). El sistema
 * las crea con los eventos (CA-010), por eso no hay ruta para crearlas.
 * Contrato: {@code docs/openapi/resenas-notificaciones.yaml}.
 */
@RestController
@RequestMapping("/notificaciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notificaciones", description = "Notificaciones dentro de la plataforma")
public class NotificacionController {

	/** Orden de las notificaciones: las más recientes primero (CA-013). */
	private static final Sort RECIENTES = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idNotificacion"));

	/** Servicio de notificaciones. */
	private final NotificacionService notificacionService;

	/**
	 * Crea el controlador.
	 *
	 * @param notificacionService servicio de notificaciones
	 */
	public NotificacionController(NotificacionService notificacionService) {
		this.notificacionService = notificacionService;
	}

	/**
	 * Mis notificaciones, de la más reciente a la más antigua (CA-013). Ruta:
	 * {@code GET /notificaciones}.
	 *
	 * @param authentication usuario autenticado
	 * @param pagina         número de página, desde 0
	 * @param tamanio        elementos por página, de 1 a 100
	 * @return 200 con la página de notificaciones
	 */
	@Operation(summary = "Mis notificaciones, de la más reciente a la más antigua", description = "Solo las del "
			+ "usuario del token (CA-013). Responde {contenido, pagina, tamanio, totalElementos, totalPaginas}.")
	@ApiResponse(responseCode = "200", description = "Página de notificaciones, las más recientes primero (CA-013)")
	@GetMapping
	public ResponseEntity<Object> listar(Authentication authentication,
			@Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = "Elementos por página, de 1 a 100") @RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(notificacionService.listar(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Cantidad de notificaciones sin leer, para el contador de la campana.
	 * Ruta: {@code GET /notificaciones/noleidas}.
	 *
	 * @param authentication usuario autenticado
	 * @return 200 con {@code {"cantidad": n}}
	 */
	@Operation(summary = "Cantidad de notificaciones sin leer (contador de la campana)", description = "Cuenta solo "
			+ "las del usuario del token. Responde {cantidad}.")
	@ApiResponse(responseCode = "200", description = "Notificaciones sin leer", content = @Content(
			mediaType = "application/json", examples = @ExampleObject(value = "{\"cantidad\": 3}")))
	@GetMapping("/noleidas")
	public ResponseEntity<Object> noLeidas(Authentication authentication) {
		return ResponseEntity
				.ok(Map.of("cantidad", notificacionService.contarNoLeidas(RespuestaUtil.idUsuario(authentication))));
	}

	/**
	 * Marca todas mis notificaciones como leídas. Ruta:
	 * {@code PATCH /notificaciones/leidas}.
	 *
	 * @param authentication usuario autenticado
	 * @return 200 con {@code {"actualizadas": n}}: cuántas se marcaron
	 */
	@Operation(summary = "Marcar todas mis notificaciones como leídas", description = "Devuelve cuántas se marcaron: "
			+ "{actualizadas}.")
	@ApiResponse(responseCode = "200", description = "Cantidad de notificaciones marcadas", content = @Content(
			mediaType = "application/json", examples = @ExampleObject(value = "{\"actualizadas\": 3}")))
	@PatchMapping("/leidas")
	public ResponseEntity<Object> marcarTodasLeidas(Authentication authentication) {
		return ResponseEntity.ok(
				Map.of("actualizadas", notificacionService.marcarTodasLeidas(RespuestaUtil.idUsuario(authentication))));
	}

	/**
	 * Marca una notificación propia como leída. Ruta:
	 * {@code PATCH /notificaciones/{id}/leida}.
	 *
	 * @param id             notificación
	 * @param authentication usuario autenticado
	 * @return 200 con la notificación; 404 si no existe o es de otro usuario
	 *         (CA-013)
	 */
	@Operation(summary = "Marcar una notificación propia como leída", description = "Si la notificación es de otro "
			+ "usuario responde 404 (CA-013).")
	@ApiResponse(responseCode = "200", description = "Notificación marcada como leída", content = @Content(
			mediaType = "application/json", schema = @Schema(implementation = NotificacionDTO.class)))
	@ApiResponse(responseCode = "404", description = NotificacionService.NO_EXISTE)
	@PatchMapping("/{id}/leida")
	public ResponseEntity<Object> marcarLeida(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(notificacionService.marcarLeida(id, RespuestaUtil.idUsuario(authentication)));
	}
}
