package co.edu.unbosque.marketplaceInversionesLR.dto;

/**
 * Datos para activar o confirmar el segundo factor (TOTP). Al activarlo, el
 * servidor responde el secreto y la URI; al confirmarlo, el cliente manda el
 * código de la app autenticadora.
 */
public class SegundoFactorDTO {

	/** Secreto en Base32, para escribirlo a mano en la app si no se puede leer el QR. */
	private String secreto;

	/** URI {@code otpauth://} que el frontend muestra como código QR. */
	private String uri;

	/** Código de 6 dígitos de la app autenticadora. */
	private String codigo;

	/** Constructor vacío, necesario para convertir desde y hacia JSON. */
	public SegundoFactorDTO() {
	}

	/**
	 * Devuelve el secreto en Base32, para escribirlo a mano en la app si no se puede leer el QR.
	 *
	 * @return secreto en Base32, para escribirlo a mano en la app si no se puede leer el QR
	 */
	public String getSecreto() {
		return secreto;
	}

	/**
	 * Asigna el secreto en Base32, para escribirlo a mano en la app si no se puede leer el QR.
	 *
	 * @param secreto secreto en Base32, para escribirlo a mano en la app si no se puede leer el QR
	 */
	public void setSecreto(String secreto) {
		this.secreto = secreto;
	}

	/**
	 * Devuelve la URI {@code otpauth://} que el frontend muestra como código QR.
	 *
	 * @return URI {@code otpauth://} que el frontend muestra como código QR
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * Asigna la URI {@code otpauth://} que el frontend muestra como código QR.
	 *
	 * @param uri URI {@code otpauth://} que el frontend muestra como código QR
	 */
	public void setUri(String uri) {
		this.uri = uri;
	}

	/**
	 * Devuelve el código de 6 dígitos de la app autenticadora.
	 *
	 * @return código de 6 dígitos de la app autenticadora
	 */
	public String getCodigo() {
		return codigo;
	}

	/**
	 * Asigna el código de 6 dígitos de la app autenticadora.
	 *
	 * @param codigo código de 6 dígitos de la app autenticadora
	 */
	public void setCodigo(String codigo) {
		this.codigo = codigo;
	}
}
