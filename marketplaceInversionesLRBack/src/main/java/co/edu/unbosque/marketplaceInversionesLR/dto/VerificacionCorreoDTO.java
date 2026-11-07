package co.edu.unbosque.marketplaceInversionesLR.dto;

/**
 * Datos para verificar el correo con el código recibido.
 */
public class VerificacionCorreoDTO {

	/** Correo de la cuenta. */
	private String correo;

	/** Código de 6 dígitos. */
	private String codigo;

	/** Constructor vacío, requerido para convertir desde JSON. */
	public VerificacionCorreoDTO() {
		// Jackson necesita un constructor sin parámetros
	}

	/**
	 * Devuelve el correo.
	 *
	 * @return correo de la cuenta
	 */
	public String getCorreo() {
		return correo;
	}

	/**
	 * Asigna el correo.
	 *
	 * @param correo correo de la cuenta
	 */
	public void setCorreo(String correo) {
		this.correo = correo;
	}

	/**
	 * Devuelve el código.
	 *
	 * @return código de 6 dígitos
	 */
	public String getCodigo() {
		return codigo;
	}

	/**
	 * Asigna el código.
	 *
	 * @param codigo código de 6 dígitos
	 */
	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}
}
