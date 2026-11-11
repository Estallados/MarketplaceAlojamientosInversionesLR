package co.edu.unbosque.marketplaceInversionesLR.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.Mensaje;

/**
 * Acceso a los mensajes de las conversaciones (HU-28).
 */
public interface MensajeRepository extends JpaRepository<Mensaje, Long> {

	/**
	 * Mensajes de una conversación, paginados.
	 *
	 * @param idConversacion conversación
	 * @param pageable       página y orden
	 * @return página de mensajes
	 */
	Page<Mensaje> findByIdConversacion(Long idConversacion, Pageable pageable);
}
