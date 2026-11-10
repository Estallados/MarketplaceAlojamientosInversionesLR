package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import jakarta.persistence.LockModeType;

/**
 * Acceso a los alojamientos. Las consultas del catálogo, la búsqueda y los
 * listados del anfitrión se agregan con sus historias de usuario.
 */
public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long> {

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
	 * Publicaciones de un anfitrión, paginadas (HU-19).
	 *
	 * @param idAnfitrion anfitrión dueño
	 * @param pageable    página y orden
	 * @return página de alojamientos
	 */
	Page<Alojamiento> findByIdAnfitrion(Long idAnfitrion, Pageable pageable);
}
