package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estados de un pago de prueba. Neon solo deja pasar de PENDIENTE a APROBADO,
 * RECHAZADO o ERROR, y de APROBADO a REEMBOLSADO.
 */
public enum EstadoPago {

	/** Cobro iniciado sin resultado todavía. */
	PENDIENTE,

	/** La pasarela aprobó el cobro; con él la reserva puede quedar CONFIRMADA. */
	APROBADO,

	/** La pasarela rechazó el cobro, por ejemplo por fondos insuficientes. */
	RECHAZADO,

	/** No se pudo confirmar el cobro con la pasarela. */
	ERROR,

	/** El cobro se devolvió al cancelar la reserva. */
	REEMBOLSADO
}
