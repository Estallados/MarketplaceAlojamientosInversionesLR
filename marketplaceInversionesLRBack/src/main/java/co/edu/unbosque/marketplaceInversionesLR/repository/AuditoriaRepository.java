package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;

/**
 * Acceso a la auditoría. Es de solo inserción y consulta: extiende
 * {@link Repository} (no {@code JpaRepository}) para no exponer update ni
 * delete, así los eventos se conservan para revisarlos después (RNF-007,
 * CA-139).
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

	/**
	 * Consulta paginada con filtros opcionales; un filtro en {@code null} no se
	 * aplica.
	 *
	 * @param accion   acción exacta, por ejemplo LOGIN
	 * @param idActor  usuario que hizo la acción
	 * @param desde    fecha y hora mínima
	 * @param hasta    fecha y hora máxima
	 * @param pageable página, tamaño y orden
	 * @return página de eventos
	 */
	@Query("select e from EventoAuditoria e where (:accion is null or e.accion = :accion) "
			+ "and (:idActor is null or e.idUsuario = :idActor) "
			+ "and (cast(:desde as timestamp) is null or e.fecha >= :desde) "
			+ "and (cast(:hasta as timestamp) is null or e.fecha <= :hasta)")
	Page<EventoAuditoria> buscar(@Param("accion") String accion, @Param("idActor") Long idActor,
			@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta, Pageable pageable);
}
