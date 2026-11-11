package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo que el frontend necesita para cobrar (HU-27): si los pagos van a Stripe
 * (en modo de prueba) o al simulador, la clave publicable para Stripe
 * Elements, la moneda y las tarjetas de prueba con su resultado.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionPagoDTO {

	/** STRIPE_TEST o SIMULADO. */
	private String modo;

	/** Clave publicable de Stripe (pk_test_...); null en modo SIMULADO. */
	private String clavePublica;

	/** Moneda en que se cobra (COP). */
	private String moneda;

	/** Tarjetas de prueba disponibles y qué resultado da cada una. */
	private List<MetodoPagoPruebaDTO> metodosPrueba;
}
