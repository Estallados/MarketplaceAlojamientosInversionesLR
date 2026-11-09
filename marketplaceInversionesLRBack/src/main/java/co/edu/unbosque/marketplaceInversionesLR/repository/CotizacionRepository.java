package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import jakarta.persistence.LockModeType;

/**
 * Acceso a las cotizaciones.
 */
public interface CotizacionRepository extends JpaRepository<Cotizacion, Long> {

	/**
	 * Lee la cotización con bloqueo de escritura. Si el huésped envía la misma
	 * cotización dos veces a la vez, la segunda petición espera a la primera y
	 * ya la ve usada.
	 *
	 * @param id cotización a bloquear
	 * @return la cotización, si existe
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Cotizacion c where c.idCotizacion = :id")
	Optional<Cotizacion> buscarParaReservar(@Param("id") Long id);
}
