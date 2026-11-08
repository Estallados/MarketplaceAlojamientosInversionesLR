package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token para restablecer la contraseña. Vive en memoria
 * ({@code TokenRecuperacionRepository}), no en la base de datos. Solo se guarda
 * el hash del token, nunca el token en claro.
 */
@Getter
@Setter
@NoArgsConstructor
public class TokenRecuperacion {

	/** Usuario dueño del token. */
	private Long idUsuario;

	/** Hash del token; el token en claro no se guarda. */
	private String tokenHash;

	/** Momento en que el token deja de servir. */
	private LocalDateTime fechaExpiracion;

	/** {@code true} si el token ya se usó o fue reemplazado por uno nuevo. */
	private Boolean usado;
}
