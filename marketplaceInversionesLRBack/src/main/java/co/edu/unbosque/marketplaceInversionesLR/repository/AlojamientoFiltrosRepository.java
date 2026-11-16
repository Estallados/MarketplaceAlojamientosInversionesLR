package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;

/**
 * Valores con que se arman los filtros de Explorar (tipos, amenidades y rango
 * de precios) sobre el mismo conjunto de alojamientos que se lista, sin
 * descargar los alojamientos.
 */
public interface AlojamientoFiltrosRepository {

	/**
	 * Tipos distintos de los alojamientos que cumplen la condición.
	 *
	 * @param condicion alojamientos a considerar
	 * @return tipos sin repetir, sin vacíos
	 */
	List<String> tiposDe(Specification<Alojamiento> condicion);

	/**
	 * Amenidades distintas de los alojamientos que cumplen la condición.
	 *
	 * @param condicion alojamientos a considerar
	 * @return amenidades sin repetir
	 */
	List<String> amenidadesDe(Specification<Alojamiento> condicion);

	/**
	 * Precio por noche mínimo y máximo de los alojamientos que cumplen la
	 * condición.
	 *
	 * @param condicion alojamientos a considerar
	 * @return {mínimo, máximo}; ambos {@code null} si no hay ninguno
	 */
	BigDecimal[] rangoPrecioDe(Specification<Alojamiento> condicion);
}
