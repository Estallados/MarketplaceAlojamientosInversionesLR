package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;

/**
 * Pagos de prueba de las reservas: nunca se hacen cobros reales (CA-160).
 * <p>
 * Por ahora funciona en modo SIMULADO: un simulador local responde como Stripe
 * a sus métodos de pago de prueba ({@code pm_card_visa} aprueba,
 * {@code pm_card_chargeDeclined} rechaza, {@code pm_card_error} falla). El
 * modo STRIPE_TEST, la configuración para el frontend y las devoluciones
 * llegan con HU-27.
 * </p>
 * <p>
 * En Neon una reserva solo pasa a CONFIRMADA si tiene un pago APROBADO, y el
 * valor del pago debe ser el total de la reserva.
 * </p>
 */
@Service
public class PagoService {

	/** Método de pago que se usa si el huésped no envía ninguno. */
	public static final String METODO_POR_DEFECTO = "pm_card_visa";

	/** Proveedor que se registra en los pagos del simulador. */
	public static final String PROVEEDOR_SIMULADO = "SIMULADO";

	/** Mensaje cuando el método de pago no tiene la forma {@code pm_...}. */
	public static final String METODO_INVALIDO = "El método de pago debe ser un PaymentMethod de Stripe (pm_...), "
			+ "por ejemplo pm_card_visa.";

	/** Detalle de un pago aprobado por el simulador. */
	public static final String APROBADO = "Pago de prueba aprobado.";

	/** Detalle cuando la pasarela no responde. */
	public static final String FALLA_PASARELA = "No se pudo comunicar con la pasarela de pago.";

	/** Forma válida de un PaymentMethod de Stripe ({@code pm_...}). */
	private static final Pattern PATRON_METODO = Pattern.compile("^pm_\\w{1,200}$");

	/**
	 * Método de pago de prueba y el resultado que da.
	 *
	 * @param metodo  id del método, por ejemplo {@code pm_card_visa}
	 * @param estado  estado en que queda el pago
	 * @param detalle mensaje para el huésped
	 */
	private record Prueba(String metodo, EstadoPago estado, String detalle) {
	}

	/** Métodos de prueba de Stripe y su resultado; cualquier otro {@code pm_} se aprueba. */
	private static final List<Prueba> PRUEBAS = List.of(
			new Prueba("pm_card_chargeDeclined", EstadoPago.RECHAZADO, "La tarjeta fue rechazada por el banco."),
			new Prueba("pm_card_chargeDeclinedInsufficientFunds", EstadoPago.RECHAZADO,
					"La tarjeta no tiene fondos suficientes."),
			new Prueba("pm_card_chargeDeclinedLostCard", EstadoPago.RECHAZADO,
					"La tarjeta fue reportada como perdida."),
			new Prueba("pm_card_chargeDeclinedStolenCard", EstadoPago.RECHAZADO,
					"La tarjeta fue reportada como robada."),
			new Prueba("pm_card_chargeDeclinedExpiredCard", EstadoPago.RECHAZADO, "La tarjeta está vencida."),
			new Prueba("pm_card_chargeDeclinedIncorrectCvc", EstadoPago.RECHAZADO,
					"El código de seguridad (CVC) no es correcto."),
			new Prueba("pm_card_chargeDeclinedFraudulent", EstadoPago.RECHAZADO,
					"El pago se bloqueó por sospecha de fraude."),
			new Prueba("pm_card_chargeDeclinedProcessingError", EstadoPago.ERROR,
					"Ocurrió un error al procesar la tarjeta. Intenta de nuevo."),
			new Prueba("pm_card_error", EstadoPago.ERROR, FALLA_PASARELA));

	/** Repositorio de pagos. */
	private final PagoRepository pagoRepo;

	/**
	 * Crea el servicio.
	 *
	 * @param pagoRepo repositorio de pagos
	 */
	public PagoService(PagoRepository pagoRepo) {
		this.pagoRepo = pagoRepo;
	}

	/**
	 * Indica si el método de pago tiene la forma de un PaymentMethod de Stripe
	 * ({@code pm_...}). Vacío también sirve: se usa {@link #METODO_POR_DEFECTO}.
	 *
	 * @param metodoPago método enviado por el frontend
	 * @return {@code true} si se puede usar
	 */
	public static boolean metodoValido(String metodoPago) {
		return metodoPago == null || metodoPago.isBlank() || PATRON_METODO.matcher(metodoPago.trim()).matches();
	}

	/**
	 * Cobra el total de la reserva en el simulador y guarda el {@link Pago} con
	 * el resultado. Se llama dentro de la transacción de la reserva.
	 *
	 * @param reserva    reserva en PENDIENTE_PAGO, ya guardada
	 * @param metodoPago método de prueba; vacío usa {@link #METODO_POR_DEFECTO}
	 * @return el pago guardado (APROBADO, RECHAZADO o ERROR)
	 */
	public Pago procesar(Reserva reserva, String metodoPago) {
		String metodo = metodoPago == null || metodoPago.isBlank() ? METODO_POR_DEFECTO : metodoPago.trim();
		Prueba prueba = PRUEBAS.stream().filter(p -> p.metodo().equalsIgnoreCase(metodo)).findFirst()
				.orElse(new Prueba(metodo, EstadoPago.APROBADO, APROBADO));

		Pago pago = new Pago();
		pago.setIdReserva(reserva.getIdReserva());
		pago.setValor(reserva.getTotal());
		pago.setMetodo(metodo);
		pago.setEstado(prueba.estado());
		pago.setReferencia(prueba.estado() == EstadoPago.ERROR ? null : "sim_pi_" + UUID.randomUUID());
		pago.setProveedor(PROVEEDOR_SIMULADO);
		pago.setDetalle(LimitesTexto.recortar(prueba.detalle(), LimitesTexto.CORTO));
		pago.setFecha(LocalDateTime.now());
		return pagoRepo.save(pago);
	}

	/**
	 * Último pago de una reserva.
	 *
	 * @param idReserva reserva
	 * @return el pago más reciente, si lo hay
	 */
	public Optional<Pago> ultimo(Long idReserva) {
		return pagoRepo.findTopByIdReservaOrderByFechaDescIdPagoDesc(idReserva);
	}
}
