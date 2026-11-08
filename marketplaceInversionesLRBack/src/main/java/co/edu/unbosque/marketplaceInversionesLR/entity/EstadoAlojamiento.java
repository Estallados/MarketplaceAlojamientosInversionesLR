package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estados de una publicación de alojamiento. Al crearla queda en
 * {@link #BORRADOR}.
 */
public enum EstadoAlojamiento {

	/** Recién creada o devuelta por el administrador; solo la ve su anfitrión. */
	BORRADOR,

	/** Enviada a revisión; espera que un administrador la apruebe. */
	PENDIENTE_REVISION,

	/** Aprobada y visible en el catálogo. */
	ACTIVA,

	/** Pausada por su anfitrión; no recibe reservas. */
	PAUSADA,

	/** Bloqueada por un administrador. */
	BLOQUEADA
}
