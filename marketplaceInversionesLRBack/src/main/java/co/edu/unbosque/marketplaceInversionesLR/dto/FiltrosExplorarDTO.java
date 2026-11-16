package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Opciones de los filtros de Explorar para el conjunto que se está viendo
 * (catálogo o búsqueda): así el frontend arma los filtros y pagina en el
 * servidor sin descargar todos los alojamientos.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FiltrosExplorarDTO {

	/** Tipos de alojamiento disponibles, en orden alfabético. */
	private List<String> tipos;

	/** Amenidades disponibles, en orden alfabético. */
	private List<String> amenidades;

	/** Precio por noche más bajo; {@code null} si no hay alojamientos. */
	private BigDecimal precioMinimo;

	/** Precio por noche más alto; {@code null} si no hay alojamientos. */
	private BigDecimal precioMaximo;
}
