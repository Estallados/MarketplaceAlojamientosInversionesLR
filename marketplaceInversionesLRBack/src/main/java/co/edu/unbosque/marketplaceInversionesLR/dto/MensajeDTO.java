package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Mensaje de una conversación entre un huésped y un anfitrión (HU-28).
 */
@Getter
@Setter
public class MensajeDTO {

	/** Identificador del mensaje. */
	private Long idMensaje;

	/** Texto del mensaje. */
	private String contenido;

	/** Momento en que se envió. */
	private LocalDateTime fechaEnvio;

	/** Conversación a la que pertenece. */
	private Long idConversacion;

	/** Usuario que lo envió. */
	private Long idUsuario;

	/** Nombre de quien envió el mensaje. */
	private String nombreUsuario;

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public MensajeDTO() {
		// Los valores se asignan con los setters.
	}
}
