package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Resultado de una operación auditada.
 */
public enum ResultadoAuditoria {

	/** La operación se hizo. */
	EXITOSO,

	/** La operación se rechazó por una regla de negocio o de permisos. */
	RECHAZADO,

	/** La operación falló por un error técnico. */
	ERROR
}
