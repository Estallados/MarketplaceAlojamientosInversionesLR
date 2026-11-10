package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import jakarta.persistence.LockModeType;

/**
 * Acceso a los alojamientos. La búsqueda pública (HU-23) combina los filtros
 * de {@link AlojamientoEspecificacion}.
 */
public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long>,
		JpaSpecificationExecutor<Alojamiento> {

	/**
	 * Lee el alojamiento con bloqueo de escritura (HU-22, CA-072). Así dos
	 * reservas que llegan al mismo tiempo sobre el mismo alojamiento se procesan
	 * una después de la otra, y la segunda ya ve las fechas ocupadas.
	 *
	 * @param id alojamiento a bloquear
	 * @return el alojamiento, si existe
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Alojamiento a where a.idAlojamiento = :id")
	Optional<Alojamiento> buscarParaReservar(@Param("id") Long id);

	/**
	 * Alojamientos en un estado, por ejemplo la cola PENDIENTE_REVISION (HU-32).
	 *
	 * @param estado estado a filtrar
	 * @param pagina página y orden
	 * @return página de alojamientos
	 */
	Page<Alojamiento> findByEstado(EstadoAlojamiento estado, Pageable pagina);

	/**
	 * Alojamientos de un anfitrión, en cualquier estado (detalle de usuario del
	 * administrador, HU-12).
	 *
	 * @param idAnfitrion anfitrión
	 * @param pagina      página y orden
	 * @return página de alojamientos
	 */
	Page<Alojamiento> findByIdAnfitrion(Long idAnfitrion, Pageable pagina);

	/**
	 * Cantidad de alojamientos por tipo, en orden alfabético, para los filtros
	 * de la página de inicio (HU-23).
	 *
	 * @return filas con la forma [tipo, cantidad]
	 */
	@Query("select a.tipo, count(a) from Alojamiento a where a.tipo is not null group by a.tipo order by a.tipo")
	List<Object[]> contarPorTipo();
}
