package co.edu.unbosque.marketplaceInversionesLR.dto;

/**
 * Credenciales de inicio de sesión; codigo es el segundo factor (TOTP) cuando
 * la cuenta lo tiene activo.
 */
public class LoginDTO {

	/** Correo de la cuenta. */
	private String correo;

	/** Contraseña en claro; solo se compara, nunca se guarda. */
	private String contrasenia;

	/** Código del segundo factor; solo si la cuenta lo tiene activo. */
	private String codigo;

	/** Constructor vacío, necesario para convertir desde y hacia JSON. */
	public LoginDTO() {
	}

	/**
	 * Devuelve el correo de la cuenta.
	 *
	 * @return correo de la cuenta
	 */
	public String getCorreo() {
		return correo;
	}

	/**
	 * Asigna el correo de la cuenta.
	 *
	 * @param correo correo de la cuenta
	 */
	public void setCorreo(String correo) {
		this.correo = correo;
	}

	/**
	 * Devuelve la contraseña en claro; solo se compara, nunca se guarda.
	 *
	 * @return contraseña en claro; solo se compara, nunca se guarda
	 */
	public String getContrasenia() {
		return contrasenia;
	}

	/**
	 * Asigna la contraseña en claro; solo se compara, nunca se guarda.
	 *
	 * @param contrasenia contraseña en claro; solo se compara, nunca se guarda
	 */
	public void setContrasenia(String contrasenia) {
		this.contrasenia = contrasenia;
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
