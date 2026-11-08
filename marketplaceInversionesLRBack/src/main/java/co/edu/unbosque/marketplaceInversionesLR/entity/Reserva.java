package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reserva de un alojamiento, mapeada a la tabla {@code reserva} de Neon. Las
 * fechas son [fechaEntrada, fechaSalida): el día de salida queda libre.
 * <p>
 * Por ahora la disponibilidad (HU-20) solo la lee, para no dejar por fuera
 * una reserva al modificar un período (CA-118). La creación, el pago y la
 * cancelación llegan con sus historias.
 * </p>
 */
@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

	/** Identificador de la reserva. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idReserva;

	/** Día de llegada. */
	@Column(nullable = false)
	private LocalDate fechaEntrada;

	/** Día de salida (no se cuenta como noche). */
	@Column(nullable = false)
	private LocalDate fechaSalida;

	/** Noches de la estancia. */
	@Column(nullable = false)
	private Integer noches;

	/** Cantidad de huéspedes. */
	@Column(nullable = false)
	private Integer huespedes;

	/** Estado de la reserva. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoReserva estado;

	/** Valor total cobrado. */
	@Column(nullable = false)
	private BigDecimal total;

	/** Desglose del cobro en JSON; es opcional. */
	private String desglose;

	/** Huésped que reservó. */
	@Column(name = "id_huesped", nullable = false)
	private Long idHuesped;

	/**
	 * Clave foránea hacia {@link Usuario}; el valor se asigna con
	 * {@code idHuesped}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_huesped", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Usuario huesped;

	/** Alojamiento reservado. */
	@Column(name = "id_alojamiento", nullable = false)
	private Long idAlojamiento;

	/**
	 * Clave foránea hacia {@link Alojamiento}; el valor se asigna con
	 * {@code idAlojamiento}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_alojamiento", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Alojamiento alojamiento;

	/** Cotización de la que salió la reserva. */
	@Column(name = "id_cotizacion", nullable = false)
	private Long idCotizacion;

	/** Momento en que se creó la reserva. */
	@Column(nullable = false)
	private LocalDateTime fechaCreacion;
}
