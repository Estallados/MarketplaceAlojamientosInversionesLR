package co.edu.unbosque.marketplaceInversionesLR.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Imagen de un alojamiento. No está en la base de datos: el archivo y sus datos
 * (alojamiento, portada y orden) se guardan en Cloudinary y se cargan en
 * memoria ({@code ImagenRepository}).
 */
@Getter
@Setter
@NoArgsConstructor
public class Imagen {

	/** Identificador de la imagen, calculado a partir de su id en Cloudinary. */
	private Long idImagen;

	/** URL pública de la imagen. */
	private String url;

	/** Alojamiento dueño de la imagen. */
	private Long idAlojamiento;

	/** {@code true} si es la portada del alojamiento. */
	private Boolean principal;

	/** Id del archivo en Cloudinary (public_id). */
	private String rutaArchivo;

	/** Posición de la imagen dentro del alojamiento. */
	private long orden;

	/**
	 * Crea una copia de otra imagen, para que cambiarla no afecte a la
	 * guardada en memoria.
	 *
	 * @param otra imagen a copiar
	 */
	public Imagen(Imagen otra) {
		this.idImagen = otra.idImagen;
		this.url = otra.url;
		this.idAlojamiento = otra.idAlojamiento;
		this.principal = otra.principal;
		this.rutaArchivo = otra.rutaArchivo;
		this.orden = otra.orden;
	}
}
