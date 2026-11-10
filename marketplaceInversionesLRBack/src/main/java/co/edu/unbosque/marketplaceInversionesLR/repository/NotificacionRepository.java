package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;

/**
 * Acceso a las notificaciones de los usuarios.
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	/**
	 * Notificaciones de un usuario, de la más reciente a la más antigua.
	 *
	 * @param idUsuario usuario
	 * @return notificaciones del usuario
	 */
	List<Notificacion> findByIdUsuarioOrderByFechaDescIdNotificacionDesc(Long idUsuario);
}
