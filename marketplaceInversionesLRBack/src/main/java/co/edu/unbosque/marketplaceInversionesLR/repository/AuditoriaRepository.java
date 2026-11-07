package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;

/**
 * Acceso a la auditoría. Es de solo inserción: extiende {@link Repository} (no
 * {@code JpaRepository}) para no exponer update ni delete (RNF-015).
 */
public interface AuditoriaRepository extends Repository<EventoAuditoria, Long> {

	/**
	 * Guarda un evento nuevo.
	 *
	 * @param evento evento a guardar
	 * @return el evento guardado
	 */
	EventoAuditoria save(EventoAuditoria evento);

	/**
	 * Busca un evento por su id.
	 *
	 * @param idEvento id del evento
	 * @return el evento, si existe
	 */
	Optional<EventoAuditoria> findById(Long idEvento);

	/**
	 * Eventos de una acción, del más reciente al más antiguo.
	 *
	 * @param accion acción exacta, por ejemplo REGISTRO_USUARIO
	 * @return eventos de esa acción
	 */
	List<EventoAuditoria> findByAccionOrderByFechaDesc(String accion);
}
