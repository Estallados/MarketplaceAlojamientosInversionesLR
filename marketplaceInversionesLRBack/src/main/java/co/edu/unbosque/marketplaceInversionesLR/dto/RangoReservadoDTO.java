package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fechas ocupadas por una reserva, sin datos del huésped (esquema
 * {@code RangoReservado}). La salida no es noche ocupada: [fechaEntrada,
 * fechaSalida).
 */
@Getter
@Setter
@NoArgsConstructor
public class RangoReservadoDTO {

	/** Día de llegada. */
	private LocalDate fechaEntrada;

	/** Día de salida. */
	private LocalDate fechaSalida;
}
