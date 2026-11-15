package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Motivo de una decisión del administrador, por ejemplo rechazar una
 * publicación.
 */
@Getter
@Setter
public class MotivoDTO {

	/** Motivo; obligatorio, de máximo 500 caracteres. */
	private String motivo;

	/**
	 * Crea el DTO vacío; Jackson y ModelMapper lo llenan campo por campo.
	 */
	public MotivoDTO() {
		// Los valores se asignan con los setters.
	}
}
