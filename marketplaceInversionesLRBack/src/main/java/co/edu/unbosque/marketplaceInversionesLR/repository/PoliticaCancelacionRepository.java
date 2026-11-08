package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;

/**
 * Acceso a las políticas de cancelación.
 */
public interface PoliticaCancelacionRepository extends JpaRepository<PoliticaCancelacion, Long> {

	/**
	 * Busca una política por su nombre.
	 *
	 * @param nombre nombre de la política
	 * @return la política, si existe
	 */
	public Optional<PoliticaCancelacion> findByNombre(String nombre);
}
