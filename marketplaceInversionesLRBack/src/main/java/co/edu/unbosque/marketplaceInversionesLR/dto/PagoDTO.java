package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pago de prueba de una reserva tal como sale por la API (CA-160). No mueve
 * dinero real.
 */
@Getter
@Setter
@NoArgsConstructor
public class PagoDTO {

	/** Identificador del pago. */
	private Long idPago;

	/** Referencia del proveedor. */
	private String referencia;

	/** Proveedor que procesó el pago: SIMULADO o STRIPE_TEST. */
	private String proveedor;

	/** Valor cobrado. */
	private BigDecimal valor;

	/** Resultado del cobro. */
	private EstadoPago estado;

	/** Mensaje de la pasarela, por ejemplo la causa de un rechazo. */
	private String detalle;

	/** Momento del cobro. */
	private LocalDateTime fecha;

	/** Método de pago usado. */
	private String metodo;

	/** Valor devuelto al cancelar; {@code null} si no hubo devolución. */
	private BigDecimal valorReembolsado;

	/** Referencia de la devolución en el proveedor. */
	private String referenciaReembolso;

	/** Reserva pagada. */
	private Long idReserva;
}
