package co.edu.unbosque.marketplaceInversionesLR.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;

/**
 * Acceso a las reseñas: la calificación de los alojamientos (HU-23) y el
 * registro y la consulta de reseñas (HU-03). Las consultas "de anfitrión"
 * juntan las reseñas de todos sus alojamientos; las de un solo alojamiento usan
 * las consultas por alojamiento.
 */
public interface ResenaRepository extends JpaRepository<Resena, Long> {

	/** Reseñas en un estado de los alojamientos de un anfitrión. */
	String DE_ANFITRION = "r.estado = :estado and r.idAlojamiento in "
			+ "(select a.idAlojamiento from Alojamiento a where a.idAnfitrion = :idAnfitrion)";

	/**
	 * Promedio de las calificaciones de un alojamiento en un estado.
	 *
	 * @param idAlojamiento alojamiento
	 * @param estado        estado de las reseñas que cuentan
	 * @return el promedio, o {@code null} si no tiene reseñas
	 */
	@Query("select avg(r.calificacion) from Resena r where r.idAlojamiento = :id and r.estado = :estado")
	Double promedio(@Param("id") Long idAlojamiento, @Param("estado") EstadoResena estado);

	/**
	 * Cantidad de reseñas de un alojamiento en un estado.
	 *
	 * @param idAlojamiento alojamiento
	 * @param estado        estado de las reseñas que cuentan
	 * @return la cantidad
	 */
	long countByIdAlojamientoAndEstado(Long idAlojamiento, EstadoResena estado);

	/**
	 * Indica si una reserva ya tiene reseña (hay una por reserva).
	 *
	 * @param idReserva reserva
	 * @return {@code true} si el huésped ya la reseñó
	 */
	boolean existsByIdReserva(Long idReserva);

	/**
	 * Reseñas de un alojamiento en un estado (HU-03, CA-019).
	 *
	 * @param idAlojamiento alojamiento
	 * @param estado        estado de las reseñas, normalmente PUBLICADA
	 * @param pagina        página y orden
	 * @return página de reseñas
	 */
	Page<Resena> findByIdAlojamientoAndEstado(Long idAlojamiento, EstadoResena estado, Pageable pagina);

	/**
	 * Reseñas que escribió un huésped, en cualquier estado (HU-03).
	 *
	 * @param idUsuario huésped
	 * @param pagina    página y orden
	 * @return página de reseñas
	 */
	Page<Resena> findByIdUsuario(Long idUsuario, Pageable pagina);

	/**
	 * Reseñas en un estado, para la moderación del administrador (HU-14).
	 *
	 * @param estado estado a filtrar
	 * @param pagina página y orden
	 * @return página de reseñas
	 */
	Page<Resena> findByEstado(EstadoResena estado, Pageable pagina);

	/**
	 * Cambia solo el estado de una reseña (moderación, HU-14). En Neon,
	 * {@code trg_resena_proteger} impide cambiar cualquier otra columna, por eso
	 * no se vuelve a guardar la reseña completa.
	 *
	 * @param idResena reseña
	 * @param estado   estado nuevo
	 * @return filas actualizadas
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Resena r set r.estado = :estado where r.idResena = :id")
	int cambiarEstado(@Param("id") Long idResena, @Param("estado") EstadoResena estado);

	/**
	 * Reseñas en un estado de todos los alojamientos de un anfitrión (HU-03).
	 *
	 * @param idAnfitrion anfitrión
	 * @param estado      estado de las reseñas
	 * @param pagina      página y orden
	 * @return página de reseñas
	 */
	@Query(value = "select r from Resena r where " + DE_ANFITRION,
			countQuery = "select count(r) from Resena r where " + DE_ANFITRION)
	Page<Resena> buscarDeAnfitrion(@Param("idAnfitrion") Long idAnfitrion, @Param("estado") EstadoResena estado,
			Pageable pagina);

	/**
	 * Promedio de las calificaciones de todos los alojamientos de un anfitrión.
	 *
	 * @param idAnfitrion anfitrión
	 * @param estado      estado de las reseñas que cuentan
	 * @return el promedio, o {@code null} si no tiene reseñas
	 */
	@Query("select avg(r.calificacion) from Resena r where " + DE_ANFITRION)
	Double promedioDeAnfitrion(@Param("idAnfitrion") Long idAnfitrion, @Param("estado") EstadoResena estado);

	/**
	 * Cantidad de reseñas de todos los alojamientos de un anfitrión.
	 *
	 * @param idAnfitrion anfitrión
	 * @param estado      estado de las reseñas
	 * @return la cantidad
	 */
	@Query("select count(r) from Resena r where " + DE_ANFITRION)
	long contarDeAnfitrion(@Param("idAnfitrion") Long idAnfitrion, @Param("estado") EstadoResena estado);
}
