package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;

/**
 * Datos de un usuario que viajan por la API: en el registro llegan nombre,
 * correo, contraseña, teléfono y rol; al consultar se devuelven sin la
 * contraseña.
 */
public class UsuarioDTO {

	/** Identificador del usuario. */
	private Long idUsuario;

	/** Nombre completo. */
	private String nombre;

	/** Correo en claro. */
	private String correo;

	/** Teléfono de contacto; es opcional. */
	private String telefono;

	/** Rol del usuario. */
	private Role rol;

	/** Estado de la cuenta. */
	private EstadoUsuario estado;

	/** Contraseña en claro; solo se recibe, nunca se devuelve. */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String contrasenia;

	/** Indica si el segundo factor está activo. */
	private Boolean totpHabilitado;

	/** Fecha en que se creó la cuenta. */
	private LocalDateTime fechaRegistro;

	/** Indica si el correo está verificado. */
	private Boolean correoVerificado;

	/** Constructor vacío, requerido para convertir desde JSON. */
	public UsuarioDTO() {
		// Jackson necesita un constructor sin parámetros
	}

	/**
	 * Devuelve el identificador.
	 *
	 * @return identificador del usuario
	 */
	public Long getIdUsuario() {
		return idUsuario;
	}

	/**
	 * Asigna el identificador.
	 *
	 * @param idUsuario identificador del usuario
	 */
	public void setIdUsuario(Long idUsuario) {
		this.idUsuario = idUsuario;
	}

	/**
	 * Devuelve el nombre.
	 *
	 * @return nombre completo
	 */
	public String getNombre() {
		return nombre;
	}

	/**
	 * Asigna el nombre.
	 *
	 * @param nombre nombre completo
	 */
	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	/**
	 * Devuelve el correo.
	 *
	 * @return correo en claro
	 */
	public String getCorreo() {
		return correo;
	}

	/**
	 * Asigna el correo.
	 *
	 * @param correo correo en claro
	 */
	public void setCorreo(String correo) {
		this.correo = correo;
	}

	/**
	 * Devuelve el teléfono.
	 *
	 * @return teléfono, o {@code null}
	 */
	public String getTelefono() {
		return telefono;
	}

	/**
	 * Asigna el teléfono.
	 *
	 * @param telefono teléfono
	 */
	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	/**
	 * Devuelve el rol.
	 *
	 * @return rol del usuario
	 */
	public Role getRol() {
		return rol;
	}

	/**
	 * Asigna el rol.
	 *
	 * @param rol rol del usuario
	 */
	public void setRol(Role rol) {
		this.rol = rol;
	}

	/**
	 * Devuelve el estado.
	 *
	 * @return estado de la cuenta
	 */
	public EstadoUsuario getEstado() {
		return estado;
	}

	/**
	 * Asigna el estado.
	 *
	 * @param estado estado de la cuenta
	 */
	public void setEstado(EstadoUsuario estado) {
		this.estado = estado;
	}

	/**
	 * Devuelve la contraseña recibida.
	 *
	 * @return contraseña en claro
	 */
	public String getContrasenia() {
		return contrasenia;
	}

	/**
	 * Asigna la contraseña.
	 *
	 * @param contrasenia contraseña en claro
	 */
	public void setContrasenia(String contrasenia) {
		this.contrasenia = contrasenia;
	}

	/**
	 * Devuelve si el segundo factor está activo.
	 *
	 * @return {@code true} si está activo
	 */
	public Boolean getTotpHabilitado() {
		return totpHabilitado;
	}

	/**
	 * Asigna si el segundo factor está activo.
	 *
	 * @param totpHabilitado {@code true} si está activo
	 */
	public void setTotpHabilitado(Boolean totpHabilitado) {
		this.totpHabilitado = totpHabilitado;
	}

	/**
	 * Devuelve la fecha de registro.
	 *
	 * @return fecha en que se creó la cuenta
	 */
	public LocalDateTime getFechaRegistro() {
		return fechaRegistro;
	}

	/**
	 * Asigna la fecha de registro.
	 *
	 * @param fechaRegistro fecha en que se creó la cuenta
	 */
	public void setFechaRegistro(LocalDateTime fechaRegistro) {
		this.fechaRegistro = fechaRegistro;
	}

	/**
	 * Devuelve si el correo está verificado.
	 *
	 * @return {@code true} si está verificado
	 */
	public Boolean getCorreoVerificado() {
		return correoVerificado;
	}

	/**
	 * Asigna si el correo está verificado.
	 *
	 * @param correoVerificado {@code true} si está verificado
	 */
	public void setCorreoVerificado(Boolean correoVerificado) {
		this.correoVerificado = correoVerificado;
	}

	/**
	 * Representación sin la contraseña, para que no aparezca en los logs.
	 *
	 * @return texto con el id, el correo y el rol
	 */
	@Override
	public String toString() {
		return "UsuarioDTO[idUsuario=" + idUsuario + ", correo=" + correo + ", rol=" + rol + "]";
	}
}
