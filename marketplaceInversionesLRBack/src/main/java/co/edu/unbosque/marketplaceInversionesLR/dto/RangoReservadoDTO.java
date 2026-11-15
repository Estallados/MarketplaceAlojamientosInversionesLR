package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Fechas ocupadas por una reserva, sin datos del huésped (esquema
 * {@code RangoReservado}). La salida no es noche ocupada: [fechaEntrada,
 * fechaSalida).
 */
@Getter
@Setter
public class RangoReservadoDTO {

	/** Día de llegada. */
	private LocalDate fechaEntrada;

	/** Día de salida. */
	private LocalDate fechaSalida;

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public RangoReservadoDTO() {
		// Los valores se asignan con los setters.
	}
}
