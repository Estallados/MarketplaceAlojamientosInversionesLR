package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

	/** El repositorio devuelve el pago que recibe. */
	private void guardar() {
		when(pagoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
	}
}
