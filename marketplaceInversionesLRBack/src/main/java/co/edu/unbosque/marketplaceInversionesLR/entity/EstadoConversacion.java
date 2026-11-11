package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estados de una conversación entre un huésped y un anfitrión (HU-28).
 */
public enum EstadoConversacion {

	/** Se pueden enviar mensajes. */
	ACTIVA,

	/** La conversación terminó; ya no admite mensajes nuevos. */
	CERRADA
}
