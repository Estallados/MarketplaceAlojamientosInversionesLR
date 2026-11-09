package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reserva tal como sale por la API. El historial de reservas (HU-25) le
 * agrega los datos que muestran sus pantallas.
 */
@Getter
@Setter
@NoArgsConstructor
public class ReservaDTO {

	/** Identificador de la reserva. */
	private Long idReserva;

	/** Día de llegada. */
	private LocalDate fechaEntrada;

	/** Día de salida (no se cuenta como noche). */
	private LocalDate fechaSalida;

	/** Noches de la estancia. */
	private Integer noches;

	/** Cantidad de huéspedes. */
	private Integer huespedes;

	/** Estado de la reserva. */
	private EstadoReserva estado;

	/** Valor total; igual al total cotizado (CA-086). */
	private BigDecimal total;

	/** Desglose de la cotización congelado, en JSON. */
	private String desglose;

	/** Huésped que reservó. */
	private Long idHuesped;

	/** Alojamiento reservado. */
	private Long idAlojamiento;

	/** Título del alojamiento reservado. */
	private String tituloAlojamiento;

	/** Cotización de la que salió el precio. */
	private Long idCotizacion;

	/** Momento en que se creó la reserva. */
	private LocalDateTime fechaCreacion;
}
