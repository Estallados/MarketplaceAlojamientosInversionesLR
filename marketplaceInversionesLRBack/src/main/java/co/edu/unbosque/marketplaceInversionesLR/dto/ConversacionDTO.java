package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoConversacion;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Conversación entre un huésped (idUsuario) y un anfitrión, con sus nombres
 * (HU-28). Para iniciarla, el huésped manda idAlojamiento y, si quiere, un
 * primer mensaje.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversacionDTO {

	/** Identificador de la conversación. */
	private Long idConversacion;

	/** Momento en que se inició. */
	private LocalDateTime fechaCreacion;

	/** Estado de la conversación. */
	private EstadoConversacion estado;

	/** Huésped de la conversación. */
	private Long idUsuario;

	/** Anfitrión de la conversación. */
	private Long idAnfitrion;

	/** Alojamiento sobre el que se conversa. */
	private Long idAlojamiento;

	/** Primer mensaje (solo al iniciarla). */
	private String mensaje;

	/** Nombre del huésped. */
	private String nombreUsuario;

	/** Nombre del anfitrión. */
	private String nombreAnfitrion;
}
