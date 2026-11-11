package co.edu.unbosque.marketplaceInversionesLR.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos que manda el huésped para reseñar una estancia (HU-03). El autor y el
 * alojamiento no se reciben: los fija el servidor a partir del token y de la
 * reserva (CA-018).
 */
@Getter
@Setter
@NoArgsConstructor
public class ResenaEntradaDTO {

	/** Reserva propia y completada que se reseña (CA-016). */
	@Schema(description = "Reserva propia y completada (CA-016)", example = "15",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private Long idReserva;

	/** Calificación de 1 a 5. */
	@Schema(description = "Calificación de 1 a 5", example = "5", minimum = "1", maximum = "5",
			requiredMode = Schema.RequiredMode.REQUIRED)
	private Integer calificacion;

	/** Comentario del huésped; opcional. */
	@Schema(description = "Comentario del huésped (opcional, hasta 2000 caracteres)", maxLength = 2000,
			example = "Muy buena ubicación y todo muy limpio.")
	private String comentario;
}
