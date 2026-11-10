package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cancelación de una reserva tal como sale por la API (CA-004): quién canceló,
 * cuándo, el motivo, la política, la devolución y el estado final de la
 * reserva.
 */
@Getter
@Setter
@NoArgsConstructor
public class CancelacionDTO {

	/** Identificador de la cancelación. */
	private Long idCancelacion;

	/** Motivo que dio el huésped. */
	private String motivo;

	/** Política aplicada, por ejemplo MODERADA. */
	private String politica;

	/** Porcentaje devuelto (0 a 100). */
	private Integer porcentajeReembolso;

	/** Valor devuelto. */
	private BigDecimal valorReembolso;

	/** Estado de la cancelación. */
	private EstadoCancelacion estado;

	/** Estado en que quedó la reserva. */
	private EstadoReserva estadoFinalReserva;

	/** Momento de la cancelación. */
	private LocalDateTime fecha;

	/** Usuario que canceló. */
	private Long idUsuario;

	/** Reserva cancelada. */
	private Long idReserva;
}
