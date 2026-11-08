package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.List;

import org.springframework.data.repository.Repository;

import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;

/**
 * Historial de estados de los alojamientos. Solo se insertan y se consultan
 * registros; nunca se editan ni se borran.
 */
public interface HistorialEstadoAlojamientoRepository extends Repository<HistorialEstadoAlojamiento, Long> {

	/**
	 * Guarda un cambio de estado.
	 *
	 * @param historial registro a guardar
	 * @return el registro guardado
	 */
	public HistorialEstadoAlojamiento save(HistorialEstadoAlojamiento historial);

	/**
	 * Cambios de estado de un alojamiento, del más antiguo al más reciente.
	 *
	 * @param idAlojamiento alojamiento
	 * @return historial de estados
	 */
	public List<HistorialEstadoAlojamiento> findByIdAlojamientoOrderByFechaAscIdHistorialAsc(Long idAlojamiento);
}
