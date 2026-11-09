package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Cotización: el precio calculado para una estancia antes de reservar,
 * mapeada a la tabla {@code cotizacion} de Neon.
 * <p>
 * Guarda cada componente del precio y los porcentajes vigentes al cotizar, así
 * la reserva cobra exactamente lo cotizado (CA-086). Solo sirve para reservar
 * mientras esté vigente y no se haya usado.
 * </p>
 */
@Entity
@Table(name = "cotizacion")
@Getter
@Setter
@NoArgsConstructor
public class Cotizacion {

	/** Identificador de la cotización. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idCotizacion;

	/** Día de llegada. */
	@Column(nullable = false)
	private LocalDate fechaEntrada;

	/** Día de salida (no se cuenta como noche). */
	@Column(nullable = false)
	private LocalDate fechaSalida;

	/** Cantidad de huéspedes. */
	@Column(nullable = false)
	private Integer huespedes;

	/** Cantidad de noches. */
	@Column(nullable = false)
	private Integer noches;

	/** Precio por noche del alojamiento al momento de cotizar. */
	@Column(nullable = false)
	private BigDecimal precioNoche;

	/** Precio por noche multiplicado por las noches. */
	@Column(nullable = false)
	private BigDecimal precioBase;

	/** Descuento aplicado al precio base. */
	@Column(nullable = false)
	private BigDecimal descuento;

	/** Porcentaje de limpieza que se aplicó. */
	@Column(nullable = false)
	private BigDecimal porcentajeLimpieza;

	/** Valor de la limpieza. */
	@Column(nullable = false)
	private BigDecimal valorLimpieza;

	/** Porcentaje de la tarifa de servicio que se aplicó. */
	@Column(nullable = false)
	private BigDecimal porcentajeServicio;

	/** Valor de la tarifa de servicio. */
	@Column(nullable = false)
	private BigDecimal valorServicio;

	/** Suma de los servicios adicionales elegidos. */
	@Column(nullable = false)
	private BigDecimal valorAdicionales;

	/** Valor total a pagar. */
	@Column(nullable = false)
	private BigDecimal total;

	/** Servicios adicionales elegidos en JSON; es opcional. */
	private String desglose;

	/** Usuario que pidió la cotización. */
	@Column(name = "id_usuario", nullable = false)
	private Long idUsuario;

	/**
	 * Clave foránea hacia {@link Usuario}; el valor se asigna con
	 * {@code idUsuario}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_usuario", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Usuario usuario;

	/** Alojamiento cotizado. */
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

	/**
	 * Reserva que se creó con esta cotización; {@code null} mientras no se use.
	 * En Neon es única: una cotización solo genera una reserva.
	 */
	@Column(name = "id_reserva", unique = true)
	private Long idReserva;

	/** Momento en que se cotizó. */
	@Column(nullable = false)
	private LocalDateTime fechaCreacion;

	/** Momento en que la cotización deja de servir para reservar. */
	@Column(nullable = false)
	private LocalDateTime fechaExpiracion;
}
