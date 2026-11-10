package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Motivo de una decisión del administrador, por ejemplo rechazar una
 * publicación.
 */
@Getter
@Setter
@NoArgsConstructor
public class MotivoDTO {

	/** Motivo; obligatorio, de máximo 500 caracteres. */
	private String motivo;
}
