package co.edu.unbosque.marketplaceInversionesLR.dto;

import co.edu.unbosque.marketplaceInversionesLR.entity.Role;

/**
 * Datos para iniciar sesión con Google: el ID token ({@code credential}) que
 * entrega el botón de Google en el front, el rol con el que se crea la cuenta
 * si es la primera vez (HUESPED por defecto, o ANFITRION) y el código del
 * segundo factor si la cuenta lo tiene activo.
 */
public class InicioGoogleDTO {

	/** ID token que entregó el botón de Google. */
	private String idToken;

	/** Rol con el que se crea la cuenta la primera vez: HUESPED o ANFITRION. */
	private Role rol;

	/** Código del segundo factor; solo si la cuenta lo tiene activo. */
	private String codigo;

	/** Constructor vacío, necesario para convertir desde y hacia JSON. */
	public InicioGoogleDTO() {
	}

	/**
	 * Devuelve el ID token que entregó el botón de Google.
	 *
	 * @return ID token que entregó el botón de Google
	 */
	public String getIdToken() {
		return idToken;
	}

	/**
	 * Asigna el ID token que entregó el botón de Google.
	 *
	 * @param idToken ID token que entregó el botón de Google
	 */
	public void setIdToken(String idToken) {
		this.idToken = idToken;
	}

	/**
	 * Devuelve el rol con el que se crea la cuenta la primera vez: HUESPED o ANFITRION.
	 *
	 * @return rol con el que se crea la cuenta la primera vez: HUESPED o ANFITRION
	 */
	public Role getRol() {
		return rol;
	}

	/**
	 * Asigna el rol con el que se crea la cuenta la primera vez: HUESPED o ANFITRION.
	 *
	 * @param rol rol con el que se crea la cuenta la primera vez: HUESPED o ANFITRION
	 */
	public void setRol(Role rol) {
		this.rol = rol;
	}

	/**
	 * Devuelve el código del segundo factor; solo si la cuenta lo tiene activo.
	 *
	 * @return código del segundo factor; solo si la cuenta lo tiene activo
	 */
	public String getCodigo() {
		return codigo;
	}

	/**
	 * Asigna el código del segundo factor; solo si la cuenta lo tiene activo.
	 *
	 * @param codigo código del segundo factor; solo si la cuenta lo tiene activo
	 */
	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}
}
