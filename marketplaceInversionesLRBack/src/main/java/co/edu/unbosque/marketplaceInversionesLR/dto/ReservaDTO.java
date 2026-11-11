package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reserva tal como sale por la API, con el desglose congelado, el último pago
 * y los datos que muestran las pantallas de reservas (HU-25): el alojamiento,
 * el huésped, el anfitrión, la política, si ya tiene reseña y la cancelación.
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

	/** Último pago de la reserva; {@code null} si no tiene. */
	private PagoDTO pago;

	/**
	 * Está CONFIRMADA y la fecha de salida ya llegó; habilita la reseña
	 * (CA-016).
	 */
	private Boolean completada;

	/** URL de la imagen principal del alojamiento; {@code null} si no tiene. */
	private String imagenAlojamiento;

	/** Ciudad del alojamiento. */
	private String ciudad;

	/** Dirección del alojamiento. */
	private String ubicacion;

	/** Tipo de alojamiento, por ejemplo Apartamento. */
	private String tipoAlojamiento;

	/** Capacidad del alojamiento. */
	private Integer capacidadAlojamiento;

	/** Política de cancelación del alojamiento. */
	private String nombrePolitica;

	/** Nombre del huésped que reservó. */
	private String nombreHuesped;

	/** Anfitrión del alojamiento. */
	private Long idAnfitrion;

	/** Nombre del anfitrión. */
	private String nombreAnfitrion;

	/** Indica si el huésped ya reseñó esta reserva. */
	private Boolean tieneResena;

	/** Datos de la cancelación; {@code null} si el huésped no la canceló. */
	private CancelacionDTO cancelacion;
}
