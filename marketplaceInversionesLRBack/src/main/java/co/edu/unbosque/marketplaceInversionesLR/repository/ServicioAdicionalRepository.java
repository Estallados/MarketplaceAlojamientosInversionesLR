package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.ServicioAdicional;

/**
 * Acceso a los servicios adicionales que ofrecen los alojamientos.
 */
public interface ServicioAdicionalRepository extends JpaRepository<ServicioAdicional, Long> {

	/**
	 * Servicios de un alojamiento, en el orden en que se crearon.
	 *
	 * @param idAlojamiento alojamiento a consultar
	 * @return lista de servicios
	 */
	List<ServicioAdicional> findByIdAlojamientoOrderByIdServicio(Long idAlojamiento);

	/**
	 * Busca un servicio verificando que sea del alojamiento indicado.
	 *
	 * @param idServicio    servicio a buscar
	 * @param idAlojamiento alojamiento al que debe pertenecer
	 * @return el servicio, si existe y es de ese alojamiento
	 */
	Optional<ServicioAdicional> findByIdServicioAndIdAlojamiento(Long idServicio, Long idAlojamiento);
}
