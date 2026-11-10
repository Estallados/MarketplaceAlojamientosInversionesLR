package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estado de una clave {@code Idempotency-Key} (RNF-008).
 */
public enum EstadoIdempotencia {

	/** La primera petición con la clave todavía se está procesando. */
	EN_PROCESO,

	/** La petición terminó y su respuesta quedó guardada. */
	COMPLETADA
}
