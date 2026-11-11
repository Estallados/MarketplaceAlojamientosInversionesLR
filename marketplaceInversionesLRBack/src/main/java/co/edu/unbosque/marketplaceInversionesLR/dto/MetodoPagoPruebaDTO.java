package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Método de pago de prueba de Stripe y el resultado que da (HU-27).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetodoPagoPruebaDTO {

	/** Id del método, por ejemplo {@code pm_card_visa}. */
	private String metodo;

	/** Descripción para el usuario. */
	private String descripcion;

	/** Resultado: APROBADO, RECHAZADO o ERROR. */
	private String resultado;
}
