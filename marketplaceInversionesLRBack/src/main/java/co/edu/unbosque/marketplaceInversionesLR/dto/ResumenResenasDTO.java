package co.edu.unbosque.marketplaceInversionesLR.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Reseñas visibles de un alojamiento, o de todos los de un anfitrión, con la
 * calificación promedio y la cantidad (HU-03).
 */
@Getter
@Setter
@NoArgsConstructor
public class ResumenResenasDTO {

	/** Alojamiento consultado; {@code null} si son todas las de un anfitrión. */
	@Schema(description = "Alojamiento consultado; null en la consulta del anfitrión sin filtro", example = "7")
	private Long idAlojamiento;

	/** Promedio con una cifra decimal; {@code null} si no hay reseñas visibles. */
	@Schema(description = "Promedio con una cifra decimal; null si no hay reseñas visibles", example = "4.5")
	private Double promedio;

	/** Cantidad de reseñas visibles. */
	@Schema(example = "8")
	private Long cantidad;

	/** Página de reseñas, de la más reciente a la más antigua. */
	private PaginaDTO<ResenaDTO> resenas;
}
