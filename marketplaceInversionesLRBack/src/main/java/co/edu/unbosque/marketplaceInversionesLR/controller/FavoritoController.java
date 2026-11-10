package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.service.FavoritoService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Favoritos del usuario autenticado (HU-29). Ruta base: {@code /favoritos}.
 * Todas las rutas usan el usuario del token, así cada uno ve y maneja solo los
 * suyos.
 */
@RestController
@RequestMapping("/favoritos")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Favoritos", description = "Alojamientos guardados por el usuario")
public class FavoritoController {

	/** Mensaje cuando se quita un favorito. */
	public static final String QUITADO = "Alojamiento quitado de favoritos.";

	/** Servicio de favoritos. */
	private final FavoritoService favoritoService;

	/**
	 * Crea el controlador.
	 *
	 * @param favoritoService servicio de favoritos
	 */
	public FavoritoController(FavoritoService favoritoService) {
		this.favoritoService = favoritoService;
	}

	/**
	 * Mis favoritos (CA-170). Ruta: {@code GET /favoritos}.
	 *
	 * @param authentication usuario autenticado
	 * @param pagina         número de página (desde 0)
	 * @param tamanio        elementos por página
	 * @return 200 con la página de favoritos
	 */
	@Operation(summary = "Mis favoritos")
	@ApiResponse(responseCode = "200", description = "Página de favoritos")
	@GetMapping
	public ResponseEntity<Object> listar(Authentication authentication, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(favoritoService.listar(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, Sort.by("fechaAgregado").descending())));
	}

	/**
	 * Agrega un alojamiento a mis favoritos, sin duplicados (CA-173). Ruta:
	 * {@code POST /favoritos/{idAlojamiento}}.
	 *
	 * @param idAlojamiento  alojamiento a guardar
	 * @param authentication usuario autenticado
	 * @return 201 con el favorito nuevo; 200 si ya estaba; 404 si el alojamiento
	 *         no existe
	 */
	@Operation(summary = "Agregar a favoritos (sin duplicados)")
	@ApiResponse(responseCode = "200", description = "Ya estaba en favoritos: el favorito existente")
	@ApiResponse(responseCode = "201", description = "Favorito guardado")
	@ApiResponse(responseCode = "404", description = FavoritoService.NO_EXISTE)
	@PostMapping("/{idAlojamiento}")
	public ResponseEntity<Object> agregar(@PathVariable Long idAlojamiento, Authentication authentication) {
		Long idUsuario = RespuestaUtil.idUsuario(authentication);
		HttpStatus exito = favoritoService.esFavorito(idUsuario, idAlojamiento) ? HttpStatus.OK : HttpStatus.CREATED;
		return RespuestaUtil.desde(favoritoService.agregar(idUsuario, idAlojamiento), exito);
	}

	/**
	 * Quita un alojamiento de mis favoritos (CA-171). Ruta:
	 * {@code DELETE /favoritos/{idAlojamiento}}.
	 *
	 * @param idAlojamiento  alojamiento a quitar
	 * @param authentication usuario autenticado
	 * @return 200 si se quitó; 404 si no estaba en mis favoritos
	 */
	@Operation(summary = "Quitar de favoritos")
	@ApiResponse(responseCode = "200", description = QUITADO)
	@ApiResponse(responseCode = "404", description = FavoritoService.NO_ES_FAVORITO)
	@DeleteMapping("/{idAlojamiento}")
	public ResponseEntity<Object> eliminar(@PathVariable Long idAlojamiento, Authentication authentication) {
		Resultado<Void> resultado = favoritoService.eliminar(RespuestaUtil.idUsuario(authentication), idAlojamiento);
		return resultado.esExito() ? RespuestaUtil.mensaje(HttpStatus.OK, QUITADO) : RespuestaUtil.desde(resultado);
	}
}
