package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Devolución estimada si el huésped cancelara hoy (CA-001). No cambia nada.
 */
@Getter
@Setter
@NoArgsConstructor
public class SimulacionCancelacionDTO {

	/** Reserva consultada. */
	private Long idReserva;

	/** Política de cancelación del alojamiento. */
	private String politica;

	/** Días que faltan para la llegada, contados desde hoy. */
	private Long diasAnticipacion;

	/** Porcentaje que se devolvería. */
	private Integer porcentajeReembolso;

	/** Valor que se devolvería. */
	private BigDecimal valorReembolso;

	/** Total pagado por la reserva. */
	private BigDecimal totalPagado;
}
