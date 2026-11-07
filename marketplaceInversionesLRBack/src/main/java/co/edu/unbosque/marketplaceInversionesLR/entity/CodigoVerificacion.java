package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;

/**
 * Código de verificación del correo, que se envía al registrarse.
 * <p>
 * No es una tabla: se guarda en memoria y solo su hash. El código en claro
 * únicamente viaja en el correo al usuario. Vence, sirve una sola vez y admite
 * pocos intentos fallidos.
 * </p>
 */
public class CodigoVerificacion {

	/** Usuario dueño del código. */
	private Long idUsuario;

	/** Hash del código de 6 dígitos; el código en claro no se guarda. */
	private String codigoHash;

	/** Momento en que se generó el código. */
	private LocalDateTime fechaCreacion;

	/** Momento en que el código deja de servir. */
	private LocalDateTime fechaExpiracion;

	/** Intentos fallidos con este código; al llegar al máximo deja de servir. */
	private int intentos;

	/** Indica si el código ya se usó o fue reemplazado por uno nuevo. */
	private boolean usado;

	/**
	 * Devuelve el usuario dueño del código.
	 *
	 * @return id del usuario
	 */
	public Long getIdUsuario() {
		return idUsuario;
	}

	/**
	 * Asigna el usuario dueño del código.
	 *
	 * @param idUsuario id del usuario
	 */
	public void setIdUsuario(Long idUsuario) {
		this.idUsuario = idUsuario;
	}

	/**
	 * Devuelve el hash del código.
	 *
	 * @return hash del código
	 */
	public String getCodigoHash() {
		return codigoHash;
	}

	/**
	 * Asigna el hash del código.
	 *
	 * @param codigoHash hash del código
	 */
	public void setCodigoHash(String codigoHash) {
		this.codigoHash = codigoHash;
	}

	/**
	 * Devuelve cuándo se generó.
	 *
	 * @return momento en que se generó
	 */
	public LocalDateTime getFechaCreacion() {
		return fechaCreacion;
	}

	/**
	 * Asigna cuándo se generó.
	 *
	 * @param fechaCreacion momento en que se generó
	 */
	public void setFechaCreacion(LocalDateTime fechaCreacion) {
		this.fechaCreacion = fechaCreacion;
	}

	/**
	 * Devuelve cuándo vence.
	 *
	 * @return momento en que deja de servir
	 */
	public LocalDateTime getFechaExpiracion() {
		return fechaExpiracion;
	}

	/**
	 * Asigna cuándo vence.
	 *
	 * @param fechaExpiracion momento en que deja de servir
	 */
	public void setFechaExpiracion(LocalDateTime fechaExpiracion) {
		this.fechaExpiracion = fechaExpiracion;
	}

	/**
	 * Devuelve los intentos fallidos.
	 *
	 * @return intentos fallidos
	 */
	public int getIntentos() {
		return intentos;
	}

	/**
	 * Asigna los intentos fallidos.
	 *
	 * @param intentos intentos fallidos
	 */
	public void setIntentos(int intentos) {
		this.intentos = intentos;
	}

	/**
	 * Indica si el código ya se usó o fue reemplazado.
	 *
	 * @return {@code true} si ya no sirve
	 */
	public boolean isUsado() {
		return usado;
	}

	/**
	 * Marca si el código ya se usó o fue reemplazado.
	 *
	 * @param usado {@code true} si ya no sirve
	 */
	public void setUsado(boolean usado) {
		this.usado = usado;
	}
}
