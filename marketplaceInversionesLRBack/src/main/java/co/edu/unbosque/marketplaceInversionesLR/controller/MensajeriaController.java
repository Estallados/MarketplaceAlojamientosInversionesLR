package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConversacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MensajeDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.MensajeriaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST de la mensajería entre huésped y anfitrión (HU-28). Ruta
 * base: {@code /conversaciones}. Solo los participantes acceden a cada
 * conversación.
 */
@RestController
@RequestMapping("/conversaciones")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mensajería", description = "Conversaciones entre huésped y anfitrión")
public class MensajeriaController {

	/** Servicio de mensajería. */
	private final MensajeriaService mensajeriaService;

	/**
	 * Crea el controlador.
	 *
	 * @param mensajeriaService servicio de mensajería
	 */
	public MensajeriaController(MensajeriaService mensajeriaService) {
		this.mensajeriaService = mensajeriaService;
	}

	/**
	 * Inicia o retoma una conversación con el anfitrión de un alojamiento
	 * (CA-169). Ruta: {@code POST /conversaciones}.
	 *
	 * @param datos          DTO con {@code idAlojamiento} y, si se quiere, un
	 *                       primer {@code mensaje}
	 * @param authentication usuario autenticado
	 * @return 201 con la conversación nueva; 200 si ya existía; 400 si los datos
	 *         no son válidos; 403 si es su propio alojamiento; 404 si el
	 *         alojamiento no existe
	 */
	@Operation(summary = "Iniciar o retomar una conversación con el anfitrión de un alojamiento")
	@ApiResponse(responseCode = "200", description = "Ya existía: la conversación de antes (con el mensaje nuevo, si "
			+ "se envió)")
	@ApiResponse(responseCode = "201", description = "Conversación creada con el anfitrión")
	@ApiResponse(responseCode = "400", description = MensajeriaService.FALTA_ALOJAMIENTO + " | "
			+ MensajeriaService.PRIMER_MENSAJE_LARGO)
	@ApiResponse(responseCode = "403", description = MensajeriaService.PROPIO_ALOJAMIENTO)
	@ApiResponse(responseCode = "404", description = MensajeriaService.ALOJAMIENTO_NO_EXISTE)
	@PostMapping
	public ResponseEntity<Object> iniciar(@RequestBody ConversacionDTO datos, Authentication authentication) {
		Long idUsuario = RespuestaUtil.idUsuario(authentication);
		HttpStatus exito = datos != null && mensajeriaService.tieneConversacion(idUsuario, datos.getIdAlojamiento())
				? HttpStatus.OK
				: HttpStatus.CREATED;
		return RespuestaUtil.desde(mensajeriaService.iniciar(datos, idUsuario), exito);
	}

	/**
	 * Mis conversaciones (CA-167). Ruta: {@code GET /conversaciones}.
	 *
	 * @param authentication usuario autenticado
	 * @param pagina         número de página (desde 0)
	 * @param tamanio        elementos por página
	 * @return 200 con la página de conversaciones
	 */
	@Operation(summary = "Mis conversaciones")
	@ApiResponse(responseCode = "200", description = "Página de conversaciones del usuario")
	@GetMapping
	public ResponseEntity<Object> listar(Authentication authentication, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(mensajeriaService.listar(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, Sort.by("fechaCreacion").descending())));
	}

	/**
	 * Mensajes de una conversación, solo para sus participantes (CA-167). Ruta:
	 * {@code GET /conversaciones/{id}/mensajes}.
	 *
	 * @param id             conversación
	 * @param authentication usuario autenticado
	 * @param pagina         número de página (desde 0)
	 * @param tamanio        elementos por página
	 * @return 200 con la página de mensajes; 403 si no participa; 404 si no
	 *         existe
	 */
	@Operation(summary = "Mensajes de una conversación (solo participantes)")
	@ApiResponse(responseCode = "200", description = "Página de mensajes")
	@ApiResponse(responseCode = "403", description = MensajeriaService.NO_PARTICIPA)
	@ApiResponse(responseCode = "404", description = MensajeriaService.NO_EXISTE)
	@GetMapping("/{id}/mensajes")
	public ResponseEntity<Object> mensajes(@PathVariable Long id, Authentication authentication,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanio) {
		return RespuestaUtil.desde(mensajeriaService.mensajes(id, RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, Sort.by("fechaEnvio").and(Sort.by("idMensaje")))));
	}

	/**
	 * Envía un mensaje en una conversación propia y se le notifica al otro
	 * participante (CA-165). Ruta: {@code POST /conversaciones/{id}/mensajes}.
	 *
	 * @param id             conversación
	 * @param datos          DTO con el {@code contenido}
	 * @param authentication usuario autenticado
	 * @return 201 con el mensaje; 400 si está vacío o es muy largo; 403 si no
	 *         participa; 404 si no existe
	 */
	@Operation(summary = "Enviar un mensaje (solo participantes)")
	@ApiResponse(responseCode = "201", description = "Mensaje enviado; se notifica al otro participante")
	@ApiResponse(responseCode = "400", description = MensajeriaService.MENSAJE_INVALIDO)
	@ApiResponse(responseCode = "403", description = MensajeriaService.NO_PARTICIPA)
	@ApiResponse(responseCode = "404", description = MensajeriaService.NO_EXISTE)
	@PostMapping("/{id}/mensajes")
	public ResponseEntity<Object> enviar(@PathVariable Long id, @RequestBody MensajeDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(
				mensajeriaService.enviar(id, datos.getContenido(), RespuestaUtil.idUsuario(authentication)),
				HttpStatus.CREATED);
	}
}
