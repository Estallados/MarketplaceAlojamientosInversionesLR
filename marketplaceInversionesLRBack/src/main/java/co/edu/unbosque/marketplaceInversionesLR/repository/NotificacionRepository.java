package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;

/**
 * Acceso a las notificaciones de los usuarios. Las consultas de HU-02 filtran
 * por el usuario dueño (CA-013); en Neon las cubren los índices
 * {@code idx_notificacion_usuario} e {@code idx_notificacion_pendientes}.
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

	/**
	 * Notificaciones de un usuario, de la más reciente a la más antigua.
	 *
	 * @param idUsuario usuario
	 * @return notificaciones del usuario
	 */
	List<Notificacion> findByIdUsuarioOrderByFechaDescIdNotificacionDesc(Long idUsuario);

	/**
	 * Notificaciones de un usuario, paginadas (HU-02).
	 *
	 * @param idUsuario usuario
	 * @param pagina    página y orden
	 * @return página de notificaciones
	 */
	Page<Notificacion> findByIdUsuario(Long idUsuario, Pageable pagina);

	/**
	 * Una notificación, solo si es del usuario.
	 *
	 * @param idNotificacion notificación
	 * @param idUsuario      usuario dueño
	 * @return la notificación, o vacío si no existe o es de otro usuario
	 */
	Optional<Notificacion> findByIdNotificacionAndIdUsuario(Long idNotificacion, Long idUsuario);

	/**
	 * Cantidad de notificaciones sin leer de un usuario.
	 *
	 * @param idUsuario usuario
	 * @return cantidad sin leer
	 */
	long countByIdUsuarioAndLeidaFalse(Long idUsuario);

	/**
	 * Marca como leídas todas las notificaciones sin leer de un usuario, en una
	 * sola sentencia.
	 *
	 * @param idUsuario usuario
	 * @return cuántas notificaciones cambiaron
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Notificacion n set n.leida = true where n.idUsuario = :idUsuario and n.leida = false")
	int marcarTodasLeidas(@Param("idUsuario") Long idUsuario);
}
