package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService.ResultadoPago;
import tools.jackson.databind.JsonNode;

/**
 * Cliente de la API de Stripe en modo de prueba (HU-27): crea y confirma
 * PaymentIntents y hace devoluciones (Refunds). Interpreta las respuestas de
 * Stripe y las convierte en un {@link ResultadoPago}. La lógica de reservas y
 * pagos está en {@link PagoService}.
 */
@Service
public class StripeService {

	/** Proveedor que queda registrado en los pagos hechos en Stripe. */
	public static final String PROVEEDOR = "STRIPE_TEST";

	/** Moneda de los cobros; Stripe la maneja con dos decimales (centavos). */
	public static final String MONEDA = "cop";

	/** Código de Stripe para un error al procesar la tarjeta. */
	public static final String PROCESSING_ERROR = "processing_error";

	/** Código de Stripe para una tarjeta que exige 3D Secure. */
	public static final String AUTENTICACION = "authentication_required";

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(StripeService.class);

	/** Dirección base de la API de Stripe. */
	private static final String URL_STRIPE = "https://api.stripe.com/v1/";

	/** Mensaje en español de cada código de rechazo de Stripe (decline_code o code). */
	private static final Map<String, String> MENSAJES_RECHAZO = Map.ofEntries(
			Map.entry("generic_decline", "La tarjeta fue rechazada por el banco."),
			Map.entry("card_declined", "La tarjeta fue rechazada por el banco."),
			Map.entry("insufficient_funds", "La tarjeta no tiene fondos suficientes."),
			Map.entry("lost_card", "La tarjeta fue reportada como perdida."),
			Map.entry("stolen_card", "La tarjeta fue reportada como robada."),
			Map.entry("expired_card", "La tarjeta está vencida."),
			Map.entry("incorrect_cvc", "El código de seguridad (CVC) no es correcto."),
			Map.entry("fraudulent", "El pago se bloqueó por sospecha de fraude."),
			Map.entry(PROCESSING_ERROR, "Ocurrió un error al procesar la tarjeta. Intenta de nuevo."),
			Map.entry(AUTENTICACION,
					"La tarjeta exige autenticación 3D Secure, que este pago no admite. Usa otra tarjeta."),
			Map.entry("resource_missing", "El método de pago no existe en Stripe."));

	/** Clave secreta de Stripe; vacía si no se configuró. */
	private final String claveSecreta;

	/** Cliente HTTP para llamar a Stripe. */
	private final ClienteHttp clienteHttp;

	/**
	 * Crea el cliente de Stripe.
	 *
	 * @param claveSecreta clave secreta ({@code STRIPE_SECRET_KEY})
	 * @param clienteHttp  cliente HTTP
	 */
	@Autowired
	public StripeService(@Value("${app.stripe.secret-key:${STRIPE_SECRET_KEY:}}") String claveSecreta,
			ClienteHttp clienteHttp) {
		this.claveSecreta = claveSecreta == null ? "" : claveSecreta.trim();
		this.clienteHttp = clienteHttp;
	}

	/**
	 * Indica si se configuró alguna clave secreta de Stripe.
	 *
	 * @return {@code true} si hay clave
	 */
	public boolean estaConfigurado() {
		return !claveSecreta.isEmpty();
	}

	/**
	 * Indica si la clave es de prueba ({@code sk_test_...}); las demás se
	 * rechazan (CA-160).
	 *
	 * @return {@code true} si es de prueba
	 */
	public boolean esClaveDePrueba() {
		return claveSecreta.startsWith("sk_test_");
	}

	/**
	 * Mensaje en español para un código de rechazo de Stripe.
	 *
	 * @param codigo código de Stripe (decline_code o code)
	 * @return mensaje para el usuario
	 */
	public static String mensajeRechazo(String codigo) {
		return MENSAJES_RECHAZO.getOrDefault(codigo, "La tarjeta fue rechazada.");
	}

	/**
	 * Crea y confirma el PaymentIntent de la reserva.
	 *
	 * @param valor      valor a cobrar
	 * @param metodoPago PaymentMethod de Stripe
	 * @param idReserva  reserva; se usa como clave de idempotencia
	 * @return resultado del cobro
	 */
	public ResultadoPago cobrar(BigDecimal valor, String metodoPago, Long idReserva) {
		MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
		formulario.add("amount", centavos(valor));
		formulario.add("currency", MONEDA);
		formulario.add("payment_method", metodoPago);
		formulario.add("confirm", "true");
		formulario.add("description", "Reserva " + idReserva + " - Marketplace Inversiones LR");
		formulario.add("metadata[id_reserva]", String.valueOf(idReserva));
		// Solo tarjetas sin redirección: el pago se confirma en esta misma petición
		formulario.add("automatic_payment_methods[enabled]", "true");
		formulario.add("automatic_payment_methods[allow_redirects]", "never");
		try {
			return enviar("payment_intents", "reserva-" + idReserva, formulario, this::resultadoCobro);
		} catch (RuntimeException e) {
			LOG.error("No se pudo comunicar con Stripe", e);
			return new ResultadoPago(EstadoPago.ERROR, null, PROVEEDOR, PagoService.FALLA_PASARELA);
		}
	}

