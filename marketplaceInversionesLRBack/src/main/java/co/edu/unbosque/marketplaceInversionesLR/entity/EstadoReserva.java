package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estado de una reserva; son los valores que permite la restricción
 * {@code ck_reserva_estado} de la tabla {@code reserva}.
 */
public enum EstadoReserva {

	/** Creada y esperando el pago; retiene las fechas por poco tiempo. */
	PENDIENTE_PAGO,

	/** Pagada; ocupa las fechas. */
	CONFIRMADA,

	/** En revisión; ocupa las fechas. */
	EN_REVISION,

	/** Cancelada; libera las fechas. */
	CANCELADA
}
