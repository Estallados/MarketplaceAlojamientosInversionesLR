package co.edu.unbosque.marketplaceInversionesLR.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Política de cancelación tal como sale por la API (esquema
 * {@code PoliticaCancelacion} de {@code docs/openapi/alojamientos.yaml}).
 */
@Getter
@Setter
@NoArgsConstructor
public class PoliticaCancelacionDTO {

	/** Identificador de la política. */
	private Long idPolitica;

	/** Nombre de la política. */
	private String nombre;

	/** Condiciones explicadas para el huésped. */
	private String condiciones;

	/** Días de anticipación para recibir el reembolso total. */
	private Integer diasReembolsoTotal;

	/** Días de anticipación para recibir el reembolso parcial. */
	private Integer diasReembolsoParcial;

	/** Porcentaje que se devuelve en el reembolso parcial. */
	private Integer porcentajeParcial;
}
