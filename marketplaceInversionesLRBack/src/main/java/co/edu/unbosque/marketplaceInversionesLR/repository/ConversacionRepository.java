package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.Conversacion;

/**
 * Acceso a las conversaciones entre huéspedes y anfitriones (HU-28).
 */
public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

	/**
	 * Busca la conversación que ya tiene un huésped sobre un alojamiento, para no
	 * crear otra.
	 *
	 * @param idUsuario     huésped
	 * @param idAlojamiento alojamiento
	 * @return la conversación, si existe
	 */
	Optional<Conversacion> findByIdUsuarioAndIdAlojamiento(Long idUsuario, Long idAlojamiento);

	/**
	 * Conversaciones en las que participa el usuario, como huésped o como
	 * anfitrión.
	 *
	 * @param idUsuario usuario
	 * @param pageable  página y orden
	 * @return página de conversaciones
	 */
	@Query("select c from Conversacion c where c.idUsuario = :id or c.idAnfitrion = :id")
	Page<Conversacion> buscarDeParticipante(@Param("id") Long idUsuario, Pageable pageable);
}
