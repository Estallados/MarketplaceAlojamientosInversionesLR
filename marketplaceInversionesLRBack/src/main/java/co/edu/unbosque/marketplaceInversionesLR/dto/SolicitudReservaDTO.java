package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que envía el huésped para reservar: la cotización vigente. El método
 * de pago llega con la historia de pagos (HU-27).
 */
@Getter
@Setter
@NoArgsConstructor
public class SolicitudReservaDTO {

	/** Cotización vigente a reservar (CA-070). */
	private Long idCotizacion;
}
