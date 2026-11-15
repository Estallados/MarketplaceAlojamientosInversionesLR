package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.util.List;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import lombok.Getter;
import lombok.Setter;

/**
 * Calendario de un alojamiento: los períodos que ofrece el anfitrión y los
 * rangos ya reservados (esquema {@code Calendario}).
 */
@Getter
@Setter
public class DisponibilidadDTO {

	/** Alojamiento consultado. */
	private Long idAlojamiento;

	/** Estado de la publicación. */
	private EstadoAlojamiento estadoAlojamiento;

	/** Períodos ofrecidos, por fecha de inicio. */
	private List<PeriodoDisponibilidadDTO> periodos;

	/** Rangos reservados desde hoy, por fecha de entrada. */
	private List<RangoReservadoDTO> reservados;

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public DisponibilidadDTO() {
		// Los valores se asignan con los setters.
	}
}
