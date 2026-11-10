package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.math.BigDecimal;
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
 * Registro de la cancelación de una reserva (HU-01, CA-004), mapeado a la tabla
 * {@code cancelacion} de Neon: quién canceló, cuándo, el motivo, la política
 * aplicada, la devolución y el estado final de la reserva. Una cancelación por
 * reserva; en Neon no se modifica ni se borra.
 */
@Entity
@Table(name = "cancelacion")
@Getter
@Setter
@NoArgsConstructor
public class Cancelacion {

	/** Identificador de la cancelación. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idCancelacion;

	/** Motivo que dio el huésped. */
	@Column(nullable = false, length = 500)
	private String motivo;

	/** Nombre de la política aplicada, por ejemplo MODERADA. */
	@Column(nullable = false, length = 50)
	private String politica;

	/** Porcentaje del total que se devuelve (0 a 100). */
	@Column(nullable = false)
	private Integer porcentajeReembolso;

	/** Valor devuelto: el total por el porcentaje, redondeado a 2 decimales. */
	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal valorReembolso;

	/** Estado de la cancelación. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoCancelacion estado;

	/** Estado en que quedó la reserva. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoReserva estadoFinalReserva;

	/** Momento de la cancelación. */
	@Column(nullable = false)
	private LocalDateTime fecha;

	/** Usuario que canceló. */
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

	/** Reserva cancelada. */
	@Column(name = "id_reserva", nullable = false, unique = true)
	private Long idReserva;

	/**
	 * Clave foránea hacia {@link Reserva}; el valor se asigna con
	 * {@code idReserva}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_reserva", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Reserva reserva;
}
