package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Servicio adicional que ofrece un alojamiento (desayuno, traslado, etc.), tal
 * como entra y sale por la API.
 */
@Getter
@Setter
@NoArgsConstructor
public class ServicioAdicionalDTO {

	/** Identificador del servicio. */
	private Long idServicio;

	/** Nombre del servicio. */
	private String nombre;

	/** Costo por reserva, en pesos colombianos. */
	private BigDecimal costo;

	/** Alojamiento que ofrece el servicio. */
	private Long idAlojamiento;
}
