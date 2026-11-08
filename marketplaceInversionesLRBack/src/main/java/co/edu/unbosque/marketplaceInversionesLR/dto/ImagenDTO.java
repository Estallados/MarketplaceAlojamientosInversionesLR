package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Imagen de un alojamiento tal como sale por la API (esquema {@code Imagen} de
 * {@code docs/openapi/alojamientos.yaml}).
 */
@Getter
@Setter
@NoArgsConstructor
public class ImagenDTO {

	/** Identificador de la imagen. */
	private Long idImagen;

	/** URL pública de la imagen. */
	private String url;

	/** Alojamiento dueño de la imagen. */
	private Long idAlojamiento;

	/** {@code true} si es la portada del alojamiento. */
	private Boolean principal;

	/** Id del archivo en Cloudinary. */
	private String rutaArchivo;
}
