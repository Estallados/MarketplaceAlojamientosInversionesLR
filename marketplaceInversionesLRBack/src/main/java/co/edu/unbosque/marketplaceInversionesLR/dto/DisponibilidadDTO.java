package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.util.List;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Calendario de un alojamiento: los períodos que ofrece el anfitrión y los
 * rangos ya reservados (esquema {@code Calendario}).
 */
@Getter
@Setter
@NoArgsConstructor
public class DisponibilidadDTO {

	/** Alojamiento consultado. */
	private Long idAlojamiento;

	/** Estado de la publicación. */
	private EstadoAlojamiento estadoAlojamiento;

	/** Períodos ofrecidos, por fecha de inicio. */
	private List<PeriodoDisponibilidadDTO> periodos;

	/** Rangos reservados desde hoy, por fecha de entrada. */
	private List<RangoReservadoDTO> reservados;
}
