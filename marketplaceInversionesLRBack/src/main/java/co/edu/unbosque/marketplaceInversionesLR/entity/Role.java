package co.edu.unbosque.marketplaceInversionesLR.entity;

/**
 * Roles de la plataforma. El registro público solo permite HUESPED y
 * ANFITRION; los administradores los crea otro administrador.
 */
public enum Role {

	/** Administrador de la plataforma. */
	ADMIN,

	/** Usuario que busca y reserva alojamientos. */
	HUESPED,

	/** Usuario que publica alojamientos. */
	ANFITRION
}
