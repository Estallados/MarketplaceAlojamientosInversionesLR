package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConfiguracionPagoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;

/**
 * Pruebas unitarias del pago de prueba en modo SIMULADO (CA-160, CA-161,
 * CA-162).
 */
@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

	@Mock
	private PagoRepository pagoRepo;

	private PagoService servicio;

	/** Reserva en PENDIENTE_PAGO. */
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		servicio = new PagoService(pagoRepo);
		reserva = new Reserva();
		reserva.setIdReserva(15L);
		reserva.setTotal(new BigDecimal("690000.00"));
	}

	@Test
	@DisplayName("CA-001 La devolución deja el pago aprobado REEMBOLSADO con valor y referencia")
	void reembolsa_CA001() {
		Pago aprobado = pagoAprobado();
		when(pagoRepo.findTopByIdReservaOrderByFechaDescIdPagoDesc(15L)).thenReturn(Optional.of(aprobado));
		guardar();

		Optional<Pago> resultado = servicio.reembolsar(15L, new BigDecimal("345000.00"));

		assertThat(resultado).containsSame(aprobado);
		assertThat(aprobado.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
		assertThat(aprobado.getValorReembolsado()).isEqualByComparingTo("345000.00");
		assertThat(aprobado.getReferenciaReembolso()).startsWith(PagoService.PREFIJO_DEVOLUCION);
		assertThat(aprobado.getDetalle()).isEqualTo("Devolución simulada de 345000.00.");
		// Lo que no cambia el reembolso queda igual (Neon lo protege)
		assertThat(aprobado.getValor()).isEqualByComparingTo("690000.00");
		assertThat(aprobado.getReferencia()).isEqualTo("sim_pi_1");
	}

	@Test
	@DisplayName("CA-009 Sin valor a devolver o sin pago aprobado no se toca el pago")
	void sinDevolucion_CA009() {
		Pago rechazado = pagoAprobado();
		rechazado.setEstado(EstadoPago.RECHAZADO);
		when(pagoRepo.findTopByIdReservaOrderByFechaDescIdPagoDesc(15L)).thenReturn(Optional.of(rechazado));

		assertThat(servicio.reembolsar(15L, BigDecimal.ZERO)).isEmpty();
		assertThat(servicio.reembolsar(15L, null)).isEmpty();
		assertThat(servicio.reembolsar(15L, new BigDecimal("100"))).isEmpty();
		assertThat(rechazado.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
		verify(pagoRepo, never()).save(any());
	}

	@Test
	@DisplayName("Nunca se devuelve más de lo que se pagó")
	void topeDeLoPagado() {
		Pago aprobado = pagoAprobado();
		when(pagoRepo.findTopByIdReservaOrderByFechaDescIdPagoDesc(15L)).thenReturn(Optional.of(aprobado));
		guardar();

		servicio.reembolsar(15L, new BigDecimal("999999.00"));

		assertThat(aprobado.getValorReembolsado()).isEqualByComparingTo("690000.00");
	}

	/**
	 * Pago APROBADO de 690.000 de la reserva 15.
	 *
	 * @return pago
	 */
	private static Pago pagoAprobado() {
		Pago pago = new Pago();
		pago.setIdPago(30L);
		pago.setIdReserva(15L);
		pago.setEstado(EstadoPago.APROBADO);
		pago.setValor(new BigDecimal("690000.00"));
		pago.setReferencia("sim_pi_1");
		return pago;
	}

	@Test
	@DisplayName("CA-160 CA-161 Sin método se usa pm_card_visa y el pago queda APROBADO por el total de la reserva")
	void apruebaPorDefecto_CA160_CA161() {
		guardar();

		Pago pago = servicio.procesar(reserva, null);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(pago.getMetodo()).isEqualTo(PagoService.METODO_POR_DEFECTO);
		assertThat(pago.getProveedor()).isEqualTo(PagoService.PROVEEDOR_SIMULADO);
		assertThat(pago.getValor()).isEqualByComparingTo("690000.00");
		assertThat(pago.getIdReserva()).isEqualTo(15L);
		assertThat(pago.getReferencia()).startsWith("sim_pi_");
		assertThat(pago.getDetalle()).isEqualTo(PagoService.APROBADO);
		assertThat(pago.getFecha()).isNotNull();
	}

	@Test
	@DisplayName("CA-161 Cualquier otro método pm_ se trata como una tarjeta válida")
	void otroMetodoSeAprueba_CA161() {
		guardar();

		Pago pago = servicio.procesar(reserva, " pm_card_mastercard ");

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(pago.getMetodo()).isEqualTo("pm_card_mastercard");
	}

	@Test
	@DisplayName("CA-162 Los métodos de rechazo de Stripe dejan el pago RECHAZADO con la causa")
	void rechazo_CA162() {
		guardar();

		Pago pago = servicio.procesar(reserva, "pm_card_chargeDeclined");

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(pago.getDetalle()).isEqualTo("La tarjeta fue rechazada por el banco.");
		assertThat(pago.getReferencia()).startsWith("sim_pi_");
	}

	@Test
	@DisplayName("CA-162 Una falla de la pasarela deja el pago en ERROR sin referencia")
	void falla_CA162() {
		guardar();

		Pago pago = servicio.procesar(reserva, "pm_card_error");

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.ERROR);
		assertThat(pago.getDetalle()).isEqualTo(PagoService.FALLA_PASARELA);
		assertThat(pago.getReferencia()).isNull();
	}

	@Test
	@DisplayName("Solo se aceptan métodos con la forma pm_...; vacío usa el de por defecto")
	void metodoValido() {
		assertThat(PagoService.metodoValido(null)).isTrue();
		assertThat(PagoService.metodoValido("  ")).isTrue();
		assertThat(PagoService.metodoValido("pm_card_visa")).isTrue();
		assertThat(PagoService.metodoValido("4242424242424242")).isFalse();
		assertThat(PagoService.metodoValido("pm_")).isFalse();
		assertThat(PagoService.metodoValido("pm_card visa")).isFalse();
	}

	@Test
	@DisplayName("CA-160 Con una clave de Stripe que no es de prueba no se llama a Stripe ni se cobra")
	void claveRealNoCobra_CA160() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
		PagoService real = new PagoService(pagoRepo, "sk_live_123", "pk_live_456", builder.build());
		guardar();

		Pago pago = real.procesar(reserva, null);

		stripe.verify();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.ERROR);
		assertThat(pago.getDetalle()).isEqualTo(PagoService.SOLO_PRUEBA);
		assertThat(real.modo()).isEqualTo(PagoService.PROVEEDOR_SIMULADO);
		assertThat(real.configuracion().getClavePublica()).isNull();
	}

	@Test
	@DisplayName("CA-160 La configuración indica el modo de prueba y solo entrega claves de prueba")
	void configuracionDePrueba_CA160() {
		ConfiguracionPagoDTO stripeTest = new PagoService(pagoRepo, "sk_test_123", "pk_test_456").configuracion();
		ConfiguracionPagoDTO simulado = new PagoService(pagoRepo, "", "pk_test_456").configuracion();

		assertThat(stripeTest.getModo()).isEqualTo(PagoService.PROVEEDOR_STRIPE);
		assertThat(stripeTest.getClavePublica()).isEqualTo("pk_test_456");
		assertThat(stripeTest.getMoneda()).isEqualTo("cop");
		assertThat(stripeTest.getMetodosPrueba()).anySatisfy(m -> {
			assertThat(m.getMetodo()).isEqualTo(PagoService.METODO_POR_DEFECTO);
			assertThat(m.getResultado()).isEqualTo("APROBADO");
		});
		assertThat(simulado.getModo()).isEqualTo(PagoService.PROVEEDOR_SIMULADO);
		assertThat(simulado.getClavePublica()).isNull();
		assertThat(new PagoService(pagoRepo, "sk_test_1", "pk_live_real").configuracion().getClavePublica()).isNull();
	}

	@Test
	@DisplayName("CA-161 En Stripe de prueba se crea y confirma el PaymentIntent y el pago queda APROBADO con su "
			+ "referencia")
	void apruebaEnStripe_CA161() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
		PagoService pagos = new PagoService(pagoRepo, "sk_test_123", "pk_test_456", builder.build());
		stripe.expect(requestTo(PAYMENT_INTENTS)).andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer sk_test_123"))
				.andExpect(header("Idempotency-Key", "marketplace-reserva-15"))
				.andExpect(content().formDataContains(Map.of("amount", "69000000", "currency", "cop", "payment_method",
						"pm_card_visa", "confirm", "true", "metadata[id_reserva]", "15")))
				.andRespond(withSuccess("{\"id\":\"pi_3Abc\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
		guardar();

		Pago pago = pagos.procesar(reserva, null);

		stripe.verify();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(pago.getReferencia()).isEqualTo("pi_3Abc");
		assertThat(pago.getProveedor()).isEqualTo(PagoService.PROVEEDOR_STRIPE);
		assertThat(pago.getIdReserva()).isEqualTo(15L);
		assertThat(pago.getValor()).isEqualByComparingTo("690000.00");
	}

	@Test
	@DisplayName("CA-162 Un rechazo de la tarjeta en Stripe deja el pago RECHAZADO con el motivo en español")
	void rechazoEnStripe_CA162() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
		PagoService pagos = new PagoService(pagoRepo, "sk_test_123", "", builder.build());
		responderError(stripe, HttpStatus.PAYMENT_REQUIRED, "{\"error\":{\"type\":\"card_error\",\"code\":"
				+ "\"card_declined\",\"decline_code\":\"insufficient_funds\",\"message\":\"Your card has insufficient "
				+ "funds.\",\"payment_intent\":{\"id\":\"pi_rechazado\"}}}");
		guardar();

		Pago pago = pagos.procesar(reserva, "pm_card_chargeDeclinedInsufficientFunds");

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(pago.getReferencia()).isEqualTo("pi_rechazado");
		assertThat(pago.getDetalle()).isEqualTo("La tarjeta no tiene fondos suficientes.");
	}

	@Test
	@DisplayName("CA-162 3D Secure, un método inexistente y las fallas de Stripe no dejan el pago aprobado")
	void otrasRespuestasDeStripe_CA162() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
		PagoService pagos = new PagoService(pagoRepo, "sk_test_123", "", builder.build());

		stripe.expect(requestTo(PAYMENT_INTENTS)).andRespond(
				withSuccess("{\"id\":\"pi_3ds\",\"status\":\"requires_action\"}", MediaType.APPLICATION_JSON));
		PagoService.ResultadoPago tresDs = pagos.cobrar(BigDecimal.TEN, "pm_card_authenticationRequired", 1L);
		assertThat(tresDs.estado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(tresDs.detalle()).contains("3D Secure");

		stripe.reset();
		responderError(stripe, HttpStatus.BAD_REQUEST, "{\"error\":{\"type\":\"invalid_request_error\",\"code\":"
				+ "\"resource_missing\",\"message\":\"No such PaymentMethod\"}}");
		PagoService.ResultadoPago inexistente = pagos.cobrar(BigDecimal.TEN, "pm_inventado", 2L);
		assertThat(inexistente.estado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(inexistente.detalle()).isEqualTo("El método de pago no existe en Stripe.");

		stripe.reset();
		responderError(stripe, HttpStatus.UNAUTHORIZED, "{\"error\":{\"type\":\"invalid_request_error\"}}");
		assertThat(pagos.cobrar(BigDecimal.TEN, "pm_card_visa", 3L).estado()).isEqualTo(EstadoPago.ERROR);

		stripe.reset();
		responderError(stripe, HttpStatus.PAYMENT_REQUIRED, "{\"error\":{\"type\":\"card_error\",\"decline_code\":"
				+ "\"processing_error\"}}");
		assertThat(pagos.cobrar(BigDecimal.TEN, "pm_card_visa", 4L).estado()).isEqualTo(EstadoPago.ERROR);

		stripe.reset();
		responderError(stripe, HttpStatus.INTERNAL_SERVER_ERROR, "{\"error\":{\"type\":\"api_error\"}}");
		assertThat(pagos.cobrar(BigDecimal.TEN, "pm_card_visa", 5L).estado()).isEqualTo(EstadoPago.ERROR);
	}

	@Test
	@DisplayName("Un pago hecho en Stripe se devuelve con un Refund; si Stripe no lo hace, queda APROBADO con el motivo")
	void devolucionEnStripe() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer stripe = MockRestServiceServer.bindTo(builder).build();
		PagoService pagos = new PagoService(pagoRepo, "sk_test_123", "", builder.build());
		Pago aprobado = pagoAprobado();
		aprobado.setProveedor(PagoService.PROVEEDOR_STRIPE);
		aprobado.setReferencia("pi_pagado");
		when(pagoRepo.findTopByIdReservaOrderByFechaDescIdPagoDesc(15L)).thenReturn(Optional.of(aprobado));
		guardar();
		stripe.expect(requestTo(REFUNDS)).andExpect(header("Idempotency-Key", "marketplace-reembolso-reserva-15"))
				.andExpect(content().formDataContains(Map.of("payment_intent", "pi_pagado", "amount", "5000000")))
				.andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));

		assertThat(pagos.reembolsar(15L, new BigDecimal("50000"))).containsSame(aprobado);
		assertThat(aprobado.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
		assertThat(aprobado.getReferenciaReembolso()).isEqualTo("re_1");

		aprobado.setEstado(EstadoPago.APROBADO);
		aprobado.setReferenciaReembolso(null);
		stripe.reset();
		stripe.expect(requestTo(REFUNDS)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Charge already refunded\"}}"));

		assertThat(pagos.reembolsar(15L, BigDecimal.TEN)).isEmpty();
		assertThat(aprobado.getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(aprobado.getDetalle()).contains("Charge already refunded");
		assertThat(aprobado.getReferenciaReembolso()).isNull();
	}

	@Test
	@DisplayName("CA-162 El simulador responde como Stripe a sus tarjetas de prueba")
	void simuladorComoStripe_CA162() {
		assertThat(servicio.cobrar(BigDecimal.TEN, "pm_card_chargeDeclinedExpiredCard", 1L).detalle())
				.isEqualTo("La tarjeta está vencida.");
		assertThat(servicio.cobrar(BigDecimal.TEN, "pm_card_chargeDeclinedProcessingError", 1L).estado())
				.isEqualTo(EstadoPago.ERROR);
		assertThat(servicio.cobrar(BigDecimal.TEN, "pm_card_authenticationRequired", 1L).estado())
				.isEqualTo(EstadoPago.RECHAZADO);
		assertThat(servicio.cobrar(BigDecimal.TEN, "pm_1PxYzAbC123", 1L).estado()).isEqualTo(EstadoPago.APROBADO);
	}

	/** Dirección de los PaymentIntents de Stripe. */
	private static final String PAYMENT_INTENTS = "https://api.stripe.com/v1/payment_intents";

	/** Dirección de las devoluciones de Stripe. */
	private static final String REFUNDS = "https://api.stripe.com/v1/refunds";

	/**
	 * Hace que el servidor falso responda como Stripe con un error.
	 *
	 * @param stripe servidor falso
	 * @param estado estado HTTP
	 * @param cuerpo cuerpo JSON del error
	 */
	private static void responderError(MockRestServiceServer stripe, HttpStatus estado, String cuerpo) {
		stripe.expect(requestTo(PAYMENT_INTENTS))
				.andRespond(withStatus(estado).contentType(MediaType.APPLICATION_JSON).body(cuerpo));
	}

	/** El repositorio devuelve el pago que recibe. */
	private void guardar() {
		when(pagoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
	}
}
