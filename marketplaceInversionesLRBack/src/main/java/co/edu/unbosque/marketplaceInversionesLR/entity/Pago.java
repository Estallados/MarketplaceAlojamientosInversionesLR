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
 * Pago de prueba de una reserva, mapeado a la tabla {@code pago} de Neon. No
 * mueve dinero real. En Neon el valor debe ser el total de la reserva y, una
 * vez guardado, solo cambian el estado y los datos del reembolso.
 */
@Entity
@Table(name = "pago")
@Getter
@Setter
@NoArgsConstructor
public class Pago {

	/** Identificador del pago. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idPago;

	/** Referencia del proveedor, por ejemplo {@code sim_pi_...}. */
	private String referencia;

	/** Proveedor que procesó el pago: SIMULADO o STRIPE_TEST. */
	@Column(nullable = false, length = 30)
	private String proveedor;

	/** Valor cobrado; siempre el total de la reserva. */
	@Column(nullable = false, precision = 18, scale = 2)
	private BigDecimal valor;

	/** Resultado del cobro. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPago estado;

	/** Mensaje de la pasarela, por ejemplo la causa de un rechazo. */
	private String detalle;

	/** Momento del cobro. */
	@Column(nullable = false)
	private LocalDateTime fecha;

	/** Método de pago usado, por ejemplo {@code pm_card_visa}. */
	private String metodo;

	/** Valor devuelto al cancelar; {@code null} si no hubo devolución. */
	@Column(precision = 18, scale = 2)
	private BigDecimal valorReembolsado;

	/** Referencia de la devolución en el proveedor. */
	private String referenciaReembolso;

	/** Reserva pagada. */
	@Column(name = "id_reserva", nullable = false)
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
