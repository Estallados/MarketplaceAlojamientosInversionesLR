package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;

/**
 * Datos de un usuario tal como entran y salen por la API (registro, perfil y
 * listados del administrador).
 * <p>
 * La contraseña solo se recibe al registrarse y nunca se devuelve en claro; el
 * hash del correo y el secreto del segundo factor nunca viajan en JSON.
 * </p>
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

	/** Contraseña; solo se recibe al registrarse. */
	private String contrasenia;

	/** Motivo de la restricción, si un administrador la restringió. */
	private String motivoRestriccion;

	/** Indica si el usuario tiene activo el segundo factor. */
	private Boolean totpHabilitado;

	/** Fecha en que se creó la cuenta. */
	private LocalDateTime fechaRegistro;

	/** Indica si el usuario confirmó su correo. */
	private Boolean correoVerificado;
	/** Datos sensibles: nunca se envían ni se reciben en JSON. */
	@JsonIgnore
	private String correoHash;

	/** Secreto del segundo factor cifrado; nunca viaja en JSON. */
	@JsonIgnore
	private String secretoTotp;

	/** Constructor vacío, necesario para convertir desde y hacia JSON. */
	public UsuarioDTO() {
	}

	/**
	 * Devuelve el identificador del usuario.
	 *
	 * @return identificador del usuario
	 */
	public Long getIdUsuario() {
		return idUsuario;
	}

	/**
	 * Asigna el identificador del usuario.
	 *
	 * @param idUsuario identificador del usuario
	 */
	public void setIdUsuario(Long idUsuario) {
		this.idUsuario = idUsuario;
	}

	/**
	 * Devuelve el nombre completo.
	 *
	 * @return nombre completo
	 */
	public String getNombre() {
		return nombre;
	}

	/**
	 * Asigna el nombre completo.
	 *
	 * @param nombre nombre completo
	 */
	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	/**
	 * Devuelve el correo en claro.
	 *
	 * @return correo en claro
	 */
	public String getCorreo() {
		return correo;
	}

	/**
	 * Asigna el correo en claro.
	 *
	 * @param correo correo en claro
	 */
	public void setCorreo(String correo) {
		this.correo = correo;
	}

	/**
	 * Devuelve el teléfono de contacto; es opcional.
	 *
	 * @return teléfono de contacto; es opcional
	 */
	public String getTelefono() {
		return telefono;
	}

	/**
	 * Asigna el teléfono de contacto; es opcional.
	 *
	 * @param telefono teléfono de contacto; es opcional
	 */
	public void setTelefono(String telefono) {
		this.telefono = telefono;
	}

	/**
	 * Devuelve el rol del usuario.
	 *
	 * @return rol del usuario
	 */
	public Role getRol() {
		return rol;
	}

	/**
	 * Asigna el rol del usuario.
	 *
	 * @param rol rol del usuario
	 */
	public void setRol(Role rol) {
		this.rol = rol;
	}

	/**
	 * Devuelve el estado de la cuenta.
	 *
	 * @return estado de la cuenta
	 */
	public EstadoUsuario getEstado() {
		return estado;
	}

	/**
	 * Asigna el estado de la cuenta.
	 *
	 * @param estado estado de la cuenta
	 */
	public void setEstado(EstadoUsuario estado) {
		this.estado = estado;
	}

	/**
	 * Devuelve la contraseña; solo se recibe al registrarse.
	 *
	 * @return contraseña; solo se recibe al registrarse
	 */
	public String getContrasenia() {
		return contrasenia;
	}

	/**
	 * Asigna la contraseña; solo se recibe al registrarse.
	 *
	 * @param contrasenia contraseña; solo se recibe al registrarse
	 */
	public void setContrasenia(String contrasenia) {
		this.contrasenia = contrasenia;
	}

	/**
	 * Devuelve el motivo de la restricción, si un administrador la restringió.
	 *
	 * @return motivo de la restricción, si un administrador la restringió
	 */
	public String getMotivoRestriccion() {
		return motivoRestriccion;
	}

	/**
	 * Asigna el motivo de la restricción, si un administrador la restringió.
	 *
	 * @param motivoRestriccion motivo de la restricción, si un administrador la restringió
	 */
	public void setMotivoRestriccion(String motivoRestriccion) {
		this.motivoRestriccion = motivoRestriccion;
	}

	/**
	 * Devuelve {@code true} si el usuario tiene activo el segundo factor.
	 *
	 * @return {@code true} si el usuario tiene activo el segundo factor
	 */
	public Boolean getTotpHabilitado() {
		return totpHabilitado;
	}

	/**
	 * Asigna {@code true} si el usuario tiene activo el segundo factor.
	 *
	 * @param totpHabilitado {@code true} si el usuario tiene activo el segundo factor
	 */
	public void setTotpHabilitado(Boolean totpHabilitado) {
		this.totpHabilitado = totpHabilitado;
	}

	/**
	 * Devuelve la fecha en que se creó la cuenta.
	 *
	 * @return fecha en que se creó la cuenta
	 */
	public LocalDateTime getFechaRegistro() {
		return fechaRegistro;
	}

	/**
	 * Asigna la fecha en que se creó la cuenta.
	 *
	 * @param fechaRegistro fecha en que se creó la cuenta
	 */
	public void setFechaRegistro(LocalDateTime fechaRegistro) {
		this.fechaRegistro = fechaRegistro;
	}

	/**
	 * Devuelve {@code true} si el usuario confirmó su correo.
	 *
	 * @return {@code true} si el usuario confirmó su correo
	 */
	public Boolean getCorreoVerificado() {
		return correoVerificado;
	}

	/**
	 * Asigna {@code true} si el usuario confirmó su correo.
	 *
	 * @param correoVerificado {@code true} si el usuario confirmó su correo
	 */
	public void setCorreoVerificado(Boolean correoVerificado) {
		this.correoVerificado = correoVerificado;
	}

	/**
	 * Devuelve los datos sensibles: nunca se envían ni se reciben en JSON.
	 *
	 * @return datos sensibles: nunca se envían ni se reciben en JSON
	 */
	public String getCorreoHash() {
		return correoHash;
	}

	/**
	 * Asigna los datos sensibles: nunca se envían ni se reciben en JSON.
	 *
	 * @param correoHash datos sensibles: nunca se envían ni se reciben en JSON
	 */
	public void setCorreoHash(String correoHash) {
		this.correoHash = correoHash;
	}

	/**
	 * Devuelve el secreto del segundo factor cifrado; nunca viaja en JSON.
	 *
	 * @return secreto del segundo factor cifrado; nunca viaja en JSON
	 */
	public String getSecretoTotp() {
		return secretoTotp;
	}

	/**
	 * Asigna el secreto del segundo factor cifrado; nunca viaja en JSON.
	 *
	 * @param secretoTotp secreto del segundo factor cifrado; nunca viaja en JSON
	 */
	public void setSecretoTotp(String secretoTotp) {
		this.secretoTotp = secretoTotp;
	}
}