	/**
	 * Devuelve el valor indicado del pago con un Refund. Si Stripe no hace la
	 * devolución, el resultado queda APROBADO con el motivo en el detalle.
	 *
	 * @param pago  pago a devolver
	 * @param valor valor a devolver
	 * @return resultado de la devolución
	 */
	public ResultadoPago devolver(Pago pago, BigDecimal valor) {
		MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
		formulario.add("payment_intent", pago.getReferencia());
		formulario.add("amount", centavos(valor));
		formulario.add("metadata[id_reserva]", String.valueOf(pago.getIdReserva()));
		try {
			return enviar("refunds", "reembolso-reserva-" + pago.getIdReserva(), formulario, (estado, cuerpo) -> {
				String status = cuerpo == null ? "" : cuerpo.path("status").asString();
				if (estado.is2xxSuccessful() && ("succeeded".equals(status) || "pending".equals(status))) {
					return new ResultadoPago(EstadoPago.REEMBOLSADO, cuerpo.path("id").asString(), PROVEEDOR,
							"Devolución de " + valor.toPlainString() + " hecha en Stripe.");
				}
				LOG.error("Stripe rechazó la devolución de la reserva {}: {}", pago.getIdReserva(), cuerpo);
				return new ResultadoPago(EstadoPago.APROBADO, null, PROVEEDOR, "La devolución de "
						+ valor.toPlainString() + " no se pudo hacer en Stripe: " + mensajeError(cuerpo));
			});
		} catch (RuntimeException e) {
			LOG.error("No se pudo comunicar con Stripe para la devolución de la reserva {}", pago.getIdReserva(), e);
			return new ResultadoPago(EstadoPago.APROBADO, null, PROVEEDOR,
					"La devolución de " + valor.toPlainString() + " está pendiente: no se pudo comunicar con Stripe.");
		}
	}

	/**
	 * Interpreta la respuesta de Stripe al crear y confirmar el PaymentIntent.
	 *
	 * @param estado estado HTTP de la respuesta
	 * @param cuerpo cuerpo JSON de la respuesta
	 * @return resultado del cobro
	 */
	private ResultadoPago resultadoCobro(HttpStatusCode estado, JsonNode cuerpo) {
		if (cuerpo == null) {
			return new ResultadoPago(EstadoPago.ERROR, null, PROVEEDOR, "Respuesta vacía de Stripe.");
		}
		if (estado.is2xxSuccessful()) {
			String status = cuerpo.path("status").asString();
			String id = cuerpo.path("id").asString(null);
			if ("succeeded".equals(status)) {
				return new ResultadoPago(EstadoPago.APROBADO, id, PROVEEDOR, "Pago de prueba aprobado por Stripe.");
			}
			if ("requires_action".equals(status)) {
				return new ResultadoPago(EstadoPago.RECHAZADO, id, PROVEEDOR, mensajeRechazo(AUTENTICACION));
			}
			return new ResultadoPago(EstadoPago.ERROR, id, PROVEEDOR, "Stripe dejó el pago en estado " + status + ".");
		}
		JsonNode error = cuerpo.path("error");
		String codigo = error.path("decline_code").asString(error.path("code").asString(""));
		String referencia = error.path("payment_intent").path("id").asString(null);
		if (estado.value() == 402) {
			return new ResultadoPago(PROCESSING_ERROR.equals(codigo) ? EstadoPago.ERROR : EstadoPago.RECHAZADO,
					referencia, PROVEEDOR, mensajeRechazo(codigo));
		}
		if (estado.value() == 400 && "invalid_request_error".equals(error.path("type").asString())) {
			// Por ejemplo, un PaymentMethod que no existe: es un problema del dato enviado,
			// no de la pasarela
			return new ResultadoPago(EstadoPago.RECHAZADO, referencia, PROVEEDOR, MENSAJES_RECHAZO.getOrDefault(codigo,
					"Stripe no aceptó el método de pago: " + mensajeError(cuerpo)));
		}
		LOG.error("Respuesta inesperada de Stripe ({}): {}", estado.value(), cuerpo);
		return new ResultadoPago(EstadoPago.ERROR, null, PROVEEDOR,
				estado.value() == 401 ? "Stripe rechazó la clave configurada."
						: "La pasarela de pago no respondió correctamente.");
	}

	/**
	 * Envía un formulario a la API de Stripe con la clave secreta y una clave de
	 * idempotencia; si la petición se repite, Stripe devuelve la primera
	 * respuesta.
	 *
	 * @param recurso           recurso de Stripe, por ejemplo
	 *                          {@code payment_intents}
	 * @param claveIdempotencia clave de idempotencia de la operación
	 * @param formulario        campos del formulario
	 * @param interprete        función que convierte la respuesta en el
	 *                          resultado
	 * @return resultado de la operación
	 */
	private ResultadoPago enviar(String recurso, String claveIdempotencia, MultiValueMap<String, String> formulario,
			BiFunction<HttpStatusCode, JsonNode, ResultadoPago> interprete) {
		Map<String, String> encabezados = Map.of("Authorization", "Bearer " + claveSecreta,
				// Si la petición se repite (reintento, doble clic), Stripe devuelve la primera
				// respuesta
				"Idempotency-Key", "marketplace-" + claveIdempotencia);
		return clienteHttp.postFormulario(URL_STRIPE + recurso, encabezados, formulario, interprete);
	}

	/**
	 * Mensaje de error que trae la respuesta de Stripe.
	 *
	 * @param cuerpo cuerpo JSON de la respuesta; puede ser {@code null}
	 * @return mensaje de error
	 */
	private static String mensajeError(JsonNode cuerpo) {
		return cuerpo == null ? "sin respuesta" : cuerpo.path("error").path("message").asString("error desconocido");
	}

	/**
	 * Convierte el valor a centavos, que es como Stripe recibe los montos (en la
	 * unidad mínima de la moneda).
	 *
	 * @param valor valor en pesos
	 * @return valor en centavos como texto
	 */
	private static String centavos(BigDecimal valor) {
		return valor.movePointRight(2).toBigInteger().toString();
	}
}
