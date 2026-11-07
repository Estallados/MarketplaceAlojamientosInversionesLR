package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Estados de una cuenta (servicio S02 Gestión de usuarios).
 */
public enum EstadoUsuario {

	/** La cuenta puede usarse. */
	ACTIVO,

	/** El dueño desactivó su cuenta; puede reactivarla. */
	INACTIVO,

	/** Un administrador restringió la cuenta. */
	RESTRINGIDO
}
