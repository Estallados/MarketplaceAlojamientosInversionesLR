package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usuario del marketplace: huésped, anfitrión o administrador.
 * <p>
 * Implementa {@link UserDetails} para que Spring Security lo use directamente
 * en la autenticación. El correo se guarda cifrado con AES y, para poder
 * buscarlo, también se guarda su hash ({@code correoHash}), que es único. La
 * contraseña se guarda con BCrypt. Las columnas son las de la tabla
 * {@code usuario} de Neon.
 * </p>
 */
@Entity
@Table(name = "usuario")
public class Usuario implements UserDetails {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/** Identificador del usuario. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idUsuario;

	/** Nombre completo. */
	@Column(nullable = false, length = 255)
	private String nombre;

	/** Correo cifrado con AES; para leerlo se usa {@link #getUsername()}. */
	@Column(nullable = false, length = 1024)
	private String correo;

	/** HMAC del correo normalizado: permite buscar por correo aunque esté cifrado. */
	@Column(nullable = false, unique = true, length = 64)
	private String correoHash;

	/** Teléfono de contacto; es opcional. */
	@Column(length = 255)
	private String telefono;

	/** Rol del usuario. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role rol;

	/** Estado de la cuenta. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoUsuario estado;

	/** Contraseña codificada con BCrypt. */
	@Column(nullable = false, length = 100)
	private String contrasenia;

	/** Motivo que dio el administrador al restringir la cuenta. */
	@Column(length = 500)
	private String motivoRestriccion;

	/** Secreto TOTP cifrado con AES; null si nunca se activó el segundo factor. */
	@Column(length = 512)
	private String secretoTotp;

	/** Indica si el segundo factor está activo; null equivale a false. */
	private Boolean totpHabilitado;

	/** Fecha en que se creó la cuenta. */
	@Column(nullable = false)
	private LocalDateTime fechaRegistro;

	/** Indica si el usuario confirmó su correo con el código de verificación. */
	@Column(nullable = false)
	private Boolean correoVerificado;

	/** Constructor vacío, requerido por JPA. */
	public Usuario() {
		// JPA necesita un constructor sin parámetros
	}

	/**
	 * Una cuenta restringida por un administrador se considera bloqueada: no
	 * puede iniciar sesión.
	 *
	 * @return {@code false} si la cuenta está restringida
	 */
	@Override
	public boolean isAccountNonLocked() {
		return estado != EstadoUsuario.RESTRINGIDO;
	}

	/**
	 * Solo las cuentas activas pueden usarse.
	 *
	 * @return {@code true} si el estado es {@link EstadoUsuario#ACTIVO}
	 */
	@Override
	public boolean isEnabled() {
		return estado == EstadoUsuario.ACTIVO;
	}

	/**
	 * Devuelve la contraseña codificada con BCrypt, para que Spring la compare.
	 *
	 * @return contraseña codificada
	 */
	@Override
	public String getPassword() {
		return contrasenia;
	}

	/**
	 * El nombre de usuario es el correo en claro: se descifra el que está
	 * guardado. Es también el sujeto de los JWT.
	 *
	 * @return correo del usuario
	 */
	@Override
	public String getUsername() {
		return AESUtil.decrypt(correo);
	}

	/**
	 * Devuelve el rol como autoridad de Spring, con el prefijo {@code ROLE_}.
	 *
	 * @return autoridades del usuario
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
	}

	/**
	 * El correo está verificado si se confirmó con el código.
	 *
	 * @return {@code true} si el correo está verificado
	 */
	public boolean esCorreoVerificado() {
		return Boolean.TRUE.equals(correoVerificado);
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
	 * Devuelve el correo cifrado; para leerlo se usa {@link #getUsername()}.
	 *
	 * @return correo cifrado
	 */
	public String getCorreo() {
		return correo;
	}

	/**
	 * Asigna el correo cifrado.
	 *
	 * @param correo correo cifrado con {@link AESUtil#encrypt(String)}
	 */
	public void setCorreo(String correo) {
		this.correo = correo;
	}

	/**
	 * Devuelve el hash del correo.
	 *
	 * @return HMAC del correo normalizado
	 */
	public String getCorreoHash() {
		return correoHash;
	}

	/**
	 * Asigna el hash del correo.
	 *
	 * @param correoHash HMAC del correo normalizado
	 */
	public void setCorreoHash(String correoHash) {
		this.correoHash = correoHash;
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
	 * @param telefono teléfono normalizado, o {@code null}
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
	 * Devuelve la contraseña codificada.
	 *
	 * @return contraseña codificada con BCrypt
	 */
	public String getContrasenia() {
		return contrasenia;
	}

	/**
	 * Asigna la contraseña codificada.
	 *
	 * @param contrasenia contraseña codificada con BCrypt
	 */
	public void setContrasenia(String contrasenia) {
		this.contrasenia = contrasenia;
	}

	/**
	 * Devuelve el motivo de la restricción.
	 *
	 * @return motivo que dio el administrador, o {@code null}
	 */
	public String getMotivoRestriccion() {
		return motivoRestriccion;
	}

	/**
	 * Asigna el motivo de la restricción.
	 *
	 * @param motivoRestriccion motivo que dio el administrador
	 */
	public void setMotivoRestriccion(String motivoRestriccion) {
		this.motivoRestriccion = motivoRestriccion;
	}

	/**
	 * Devuelve el secreto TOTP cifrado.
	 *
	 * @return secreto TOTP cifrado, o {@code null}
	 */
	public String getSecretoTotp() {
		return secretoTotp;
	}

	/**
	 * Asigna el secreto TOTP cifrado.
	 *
	 * @param secretoTotp secreto TOTP cifrado
	 */
	public void setSecretoTotp(String secretoTotp) {
		this.secretoTotp = secretoTotp;
	}

	/**
	 * Devuelve si el segundo factor está activo.
	 *
	 * @return {@code true}, {@code false} o {@code null} (equivale a false)
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
	 * @return {@code true} si se verificó con el código
	 */
	public Boolean getCorreoVerificado() {
		return correoVerificado;
	}

	/**
	 * Asigna si el correo está verificado.
	 *
	 * @param correoVerificado {@code true} si se verificó con el código
	 */
	public void setCorreoVerificado(Boolean correoVerificado) {
		this.correoVerificado = correoVerificado;
	}
}
