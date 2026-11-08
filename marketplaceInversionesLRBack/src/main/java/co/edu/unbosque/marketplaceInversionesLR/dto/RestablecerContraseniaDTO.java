package co.edu.unbosque.marketplaceInversionesLR.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos para restablecer la contraseña con el token de recuperación que llegó
 * al correo.
 */
@Getter
@Setter
@NoArgsConstructor
public class RestablecerContraseniaDTO {

	/** Token de recuperación que llegó al correo. */
	private String token;

	/** Contraseña nueva; solo se recibe, nunca se devuelve. */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String contrasenia;
}
