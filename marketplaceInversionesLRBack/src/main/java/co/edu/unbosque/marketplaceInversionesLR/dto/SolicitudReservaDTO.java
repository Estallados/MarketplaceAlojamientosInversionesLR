package co.edu.unbosque.marketplaceInversionesLR.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que envía el huésped para reservar: la cotización vigente y el método
 * de pago de prueba.
 */
@Getter
@Setter
@NoArgsConstructor
public class SolicitudReservaDTO {

	/** Cotización vigente a reservar (CA-070). */
	@Schema(description = "Cotización vigente del huésped (POST /cotizaciones)", example = "27",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private Long idCotizacion;

	/**
	 * Método de pago de prueba ({@code pm_...}). Si se omite, se usa
	 * {@code pm_card_visa}.
	 */
	@Schema(description = "Método de pago de prueba. Opcional: por defecto pm_card_visa (aprobado). "
			+ "pm_card_chargeDeclined simula un rechazo (402) y pm_card_error una falla de la pasarela (502).",
			example = "pm_card_visa", pattern = "^pm_\\w{1,200}$")
	private String metodoPago;
}
