package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtros de la búsqueda pública de alojamientos (HU-23), como
 * especificaciones de JPA.
 * <p>
 * Cada método devuelve una condición que se combina con las demás. Si el valor
 * llega vacío o {@code null}, la condición no filtra (devuelve {@code null},
 * que JPA interpreta como "sin filtro"); así el usuario busca solo por los
 * campos que le interesan.
 * </p>
 */
public final class AlojamientoEspecificacion {

	/** Atributo del id, que se repite en las subconsultas. */
	private static final String ID_ALOJAMIENTO = "idAlojamiento";

	/** Atributo del estado, en el alojamiento, la reseña y la reserva. */
	private static final String ESTADO = "estado";

	/** Clase utilitaria: no se instancia. */
	private AlojamientoEspecificacion() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Solo publicaciones en el estado indicado (la búsqueda pública usa
	 * ACTIVA).
	 *
	 * @param estado estado requerido
	 * @return condición
	 */
	public static Specification<Alojamiento> conEstado(EstadoAlojamiento estado) {
		return (root, query, cb) -> cb.equal(root.get(ESTADO), estado);
	}

	/**
	 * Solo alojamientos de una ciudad, sin distinguir mayúsculas.
	 *
	 * @param ciudad ciudad a buscar; vacía no filtra
	 * @return condición
	 */
	public static Specification<Alojamiento> enCiudad(String ciudad) {
		return (root, query, cb) -> vacio(ciudad) ? null
				: cb.equal(cb.lower(root.get("ciudad")), minusculas(ciudad));
	}

	/**
	 * Solo alojamientos de un tipo, sin distinguir mayúsculas.
	 *
	 * @param tipo tipo a buscar; vacío no filtra
	 * @return condición
	 */
	public static Specification<Alojamiento> deTipo(String tipo) {
		return (root, query, cb) -> vacio(tipo) ? null : cb.equal(cb.lower(root.get("tipo")), minusculas(tipo));
	}

	/**
	 * Solo alojamientos con capacidad para la cantidad de huéspedes.
	 *
	 * @param huespedes cantidad de huéspedes; {@code null} no filtra
	 * @return condición
	 */
	public static Specification<Alojamiento> conCapacidadPara(Integer huespedes) {
		return (root, query, cb) -> huespedes == null ? null
				: cb.greaterThanOrEqualTo(root.get("capacidad"), huespedes);
	}

	/**
	 * Solo alojamientos con precio por noche igual o mayor al mínimo.
	 *
	 * @param minimo precio mínimo; {@code null} no filtra
	 * @return condición
	 */
	public static Specification<Alojamiento> precioDesde(BigDecimal minimo) {
		return (root, query, cb) -> minimo == null ? null : cb.greaterThanOrEqualTo(root.get("precioNoche"), minimo);
	}

	/**
	 * Solo alojamientos con precio por noche igual o menor al máximo.
	 *
	 * @param maximo precio máximo; {@code null} no filtra
	 * @return condición
	 */
	public static Specification<Alojamiento> precioHasta(BigDecimal maximo) {
		return (root, query, cb) -> maximo == null ? null : cb.lessThanOrEqualTo(root.get("precioNoche"), maximo);
	}

	/**
	 * Solo alojamientos que tengan todas las amenidades indicadas (CP-030). Las
	 * amenidades se guardan en minúsculas, así que se comparan igual.
	 *
	 * @param amenidades amenidades requeridas; vacía o sin valores no filtra
	 * @return condición
	 */
	public static Specification<Alojamiento> conAmenidades(Collection<String> amenidades) {
		return (root, query, cb) -> {
			if (amenidades == null) {
				return null;
			}
			Predicate[] todas = amenidades.stream().filter(a -> !vacio(a)).map(AlojamientoEspecificacion::minusculas)
					.distinct().map(a -> cb.isMember(a, root.<Collection<String>>get("amenidades")))
					.toArray(Predicate[]::new);
			return todas.length == 0 ? null : cb.and(todas);
		};
	}

	/**
	 * Solo alojamientos con fechas para reservar: tienen un período ofrecido
	 * con al menos una noche desde {@code desde}. Así el catálogo no muestra
	 * publicaciones activas sin calendario o con todos sus períodos vencidos.
	 *
	 * @param desde fecha de referencia (normalmente hoy)
	 * @return condición
	 */
	public static Specification<Alojamiento> conFechasDesde(LocalDate desde) {
		return (root, query, cb) -> {
			Subquery<Long> periodo = query.subquery(Long.class);
			Root<PeriodoDisponibilidad> p = periodo.from(PeriodoDisponibilidad.class);
			periodo.select(p.get("idPeriodo")).where(cb.equal(p.get(ID_ALOJAMIENTO), root.get(ID_ALOJAMIENTO)),
					cb.greaterThan(p.get("fechaFin"), desde));
			return cb.exists(periodo);
		};
	}

