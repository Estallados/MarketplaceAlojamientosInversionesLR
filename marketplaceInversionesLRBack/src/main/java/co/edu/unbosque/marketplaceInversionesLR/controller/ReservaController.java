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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.CancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.CotizacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MotivoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SimulacionCancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SolicitudReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.CancelacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.CotizacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.IdempotenciaService;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cotizaciones, reservas, historial y cancelaciones (HU-22, HU-25 y HU-01,
 * servicios S06, S07 y S09). Contrato: {@code docs/openapi/reservas-pagos.yaml}.
 * <p>
 * El huésped cotiza ({@code POST /cotizaciones}) y con la cotización vigente
 * reserva y paga en una sola petición ({@code POST /reservas}). Con el pago
 * aprobado la reserva bloquea las fechas de inmediato. Después puede simular y
 * cancelar la reserva según la política del alojamiento; cancelar es
 * idempotente con la cabecera {@code Idempotency-Key}.
 * </p>
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
public class ReservaController {

	/** Etiqueta de Swagger de la cotización y la reserva. */
	private static final String TAG_RESERVAS = "Cotización y reservas";

	/** Mensajes del 400 por fechas inválidas. */
	private static final String FECHAS_INVALIDAS = CalendarioService.FECHAS_FALTANTES + " | "
			+ CalendarioService.SALIDA_NO_POSTERIOR + " | " + CalendarioService.LLEGADA_EN_EL_PASADO;

	/** Mensaje del 409 cuando el alojamiento no está ACTIVA. */
	private static final String NO_ACTIVA = "El alojamiento no está disponible para nuevas reservas (estado PAUSADA).";

	/** Etiqueta de Swagger de las cancelaciones. */
	private static final String TAG_CANCELACIONES = "Cancelaciones";

	/** Mensajes del 409 cuando la reserva no se puede cancelar. */
	private static final String NO_CANCELABLE = CancelacionService.ESTADO_NO_CANCELABLE + "CANCELADA. | "
			+ CancelacionService.SOLO_FUTURAS;

	/** Etiqueta de Swagger del historial de reservas (HU-25). */
	private static final String TAG_HISTORIAL = "Historial de reservas";

