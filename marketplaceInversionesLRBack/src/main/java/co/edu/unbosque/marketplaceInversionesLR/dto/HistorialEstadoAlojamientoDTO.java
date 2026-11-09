package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cambio de estado de un alojamiento: de qué estado a cuál, por qué, quién
 * lo hizo y cuándo.
 */
@Getter
@Setter
@NoArgsConstructor
public class HistorialEstadoAlojamientoDTO {

	/** Identificador del registro. */
	private Long idHistorial;

	/** Alojamiento que cambió de estado. */
	private Long idAlojamiento;

	/** Estado antes del cambio; {@code null} al crearlo. */
	private EstadoAlojamiento estadoAnterior;

	/** Estado después del cambio. */
	private EstadoAlojamiento estadoNuevo;

	/** Motivo del cambio. */
	private String motivo;

	/** Usuario que hizo el cambio. */
	private Long idUsuario;

	/** Momento del cambio. */
	private LocalDateTime fecha;
}
