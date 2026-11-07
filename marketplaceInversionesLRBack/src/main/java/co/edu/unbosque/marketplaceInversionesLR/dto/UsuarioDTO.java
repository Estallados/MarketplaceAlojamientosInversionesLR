package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un usuario tal como entran y salen por la API (registro, perfil y
 * listados del administrador). Lombok genera los getters, los setters y el
 * constructor vacío que Jackson necesita.
 * <p>
 * La contraseña solo se recibe al registrarse y nunca se devuelve; el hash del
 * correo y el secreto del segundo factor nunca viajan en JSON.
 * </p>
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

	/** Motivo de la restricción, si un administrador la restringió. */
	private String motivoRestriccion;

	/** Indica si el usuario tiene activo el segundo factor. */
	private Boolean totpHabilitado;

	/** Fecha en que se creó la cuenta. */
	private LocalDateTime fechaRegistro;

	/** Indica si el usuario confirmó su correo. */
	private Boolean correoVerificado;

	/** Hash del correo para buscarlo; nunca se envía ni se recibe en JSON. */
	@JsonIgnore
	private String correoHash;

	/** Secreto del segundo factor cifrado; nunca viaja en JSON. */
	@JsonIgnore
	private String secretoTotp;

	/**
	 * Representación sin la contraseña ni datos sensibles, para que no aparezcan
	 * en los logs.
	 *
	 * @return texto con el id, el correo y el rol
	 */
	@Override
	public String toString() {
		return "UsuarioDTO[idUsuario=" + idUsuario + ", correo=" + correo + ", rol=" + rol + "]";
	}
}
