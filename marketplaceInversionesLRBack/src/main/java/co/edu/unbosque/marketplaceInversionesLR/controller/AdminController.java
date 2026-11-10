package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
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

	/** Etiqueta de Swagger de la gestión de usuarios. */
	private static final String TAG_USUARIOS = "Gestión de usuarios";

	/** Descripción del parámetro de página. */
	private static final String PAGINA = "Número de página, desde 0";

	/** Descripción del parámetro de tamaño. */
	private static final String TAMANIO = "Elementos por página, de 1 a 100";

	/** Orden de los usuarios: por id. */
	private static final Sort POR_ID = Sort.by("idUsuario");

	/** Orden de las reservas: las más recientes primero. */
	private static final Sort RESERVAS_RECIENTES = Sort.by(Sort.Order.desc("fechaCreacion"),
			Sort.Order.desc("idReserva"));

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

	/** Gestión de usuarios (HU-12). */
	private final UsuarioService usuarioService;

	/** Reservas de un usuario, para su detalle (HU-12). */
	private final ReservaService reservaService;

	/**
	 * Crea el controlador.
	 *
	 * @param tarifaService      servicio de tarifas
	 * @param publicacionService servicio de publicaciones
	 * @param usuarioService     servicio de usuarios
	 * @param reservaService     servicio de reservas
	 */
	public AdminController(TarifaService tarifaService, PublicacionService publicacionService,
			UsuarioService usuarioService, ReservaService reservaService) {
		this.tarifaService = tarifaService;
		this.publicacionService = publicacionService;
		this.usuarioService = usuarioService;
		this.reservaService = reservaService;
	}

	/**
	 * Usuarios paginados por id, con su estado y el motivo de la restricción si
	 * la tienen (HU-12, CA-058). Ruta: {@code GET /admin/usuarios}.
	 *
	 * @param pagina  número de página, desde 0
	 * @param tamanio elementos por página, de 1 a {@value #TAMANIO_MAXIMO}
	 * @return 200 con la página de usuarios
	 */
	@Tag(name = TAG_USUARIOS, description = "Consultar, restringir y liberar usuarios")
	@Operation(summary = "Usuarios, paginados por id (ADMIN)", description = "Cada usuario trae su estado y, si está "
			+ "RESTRINGIDO, el motivoRestriccion (CA-058).")
	@ApiResponse(responseCode = "200", description = "Página de usuarios")
	@GetMapping("/usuarios")
	public ResponseEntity<Object> usuarios(
			@Parameter(description = PAGINA) @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = TAMANIO) @RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(usuarioService.listarPaginado(RespuestaUtil.pagina(pagina, tamanio, POR_ID)));
	}

	/**
	 * Reservas de un usuario como huésped, las más recientes primero, para su
	 * detalle (HU-12). Ruta: {@code GET /admin/usuarios/{id}/reservas}.
	 *
	 * @param id      usuario
	 * @param pagina  número de página, desde 0
	 * @param tamanio elementos por página, de 1 a {@value #TAMANIO_MAXIMO}
	 * @return 200 con la página de reservas (vacía si no tiene); 404 si el
	 *         usuario no existe
	 */
	@Tag(name = TAG_USUARIOS)
	@Operation(summary = "Reservas de un usuario como huésped (ADMIN)", description = "Para el detalle de usuario, las "
			+ "más recientes primero, con su último pago.")
	@ApiResponse(responseCode = "200", description = "Reservas del usuario como huésped (vacía si no tiene)")
	@ApiResponse(responseCode = "404", description = UsuarioService.NO_ENCONTRADO)
	@GetMapping("/usuarios/{id}/reservas")
	public ResponseEntity<Object> reservasDeUsuario(@PathVariable Long id,
			@Parameter(description = PAGINA) @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = TAMANIO) @RequestParam(defaultValue = "20") int tamanio) {
		if (!usuarioService.existe(id)) {
			return RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, UsuarioService.NO_ENCONTRADO);
		}
		return ResponseEntity
				.ok(reservaService.listarDeHuesped(id, RespuestaUtil.pagina(pagina, tamanio, RESERVAS_RECIENTES)));
	}

	/**
	 * Alojamientos de un usuario como anfitrión, en cualquier estado y los más
	 * recientes primero, para su detalle (HU-12). Ruta:
	 * {@code GET /admin/usuarios/{id}/alojamientos}.
	 *
	 * @param id      usuario
	 * @param pagina  número de página, desde 0
	 * @param tamanio elementos por página, de 1 a {@value #TAMANIO_MAXIMO}
	 * @return 200 con la página de alojamientos (vacía si no tiene); 404 si el
	 *         usuario no existe
	 */
	@Tag(name = TAG_USUARIOS)
	@Operation(summary = "Alojamientos de un usuario como anfitrión, en cualquier estado (ADMIN)")
	@ApiResponse(responseCode = "200", description = "Alojamientos del usuario como anfitrión (vacía si no tiene)")
	@ApiResponse(responseCode = "404", description = UsuarioService.NO_ENCONTRADO)
	@GetMapping("/usuarios/{id}/alojamientos")
	public ResponseEntity<Object> alojamientosDeUsuario(@PathVariable Long id,
			@Parameter(description = PAGINA) @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = TAMANIO) @RequestParam(defaultValue = "20") int tamanio) {
		if (!usuarioService.existe(id)) {
			return RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, UsuarioService.NO_ENCONTRADO);
		}
		return ResponseEntity.ok(publicacionService.listarDeAnfitrion(id, pagina(pagina, tamanio)));
	}

	/**
	 * Restringe un usuario con un motivo (HU-12, CA-058). Queda RESTRINGIDO, se
	 * le notifica el motivo y desde ese momento no puede iniciar sesión ni usar
	 * sus tokens (CA-059). Ruta: {@code PATCH /admin/usuarios/{id}/restringir}.
	 *
	 * @param id             usuario
	 * @param datos          DTO con el {@code motivo}
	 * @param authentication administrador autenticado
	 * @return 200 con el usuario RESTRINGIDO; 400 si falta el motivo o es muy
	 *         largo; 403 si es administrador; 404 si no existe
	 */
	@Tag(name = TAG_USUARIOS)
	@Operation(summary = "Restringir un usuario con motivo (ADMIN)", description = "Registra y aplica la restricción, "
			+ "que se ve al consultar el usuario (CA-058), y le notifica el motivo. Desde ese momento no puede iniciar "
			+ "sesión, sus tokens dejan de servir y no puede reactivarse sola (CA-059). A un administrador no se le "
			+ "puede restringir.")
	@ApiResponse(responseCode = "200", description = "Usuario RESTRINGIDO con su motivoRestriccion; se le notifica")
	@ApiResponse(responseCode = "400", description = UsuarioService.MOTIVO_INVALIDO)
	@ApiResponse(responseCode = "403", description = UsuarioService.NO_RESTRINGIR_ADMIN
			+ " | No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = UsuarioService.NO_ENCONTRADO)
	@PatchMapping("/usuarios/{id}/restringir")
	public ResponseEntity<Object> restringir(@PathVariable Long id, @RequestBody MotivoDTO datos,
			Authentication authentication) {
		return RespuestaUtil
				.desde(usuarioService.restringir(id, datos.getMotivo(), RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Habilita un usuario restringido o inactivo; el motivo es opcional y el
	 * cuerpo se puede omitir (HU-12). Ruta:
	 * {@code PATCH /admin/usuarios/{id}/liberar}.
	 *
	 * @param id             usuario
	 * @param datos          DTO con el {@code motivo}; puede no enviarse
	 * @param authentication administrador autenticado
	 * @return 200 con el usuario ACTIVO; 400 si el motivo es muy largo; 404 si
	 *         no existe; 409 si ya estaba activo
	 */
	@Tag(name = TAG_USUARIOS)
	@Operation(summary = "Activar un usuario restringido o inactivo (ADMIN)", description = "El motivo es opcional y el "
			+ "cuerpo se puede omitir. La cuenta queda ACTIVO, sin motivo de restricción, y se le notifica.")
	@ApiResponse(responseCode = "200", description = "Usuario ACTIVO; se le notifica")
	@ApiResponse(responseCode = "400", description = UsuarioService.MOTIVO_LARGO)
	@ApiResponse(responseCode = "404", description = UsuarioService.NO_ENCONTRADO)
	@ApiResponse(responseCode = "409", description = UsuarioService.YA_ACTIVA)
	@PatchMapping("/usuarios/{id}/liberar")
	public ResponseEntity<Object> liberar(@PathVariable Long id, @RequestBody(required = false) MotivoDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(usuarioService.liberar(id, datos == null ? null : datos.getMotivo(),
				RespuestaUtil.idUsuario(authentication)));
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
	 * Bloquea una publicación con motivo; sus reservas futuras pasan a
	 * EN_REVISION (HU-11). Ruta: {@code POST /admin/alojamientos/{id}/bloquear}.
	 *
	 * @param id             alojamiento
	 * @param datos          DTO con el {@code motivo}
	 * @param authentication administrador autenticado
	 * @return 200 con el alojamiento; 400 si falta el motivo; 404 si no existe;
	 *         409 si ya estaba bloqueado
	 */
	@Tag(name = TAG_MODERACION)
	@Operation(summary = "Bloquear con motivo (las reservas futuras pasan a EN_REVISION)")
	@ApiResponse(responseCode = "200", description = "Alojamiento BLOQUEADA; sus reservas futuras pasan a EN_REVISION")
	@ApiResponse(responseCode = "400", description = PublicacionService.MOTIVO_BLOQUEO + " | " + LimitesTexto.MOTIVO_LARGO)
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = "No se puede pasar de BLOQUEADA a BLOQUEADA.")
	@PostMapping("/alojamientos/{id}/bloquear")
	public ResponseEntity<Object> bloquear(@PathVariable Long id, @RequestBody MotivoDTO datos,
			Authentication authentication) {
		return RespuestaUtil
				.desde(publicacionService.bloquear(id, RespuestaUtil.idUsuario(authentication), datos.getMotivo()));
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
