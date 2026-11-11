package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reseña tal como sale por la API, asociada a huésped, reserva, alojamiento,
 * fecha y contenido (HU-03, CA-018).
 */
@Getter
@Setter
@NoArgsConstructor
public class ResenaDTO {

	/** Identificador de la reseña. */
	@Schema(example = "9")
	private Long idResena;

	/** Calificación de 1 a 5. */
	@Schema(example = "5", minimum = "1", maximum = "5")
	private Integer calificacion;

	/** Comentario del huésped; {@code null} si no escribió. */
	@Schema(example = "Muy buena ubicación y todo muy limpio.")
	private String comentario;

	/** PUBLICADA u OCULTA (la oculta un administrador). */
	private EstadoResena estado;

	/** Momento en que se publicó. */
	private LocalDateTime fecha;

	/** Huésped que la escribió. */
	@Schema(example = "12")
	private Long idUsuario;

	/** Reserva reseñada. */
	@Schema(example = "15")
	private Long idReserva;

	/** Alojamiento reseñado. */
	@Schema(example = "7")
	private Long idAlojamiento;

	/** Nombre del huésped que la escribió. */
	@Schema(example = "Ana Pérez")
	private String nombreUsuario;

	/** Título del alojamiento reseñado. */
	@Schema(example = "Apartamento en Chapinero")
	private String tituloAlojamiento;
}
