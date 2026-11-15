package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * Alojamiento que el usuario guardó en favoritos, con los datos del
 * alojamiento para mostrar la tarjeta.
 */
@Getter
@Setter
public class FavoritoDTO {

	/** Identificador del favorito. */
	private Long idFavorito;

	/** Momento en que se agregó a favoritos. */
	private LocalDateTime fechaAgregado;

	/** Usuario dueño del favorito. */
	private Long idUsuario;

	/** Alojamiento guardado. */
	private Long idAlojamiento;

	/** Datos del alojamiento para mostrar la tarjeta. */
	private AlojamientoDTO alojamiento;

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public FavoritoDTO() {
		// Los valores se asignan con los setters.
	}
}