	/** Orden de las listas: las reservas más recientes primero. */
	private static final Sort RECIENTES = Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("idReserva"));

	/** Operación protegida con Idempotency-Key. */
	public static final String OPERACION_CANCELAR = "CANCELAR_RESERVA";

	/** Servicio de cotizaciones. */
	private final CotizacionService cotizacionService;

	/** Servicio de reservas. */
	private final ReservaService reservaService;

	/** Cancelaciones (HU-01). */
	private final CancelacionService cancelacionService;

	/** Evita cancelar dos veces con la misma Idempotency-Key (RNF-008). */
	private final IdempotenciaService idempotenciaService;

	/**
	 * Crea el controlador.
	 *
	 * @param cotizacionService   servicio de cotizaciones
	 * @param reservaService      servicio de reservas
	 * @param cancelacionService  servicio de cancelaciones
	 * @param idempotenciaService servicio de idempotencia
	 */
	public ReservaController(CotizacionService cotizacionService, ReservaService reservaService,
			CancelacionService cancelacionService, IdempotenciaService idempotenciaService) {
		this.cotizacionService = cotizacionService;
		this.reservaService = reservaService;
		this.cancelacionService = cancelacionService;
		this.idempotenciaService = idempotenciaService;
	}

	/**
	 * Cotiza una estancia: noches, precio base, descuento, limpieza, servicio,
	 * adicionales y total. La cotización sirve
	 * {@value CotizacionService#MINUTOS_VIGENCIA} minutos para reservar. Ruta:
	 * {@code POST /cotizaciones}.
	 *
	 * @param solicitud      alojamiento, fechas, huéspedes y servicios elegidos
	 * @param authentication usuario autenticado
	 * @return 201 con la cotización; 400 si los datos no son válidos; 404 si el
	 *         alojamiento no existe; 409 si no está ACTIVA o las fechas no están
	 *         libres
	 */
	@Tag(name = TAG_RESERVAS, description = "Cotizar una estancia y reservarla")
	@Operation(summary = "Cotizar una estancia", description = "Devuelve noches, precio base, descuento, limpieza, "
			+ "servicio, adicionales y total con los porcentajes vigentes. La cotización sirve 30 minutos para reservar.")
	@ApiResponse(responseCode = "201", description = "Cotización con el desglose")
	@ApiResponse(responseCode = "400", description = CotizacionService.FALTA_ALOJAMIENTO + " | " + FECHAS_INVALIDAS
			+ " | " + CotizacionService.HUESPEDES_INVALIDOS + " | La cantidad de huéspedes (9) supera la capacidad "
			+ "del alojamiento (4). | El servicio adicional 7 no pertenece a este alojamiento.")
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = NO_ACTIVA + " | " + CotizacionService.FECHAS_NO_DISPONIBLES)
	@PostMapping("/cotizaciones")
	public ResponseEntity<Object> cotizar(@RequestBody CotizacionDTO solicitud, Authentication authentication) {
		return RespuestaUtil.desde(cotizacionService.cotizar(solicitud, RespuestaUtil.idUsuario(authentication)),
				HttpStatus.CREATED);
	}

	/**
	 * Crea y paga una reserva desde una cotización vigente (solo huéspedes).
	 * Revalida la disponibilidad antes de confirmar; con el pago de prueba
	 * aprobado la reserva queda CONFIRMADA con el monto cotizado. Ruta:
	 * {@code POST /reservas}.
	 *
	 * @param solicitud      cotización a reservar y método de pago
	 * @param authentication huésped autenticado
	 * @return 201 con la reserva CONFIRMADA y su pago; 402 o 502 con la reserva
	 *         CANCELADA si el pago no se aprobó; 400, 403, 404 o 409 según el
	 *         caso
	 */
	@Tag(name = TAG_RESERVAS)
	@Operation(summary = "Crear y pagar una reserva (HUESPED)", description = "Revalida la disponibilidad justo "
			+ "antes de confirmar. La reserva nace en PENDIENTE_PAGO, se hace el pago de prueba (modo SIMULADO) y, si "
			+ "se aprueba, queda CONFIRMADA con el monto exacto de la cotización y sus fechas dejan de estar "
			+ "disponibles. Si el pago se rechaza o falla queda CANCELADA y la cotización se puede usar con otro "
			+ "método. metodoPago es opcional (por defecto pm_card_visa); pm_card_chargeDeclined simula un rechazo "
			+ "y pm_card_error una falla de la pasarela.")
	@PreAuthorize("hasRole('HUESPED')")
	@ApiResponse(responseCode = "201", description = "Reserva CONFIRMADA y pagada, con su pago (CA-070, CA-075)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ReservaDTO.class)))
	@ApiResponse(responseCode = "400", description = ReservaService.FALTA_COTIZACION + " | "
			+ PagoService.METODO_INVALIDO + " | " + FECHAS_INVALIDAS)
	@ApiResponse(responseCode = "402", description = "El pago fue rechazado: La tarjeta fue rechazada por el banco. "
			+ "La reserva no quedó pagada. (con la reserva CANCELADA en \"dato\")")
	@ApiResponse(responseCode = "502", description = "No se pudo confirmar el pago: "
			+ PagoService.FALLA_PASARELA + ReservaService.SIN_PAGAR + " (con la reserva CANCELADA en \"dato\")")
	@ApiResponse(responseCode = "403", description = ReservaService.PROPIO_ALOJAMIENTO
			+ " | No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = ReservaService.COTIZACION_NO_EXISTE + " | "
			+ CalendarioService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = ReservaService.COTIZACION_NO_VIGENTE + " | " + NO_ACTIVA + " | "
			+ ReservaService.PERIODO_TOMADO)
	@PostMapping("/reservas")
	public ResponseEntity<Object> crear(@RequestBody SolicitudReservaDTO solicitud, Authentication authentication) {
		return RespuestaUtil.desde(reservaService.crear(solicitud, RespuestaUtil.idUsuario(authentication)),
				HttpStatus.CREATED);
	}

	/**
	 * Historial del usuario autenticado: todas sus reservas como huésped, en
	 * cualquier estado, de la más reciente a la más antigua (HU-25, CA-078).
	 * Ruta: {@code GET /reservas/mias}.
	 *
	 * @param authentication usuario autenticado
	 * @param pagina         número de página, desde 0
	 * @param tamanio        elementos por página, de 1 a 100
	 * @return 200 con la página de reservas; vacía si no tiene
	 */
	@Tag(name = TAG_HISTORIAL, description = "Consultar el estado y el historial de las reservas")
	@Operation(summary = "Mis reservas", description = "Todas las reservas del usuario autenticado como huésped, en "
			+ "cualquier estado (PENDIENTE_PAGO, CONFIRMADA, EN_REVISION, CANCELADA), las más recientes primero. "
			+ "Cada una trae el alojamiento, su imagen principal, la política, el último pago, la cancelación si la "
			+ "hubo, si ya está completada y si ya tiene reseña (CA-078).")
	@ApiResponse(responseCode = "200", description = "Página de reservas del huésped (CA-078)")
	@GetMapping("/reservas/mias")
	public ResponseEntity<Object> mias(Authentication authentication,
			@Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = "Elementos por página, de 1 a 100") @RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(reservaService.listarMias(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Reservas de los alojamientos del anfitrión autenticado, nunca las de
	 * alojamientos de otros (HU-25, CA-079). Ruta:
	 * {@code GET /reservas/anfitrion}.
	 *
	 * @param authentication anfitrión autenticado
	 * @param pagina         número de página, desde 0
	 * @param tamanio        elementos por página, de 1 a 100
	 * @return 200 con la página de reservas; 403 si no es anfitrión
	 */
	@Tag(name = TAG_HISTORIAL)
	@Operation(summary = "Reservas de mis alojamientos (ANFITRION)", description = "Todas las reservas de los "
			+ "alojamientos del anfitrión autenticado, en cualquier estado, las más recientes primero. Nunca incluye "
			+ "reservas de alojamientos de otros anfitriones (CA-079).")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "200", description = "Página de reservas de sus alojamientos (CA-079)")
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@GetMapping("/reservas/anfitrion")
	public ResponseEntity<Object> deAnfitrion(Authentication authentication,
			@Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = "Elementos por página, de 1 a 100") @RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(reservaService.listarDeAnfitrion(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Detalle de una reserva (HU-25). La ven su huésped, el anfitrión del
	 * alojamiento y los administradores; para los demás responde 404. Ruta:
	 * {@code GET /reservas/{id}}.
	 *
	 * @param id             reserva
	 * @param authentication usuario autenticado
	 * @return 200 con la reserva; 404 si no existe o no la puede ver
	 */
	@Tag(name = TAG_HISTORIAL)
	@Operation(summary = "Detalle de una reserva", description = "Estado actual, fechas, huéspedes, total, desglose, "
			+ "último pago, alojamiento, huésped, anfitrión, política y cancelación si la hubo. Solo la ven su "
			+ "huésped, el anfitrión del alojamiento y los administradores; para los demás no existe.")
	@ApiResponse(responseCode = "200", description = "Detalle de la reserva",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = ReservaDTO.class)))
	@ApiResponse(responseCode = "404", description = ReservaService.NO_EXISTE)
	@GetMapping("/reservas/{id}")
	public ResponseEntity<Object> obtener(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(reservaService.obtener(id, RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Simula la cancelación de una reserva propia (solo huéspedes): la política,
	 * los días de anticipación, el porcentaje y el valor que se devolvería si
	 * se cancelara hoy (HU-01, CA-001). No cancela nada. Ruta:
	 * {@code GET /reservas/{id}/simularcancelacion}.
	 *
	 * @param id             reserva
	 * @param authentication huésped autenticado
	 * @return 200 con la estimación; 404 si no existe o no es suya; 409 si no
	 *         se puede cancelar
	 */
	@Tag(name = TAG_CANCELACIONES, description = "Simular y cancelar reservas según la política")
	@Operation(summary = "Simular la cancelación de una reserva (HUESPED)", description = "Devuelve la política, los "
			+ "días de anticipación, el porcentaje y el valor que se devolvería si se cancelara hoy (CA-001). No "
			+ "cancela nada. Solo aplica a las reservas propias.")
	@PreAuthorize("hasRole('HUESPED')")
	@ApiResponse(responseCode = "200", description = "Devolución que recibiría si cancela hoy",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = SimulacionCancelacionDTO.class)))
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = CancelacionService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = NO_CANCELABLE)
	@GetMapping("/reservas/{id}/simularcancelacion")
	public ResponseEntity<Object> simularCancelacion(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(cancelacionService.simular(id, RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Cancela una reserva futura propia (solo huéspedes) aplicando la política
	 * del alojamiento (HU-01): la reserva queda CANCELADA, se registra la
	 * cancelación (CA-004), se devuelve lo que corresponde y las fechas quedan
	 * libres (CA-007); si la política no da devolución, se respeta (CA-009).
	 * Exige la cabecera Idempotency-Key: un reintento con la misma clave
	 * devuelve la misma respuesta sin cancelar dos veces (CA-140, CA-141).
	 * Ruta: {@code POST /reservas/{id}/cancelar}.
	 *
	 * @param id             reserva
	 * @param datos          DTO con el {@code motivo}
	 * @param clave          valor de la cabecera Idempotency-Key
	 * @param authentication huésped autenticado
	 * @return 200 con la cancelación y la devolución; 400, 404, 409 o 422
	 *         según el caso
	 */
	@Tag(name = TAG_CANCELACIONES)
	@Operation(summary = "Cancelar una reserva futura (HUESPED)", description = "Aplica la política de cancelación "
			+ "del alojamiento (CA-001): FLEXIBLE 100 % con 1 o más días; MODERADA 100 % con 5 o más días y 50 % con "
			+ "1 a 4; ESTRICTA 50 % con 7 o más días y nada después. La reserva queda CANCELADA, sus fechas vuelven a "
			+ "estar disponibles y se registran usuario, fecha, motivo, devolución y estado final (CA-004). Solo "
			+ "reservas propias CONFIRMADA o EN_REVISION con la llegada en el futuro. Requiere motivo y la cabecera "
			+ "Idempotency-Key; con la misma clave y los mismos datos se devuelve la respuesta guardada "
			+ "(Idempotent-Replayed: true).")
	@PreAuthorize("hasRole('HUESPED')")
	@ApiResponse(responseCode = "200", description = "Cancelación con la devolución según la política (CA-004)",
			content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
					schema = @Schema(implementation = CancelacionDTO.class)))
	@ApiResponse(responseCode = "400", description = CancelacionService.MOTIVO_INVALIDO + " | "
			+ IdempotenciaService.CLAVE_INVALIDA)
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = CancelacionService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = NO_CANCELABLE + " | " + IdempotenciaService.EN_PROCESO)
	@ApiResponse(responseCode = "422", description = IdempotenciaService.CLAVE_REUTILIZADA)
	@PostMapping("/reservas/{id}/cancelar")
	public ResponseEntity<Object> cancelar(@PathVariable Long id, @RequestBody MotivoDTO datos,
			@Parameter(in = ParameterIn.HEADER, name = IdempotenciaService.CABECERA, required = true,
					description = "Clave única por intento (1 a 100 caracteres); repítela solo al reintentar la misma "
							+ "petición", example = "3f2b8c1e-7a44-4d0b-9e21-6c1f0a7d5e10")
			@RequestHeader(value = IdempotenciaService.CABECERA, required = false) String clave,
			Authentication authentication) {
		Long idHuesped = RespuestaUtil.idUsuario(authentication);
		String motivo = datos.getMotivo();
		return idempotenciaService.ejecutar(idHuesped, OPERACION_CANCELAR, clave, new Object[] { id, motivo },
				() -> RespuestaUtil.desde(cancelacionService.cancelar(id, motivo, idHuesped)));
	}
}
