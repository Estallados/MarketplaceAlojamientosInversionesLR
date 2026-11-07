package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un usuario que viajan por la API: en el registro llegan nombre,
 * correo, contraseña, teléfono y rol; al consultar se devuelven sin la
 * contraseña. Lombok genera los getters, los setters y el constructor vacío
 * que Jackson necesita.
 */
@Getter
@Setter
@NoArgsConstructor
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
