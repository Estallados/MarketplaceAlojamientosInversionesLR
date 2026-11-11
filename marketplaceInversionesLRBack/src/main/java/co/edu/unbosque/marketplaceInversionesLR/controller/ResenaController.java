package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaEntradaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResumenResenasDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.ResenaService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Reseñas y calificaciones (HU-03, RF-004, servicio S10). Para reseñar hay que
 * ser el huésped de una reserva completada; la consulta por alojamiento es
 * pública. El huésped ve sus reseñas y el anfitrión las de sus alojamientos.
 * Contrato: {@code docs/openapi/resenas-notificaciones.yaml}.
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reseñas", description = "Calificaciones de estancias completadas")
public class ResenaController {

	/** Orden de las reseñas: las más recientes primero. */
	private static final Sort RECIENTES = Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("idResena"));

	/** Descripción del parámetro de página. */
	private static final String DESC_PAGINA = "Número de página, desde 0";

	/** Descripción del parámetro de tamaño. */
	private static final String DESC_TAMANIO = "Elementos por página, de 1 a 100";

	/** Servicio de reseñas. */
	private final ResenaService resenaService;

	/**
	 * Crea el controlador.
	 *
	 * @param resenaService servicio de reseñas
	 */
	public ResenaController(ResenaService resenaService) {
		this.resenaService = resenaService;
	}

	/**
	 * Reseña una estancia completada (solo huéspedes): una reseña por reserva,
	 * con calificación de 1 a 5 (CA-016, CA-018). Ruta: {@code POST /resenas}.
	 *
	 * @param datos          reserva, calificación y comentario
	 * @param authentication huésped autenticado
	 * @return 201 con la reseña; 400, 403, 404 o 409 según el caso
	 */
	@Operation(summary = "Reseñar una estancia completada (HUESPED)", description = "Una reseña por reserva, con "
			+ "calificación de 1 a 5 y comentario opcional. Solo si la reserva es propia y está completada: CONFIRMADA "
			+ "y con la fecha de salida ya alcanzada (CA-016). Queda PUBLICADA y asociada a huésped, reserva, "
			+ "alojamiento y fecha; el autor sale del token y el alojamiento de la reserva (CA-018). Se notifica al "
			+ "anfitrión.")
	@PreAuthorize("hasRole('HUESPED')")
	@ApiResponse(responseCode = "201", description = "Reseña publicada; se notifica al anfitrión (CA-018)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ResenaDTO.class)))
	@ApiResponse(responseCode = "400", description = ResenaService.DATOS_INVALIDOS + " | "
			+ ResenaService.COMENTARIO_LARGO)
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = ReservaService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = ResenaService.NO_COMPLETADA + " | " + ResenaService.YA_RESENADA)
	@PostMapping("/resenas")
	public ResponseEntity<Object> crear(@RequestBody ResenaEntradaDTO datos, Authentication authentication) {
		return RespuestaUtil.desde(resenaService.crear(datos, RespuestaUtil.idUsuario(authentication)),
				HttpStatus.CREATED);
	}

	/**
	 * Mis reseñas como huésped, incluidas las ocultas, las más recientes
	 * primero. Ruta: {@code GET /resenas/mias}.
	 *
	 * @param authentication huésped autenticado
	 * @param pagina         número de página, desde 0
	 * @param tamanio        elementos por página, de 1 a 100
	 * @return 200 con la página de reseñas y su estado
	 */
	@Operation(summary = "Mis reseñas, incluidas las ocultas (HUESPED)", description = "Reseñas del huésped del "
			+ "token con su estado (PUBLICADA u OCULTA), las más recientes primero.")
	@PreAuthorize("hasRole('HUESPED')")
	@ApiResponse(responseCode = "200", description = "Página de reseñas del huésped con su estado")
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@GetMapping("/resenas/mias")
	public ResponseEntity<Object> mias(Authentication authentication,
			@Parameter(description = DESC_PAGINA) @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = DESC_TAMANIO) @RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(resenaService.listarMias(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Reseñas visibles de mis alojamientos con promedio y cantidad (solo
	 * anfitriones). Con {@code idAlojamiento} se filtran las de ese
	 * alojamiento, que debe ser propio. Ruta: {@code GET /resenas/anfitrion}.
	 *
	 * @param authentication anfitrión autenticado
	 * @param idAlojamiento  alojamiento propio a filtrar; opcional
	 * @param pagina         número de página, desde 0
	 * @param tamanio        elementos por página, de 1 a 100
	 * @return 200 con el resumen; 404 si el alojamiento no existe o no es
	 *         propio
	 */
	@Operation(summary = "Reseñas visibles de mis alojamientos con promedio y cantidad (ANFITRION)",
			description = "Solo reseñas PUBLICADA. Con idAlojamiento se filtran las de ese alojamiento, que debe ser "
					+ "propio; sin él, se juntan las de todos sus alojamientos.")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "200", description = "Reseñas de los alojamientos del anfitrión con promedio y cantidad",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ResumenResenasDTO.class)))
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@GetMapping("/resenas/anfitrion")
	public ResponseEntity<Object> deAnfitrion(Authentication authentication,
			@Parameter(description = "Alojamiento propio a filtrar (opcional)") @RequestParam(required = false)
			Long idAlojamiento,
			@Parameter(description = DESC_PAGINA) @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = DESC_TAMANIO) @RequestParam(defaultValue = "20") int tamanio) {
		return RespuestaUtil.desde(resenaService.resumenDeAnfitrion(RespuestaUtil.idUsuario(authentication),
				idAlojamiento, RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Reseñas visibles de un alojamiento con promedio y cantidad (CA-019). Ruta
	 * pública: {@code GET /alojamientos/{idAlojamiento}/resenas}.
	 *
	 * @param idAlojamiento alojamiento
	 * @param pagina        número de página, desde 0
	 * @param tamanio       elementos por página, de 1 a 100
	 * @return 200 con el resumen; 404 si el alojamiento no existe
	 */
	@Operation(summary = "Reseñas visibles de un alojamiento con promedio y cantidad (público)", description = "Solo "
			+ "las reseñas PUBLICADA de ese alojamiento, las más recientes primero (CA-019). No requiere sesión.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Reseñas visibles con promedio y cantidad (CA-019)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ResumenResenasDTO.class)))
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@GetMapping("/alojamientos/{idAlojamiento}/resenas")
	public ResponseEntity<Object> deAlojamiento(@PathVariable Long idAlojamiento,
			@Parameter(description = DESC_PAGINA) @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = DESC_TAMANIO) @RequestParam(defaultValue = "20") int tamanio) {
		return RespuestaUtil.desde(
				resenaService.resumen(idAlojamiento, RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}
}
