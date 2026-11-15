package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Imagen de un alojamiento tal como sale por la API (esquema {@code Imagen} de
 * {@code docs/openapi/alojamientos.yaml}).
 */
@Getter
@Setter
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

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public ImagenDTO() {
		// Los valores se asignan con los setters.
	}
}