	/**
	 * Solo alojamientos libres en [llegada, salida): un período ofrecido
	 * contiene las fechas y ninguna reserva que ocupa fechas se cruza con ellas.
	 * Es la misma regla que {@code CalendarioService.estaLibre}.
	 *
	 * @param llegada         día de llegada; {@code null} no filtra
	 * @param salida          día de salida; {@code null} no filtra
	 * @param estadosActivos  estados de reserva que ocupan fechas
	 * @param limitePendiente las reservas PENDIENTE_PAGO creadas antes de este
	 *                        momento ya no ocupan fechas
	 * @return condición
	 */
	public static Specification<Alojamiento> libreEntre(LocalDate llegada, LocalDate salida,
			List<EstadoReserva> estadosActivos, LocalDateTime limitePendiente) {
		return (root, query, cb) -> {
			if (llegada == null || salida == null) {
				return null;
			}
			Subquery<Long> periodo = query.subquery(Long.class);
			Root<PeriodoDisponibilidad> p = periodo.from(PeriodoDisponibilidad.class);
			periodo.select(p.get("idPeriodo")).where(cb.equal(p.get(ID_ALOJAMIENTO), root.get(ID_ALOJAMIENTO)),
					cb.lessThanOrEqualTo(p.get("fechaInicio"), llegada),
					cb.greaterThanOrEqualTo(p.get("fechaFin"), salida));

			Subquery<Long> cruce = query.subquery(Long.class);
			Root<Reserva> r = cruce.from(Reserva.class);
			cruce.select(r.get("idReserva")).where(cb.equal(r.get(ID_ALOJAMIENTO), root.get(ID_ALOJAMIENTO)),
					cb.lessThan(r.get("fechaEntrada"), salida), cb.greaterThan(r.get("fechaSalida"), llegada),
					cb.or(r.get(ESTADO).in(estadosActivos),
							cb.and(cb.equal(r.get(ESTADO), EstadoReserva.PENDIENTE_PAGO),
									cb.greaterThanOrEqualTo(r.get("fechaCreacion"), limitePendiente))));

			return cb.and(cb.exists(periodo), cb.not(cb.exists(cruce)));
		};
	}

	/**
	 * Ordena por las reseñas publicadas: por cantidad y luego por promedio (los
	 * más recomendados) o por promedio y luego por cantidad (los mejor
	 * calificados). Los empates quedan del más reciente al más antiguo. No
	 * filtra nada; se usa solo en la consulta de la página, no en la que cuenta
	 * el total.
	 *
	 * @param primeroCantidad {@code true} para ordenar primero por cantidad de
	 *                        reseñas
	 * @return especificación que solo ordena
	 */
	public static Specification<Alojamiento> ordenadoPorResenas(boolean primeroCantidad) {
		return (root, query, cb) -> {
			Subquery<Double> promedio = query.subquery(Double.class);
			Root<Resena> r = promedio.from(Resena.class);
			promedio.select(cb.coalesce(cb.avg(r.get("calificacion")), 0.0)).where(
					cb.equal(r.get(ID_ALOJAMIENTO), root.get(ID_ALOJAMIENTO)),
					cb.equal(r.get(ESTADO), EstadoResena.PUBLICADA));

			Subquery<Long> cantidad = query.subquery(Long.class);
			Root<Resena> c = cantidad.from(Resena.class);
			cantidad.select(cb.count(c)).where(cb.equal(c.get(ID_ALOJAMIENTO), root.get(ID_ALOJAMIENTO)),
					cb.equal(c.get(ESTADO), EstadoResena.PUBLICADA));

			Order porPromedio = cb.desc(promedio);
			Order porCantidad = cb.desc(cantidad);
			query.orderBy(primeroCantidad ? porCantidad : porPromedio, primeroCantidad ? porPromedio : porCantidad,
					cb.desc(root.get("fechaCreacion")), cb.desc(root.get(ID_ALOJAMIENTO)));
			return null;
		};
	}

	/**
	 * Indica si un texto no trae nada para filtrar.
	 *
	 * @param texto texto recibido
	 * @return {@code true} si es {@code null} o solo espacios
	 */
	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	/**
	 * Texto sin espacios a los lados y en minúsculas, para comparar sin
	 * distinguir mayúsculas.
	 *
	 * @param texto texto recibido
	 * @return texto normalizado
	 */
	private static String minusculas(String texto) {
		return texto.trim().toLowerCase(Locale.ROOT);
	}
}
