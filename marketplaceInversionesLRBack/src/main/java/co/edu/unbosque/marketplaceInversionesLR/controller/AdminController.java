package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConfiguracionTarifaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MotivoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.TarifaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

	/** Etiqueta de Swagger de las tarifas. */
	private static final String TAG_TARIFAS = "Tarifas";

	/** Etiqueta de Swagger de la moderación de publicaciones. */
	private static final String TAG_MODERACION = "Moderación de publicaciones";

	/** Etiqueta de Swagger de la auditoría. */
	private static final String TAG_AUDITORIA = "Auditoría";

	/** Elementos por página como máximo. */
	public static final int TAMANIO_MAXIMO = 100;

	/** Orden de los listados: lo más reciente primero. */
	private static final Sort RECIENTES = Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("idAlojamiento"));

	/** Mensaje del 409 cuando la transición no está permitida. */
	private static final String TRANSICION = "No se puede pasar de BORRADOR a ACTIVA.";

	/** Porcentajes de limpieza y servicio. */
	private final TarifaService tarifaService;

	/** Moderación de publicaciones. */
	private final PublicacionService publicacionService;

	/** Consulta de la auditoría. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el controlador.
	 *
	 * @param tarifaService      servicio de tarifas
	 * @param publicacionService servicio de publicaciones
	 * @param auditoriaService   servicio de auditoría
	 */
	public AdminController(TarifaService tarifaService, PublicacionService publicacionService,
			AuditoriaService auditoriaService) {
		this.tarifaService = tarifaService;
		this.publicacionService = publicacionService;
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Porcentajes globales vigentes de limpieza y servicio (HU-24, RF-023). Ruta:
	 * {@code GET /admin/tarifas}.
	 *
	 * @return 200 con los porcentajes vigentes, o los de por defecto con
	 *         {@code porDefecto=true}
	 */
	@Tag(name = TAG_TARIFAS, description = "Porcentajes globales de limpieza y servicio")
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
	@Tag(name = TAG_TARIFAS)
	@Operation(summary = "Cambiar los porcentajes globales (ADMIN o ANFITRION; aplican a cotizaciones nuevas)")
	@ApiResponse(responseCode = "200", description = "Configuración nueva; aplica a las cotizaciones siguientes")
	@ApiResponse(responseCode = "400", description = "Los porcentajes de limpieza y servicio son obligatorios y deben "
			+ "estar entre 0 y 100.")
	@PutMapping("/tarifas")
	public ResponseEntity<Object> actualizarTarifas(@RequestBody ConfiguracionTarifaDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(tarifaService.actualizar(datos, RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Consulta la auditoría con filtros opcionales por acción, actor y fechas
	 * (HU-13, CA-062). Ruta: {@code GET /admin/auditoria}.
	 *
	 * @param accion  acción exacta; opcional
	 * @param idActor usuario que hizo la acción; opcional
	 * @param desde   fecha y hora mínima; opcional
	 * @param hasta   fecha y hora máxima; opcional
	 * @param pagina  número de página (desde 0)
	 * @param tamanio elementos por página
	 * @return 200 con la página de eventos, del más reciente al más antiguo
	 */
	@Tag(name = TAG_AUDITORIA, description = "Consulta de los eventos de auditoría")
	@Operation(summary = "Consultar la auditoría con filtros por acción, actor y fechas")
	@ApiResponse(responseCode = "200", description = "Página de eventos, del más reciente al más antiguo")
	@ApiResponse(responseCode = "400", description = "El valor de desde no es válido.")
	@GetMapping("/auditoria")
	public ResponseEntity<Object> auditoria(@RequestParam(required = false) String accion,
			@RequestParam(required = false) Long idActor,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "50") int tamanio) {
		return ResponseEntity.ok(auditoriaService.consultar(accion, idActor, desde, hasta, RespuestaUtil.pagina(pagina,
				tamanio, Sort.by("fecha").descending().and(Sort.by("idEvento").descending()))));
	}

	/**
	 * Detalle de un evento de auditoría (HU-13, CA-062). Ruta:
	 * {@code GET /admin/auditoria/{id}}.
	 *
	 * @param id evento
	 * @return 200 con el evento; 404 si no existe
	 */
	@Tag(name = TAG_AUDITORIA)
	@Operation(summary = "Detalle de un evento de auditoría")
	@ApiResponse(responseCode = "200", description = "Evento de auditoría")
	@ApiResponse(responseCode = "404", description = AuditoriaService.EVENTO_NO_EXISTE)
	@GetMapping("/auditoria/{id}")
	public ResponseEntity<Object> evento(@PathVariable Long id) {
		return RespuestaUtil.desde(auditoriaService.obtener(id));
	}

	/**
	 * Alojamientos por estado, por ejemplo la cola PENDIENTE_REVISION; sin
	 * estado, todos (HU-32). Ruta: {@code GET /admin/alojamientos}.
	 *
	 * @param estado  estado a filtrar; opcional
	 * @param pagina  número de página, desde 0
	 * @param tamanio elementos por página, de 1 a {@value #TAMANIO_MAXIMO}
	 * @return 200 con la página de alojamientos, los más recientes primero
	 */
	@Tag(name = TAG_MODERACION, description = "Revisar, aprobar y rechazar publicaciones")
	@Operation(summary = "Alojamientos por estado (por ejemplo, la cola PENDIENTE_REVISION)")
	@ApiResponse(responseCode = "200", description = "Página de alojamientos, todos o los del estado indicado")
	@ApiResponse(responseCode = "400", description = "El valor de estado no es válido.")
	@GetMapping("/alojamientos")
	public ResponseEntity<Object> alojamientos(
			@Parameter(description = "Estado a filtrar, por ejemplo PENDIENTE_REVISION") @RequestParam(required = false) EstadoAlojamiento estado,
			@RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(publicacionService.listarPorEstado(estado, pagina(pagina, tamanio)));
	}

	/**
	 * Aprueba una publicación: PENDIENTE_REVISION → ACTIVA, y se le avisa al
	 * anfitrión (CA-158). Ruta: {@code POST /admin/alojamientos/{id}/aprobar}.
	 *
	 * @param id             alojamiento
	 * @param authentication administrador autenticado
	 * @return 200 con el alojamiento ACTIVA; 404 si no existe; 409 si no está en
	 *         revisión o ya no tiene fechas
	 */
	@Tag(name = TAG_MODERACION)
	@Operation(summary = "Aprobar: PENDIENTE_REVISION → ACTIVA (notifica al anfitrión)")
	@ApiResponse(responseCode = "200", description = "Alojamiento ACTIVA; se notifica al anfitrión")
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = TRANSICION + " | " + PublicacionService.SIN_FECHAS)
	@PostMapping("/alojamientos/{id}/aprobar")
	public ResponseEntity<Object> aprobar(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.aprobar(id, RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Rechaza una publicación con motivo: PENDIENTE_REVISION → BORRADOR, y se
	 * le avisa al anfitrión (CA-195). Ruta:
	 * {@code POST /admin/alojamientos/{id}/rechazar}.
	 *
	 * @param id             alojamiento
	 * @param datos          DTO con el {@code motivo}
	 * @param authentication administrador autenticado
	 * @return 200 con el alojamiento en BORRADOR; 400 si falta el motivo o es
	 *         muy largo; 404 si no existe; 409 si no está en revisión
	 */
	@Tag(name = TAG_MODERACION)
	@Operation(summary = "Rechazar con motivo: PENDIENTE_REVISION → BORRADOR (notifica al anfitrión)")
	@ApiResponse(responseCode = "200", description = "Alojamiento devuelto a BORRADOR; se notifica el motivo al "
			+ "anfitrión")
	@ApiResponse(responseCode = "400", description = PublicacionService.MOTIVO_INVALIDO)
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = "No se puede pasar de ACTIVA a BORRADOR.")
	@PostMapping("/alojamientos/{id}/rechazar")
	public ResponseEntity<Object> rechazar(@PathVariable Long id, @RequestBody MotivoDTO datos,
			Authentication authentication) {
		return RespuestaUtil
				.desde(publicacionService.rechazar(id, RespuestaUtil.idUsuario(authentication), datos.getMotivo()));
	}

	/**
	 * Arma la página pedida dentro de los límites: tamaño de 1 a
	 * {@value #TAMANIO_MAXIMO} y página no negativa ni tan grande que el
	 * desplazamiento se desborde.
	 *
	 * @param pagina  número de página pedido
	 * @param tamanio tamaño pedido
	 * @return página válida, con lo más reciente primero
	 */
	static Pageable pagina(int pagina, int tamanio) {
		int tamanioValido = Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO);
		int paginaValida = Math.min(Math.max(pagina, 0), Integer.MAX_VALUE / tamanioValido - 1);
		return PageRequest.of(paginaValida, tamanioValido, RECIENTES);
	}
}
