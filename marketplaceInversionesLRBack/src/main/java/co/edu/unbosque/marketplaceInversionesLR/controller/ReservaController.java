package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.CotizacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SolicitudReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.CotizacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cotizaciones y reservas (HU-22, servicios S06 y S07). Contrato:
 * {@code docs/openapi/reservas-pagos.yaml}.
 * <p>
 * El huésped cotiza ({@code POST /cotizaciones}) y con la cotización vigente
 * reserva y paga en una sola petición ({@code POST /reservas}). Con el pago
 * aprobado la reserva bloquea las fechas de inmediato.
 * </p>
 */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cotización y reservas", description = "Cotizar una estancia y reservarla")
public class ReservaController {

	/** Mensajes del 400 por fechas inválidas. */
	private static final String FECHAS_INVALIDAS = CalendarioService.FECHAS_FALTANTES + " | "
			+ CalendarioService.SALIDA_NO_POSTERIOR + " | " + CalendarioService.LLEGADA_EN_EL_PASADO;

	/** Mensaje del 409 cuando el alojamiento no está ACTIVA. */
	private static final String NO_ACTIVA = "El alojamiento no está disponible para nuevas reservas (estado PAUSADA).";

	/** Servicio de cotizaciones. */
	private final CotizacionService cotizacionService;

	/** Servicio de reservas. */
	private final ReservaService reservaService;

	/**
	 * Crea el controlador.
	 *
	 * @param cotizacionService servicio de cotizaciones
	 * @param reservaService    servicio de reservas
	 */
	public ReservaController(CotizacionService cotizacionService, ReservaService reservaService) {
		this.cotizacionService = cotizacionService;
		this.reservaService = reservaService;
	}

	/**
	 * Cotiza una estancia: noches, precio base, limpieza, servicio y total. La
	 * cotización sirve {@value CotizacionService#MINUTOS_VIGENCIA} minutos para
	 * reservar. Ruta: {@code POST /cotizaciones}.
	 *
	 * @param solicitud      alojamiento, fechas y huéspedes
	 * @param authentication usuario autenticado
	 * @return 201 con la cotización; 400 si los datos no son válidos; 404 si el
	 *         alojamiento no existe; 409 si no está ACTIVA o las fechas no están
	 *         libres
	 */
	@Operation(summary = "Cotizar una estancia", description = "Devuelve noches, precio base, limpieza, servicio y "
			+ "total con los porcentajes vigentes. La cotización sirve 30 minutos para reservar.")
	@ApiResponse(responseCode = "201", description = "Cotización con el desglose")
	@ApiResponse(responseCode = "400", description = CotizacionService.FALTA_ALOJAMIENTO + " | " + FECHAS_INVALIDAS
			+ " | " + CotizacionService.HUESPEDES_INVALIDOS + " | La cantidad de huéspedes (9) supera la capacidad "
			+ "del alojamiento (4).")
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
}
