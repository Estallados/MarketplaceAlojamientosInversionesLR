package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estados de una cancelación. La cancelación del huésped se procesa en el
 * momento, así que queda COMPLETADA; los demás estados existen en Neon para
 * flujos con aprobación.
 */
public enum EstadoCancelacion {

	/** Pedida y pendiente de revisión. */
	SOLICITADA,

	/** Aprobada y pendiente de procesar. */
	APROBADA,

	/** Rechazada; la reserva sigue igual. */
	RECHAZADA,

	/** Procesada: la reserva quedó CANCELADA y se hizo la devolución que corresponde. */
	COMPLETADA
}
