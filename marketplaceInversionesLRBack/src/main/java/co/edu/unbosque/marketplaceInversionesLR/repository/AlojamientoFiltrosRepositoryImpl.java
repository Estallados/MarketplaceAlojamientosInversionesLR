package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Implementación con consultas de agregación sobre la misma
 * {@link Specification} que usa el listado (Spring Data la une a
 * {@link AlojamientoRepository}).
 */
public class AlojamientoFiltrosRepositoryImpl implements AlojamientoFiltrosRepository {

	/** Gestor de entidades. */
	@PersistenceContext
	private EntityManager em;

	@Override
	public List<String> tiposDe(Specification<Alojamiento> condicion) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<String> consulta = cb.createQuery(String.class);
		Root<Alojamiento> a = consulta.from(Alojamiento.class);
		consulta.select(a.get("tipo")).distinct(true)
				.where(condiciones(condicion, a, consulta, cb, cb.isNotNull(a.get("tipo"))));
		return em.createQuery(consulta).getResultList();
	}

	@Override
	public List<String> amenidadesDe(Specification<Alojamiento> condicion) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<String> consulta = cb.createQuery(String.class);
		Root<Alojamiento> a = consulta.from(Alojamiento.class);
		Join<Alojamiento, String> amenidad = a.join("amenidades");
		consulta.select(amenidad).distinct(true).where(condiciones(condicion, a, consulta, cb));
		return em.createQuery(consulta).getResultList();
	}

	@Override
	public BigDecimal[] rangoPrecioDe(Specification<Alojamiento> condicion) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> consulta = cb.createTupleQuery();
		Root<Alojamiento> a = consulta.from(Alojamiento.class);
		consulta.multiselect(cb.min(a.<BigDecimal>get("precioNoche")), cb.max(a.<BigDecimal>get("precioNoche")))
				.where(condiciones(condicion, a, consulta, cb));
		Tuple fila = em.createQuery(consulta).getSingleResult();
		return new BigDecimal[] { fila.get(0, BigDecimal.class), fila.get(1, BigDecimal.class) };
	}

	/**
	 * Condición de la especificación más las adicionales.
	 *
	 * @param condicion  especificación del listado
	 * @param a          raíz de la consulta
	 * @param consulta   consulta
	 * @param cb         constructor de criterios
	 * @param adicionales condiciones extra
	 * @return condiciones a aplicar
	 */
	private static Predicate[] condiciones(Specification<Alojamiento> condicion, Root<Alojamiento> a,
			CriteriaQuery<?> consulta, CriteriaBuilder cb, Predicate... adicionales) {
		List<Predicate> todas = new ArrayList<>(List.of(adicionales));
		Predicate base = condicion == null ? null : condicion.toPredicate(a, consulta, cb);
		if (base != null) {
			todas.add(base);
		}
		return todas.toArray(Predicate[]::new);
	}
}
