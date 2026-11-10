package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estados de una reseña. Solo las PUBLICADA cuentan para la calificación de
 * un alojamiento.
 */
public enum EstadoResena {

	/** Visible para todos y cuenta en el promedio. */
	PUBLICADA,

	/** Ocultada por un administrador; no se muestra ni cuenta. */
	OCULTA
}
