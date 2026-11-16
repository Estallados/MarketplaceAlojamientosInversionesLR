package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import co.edu.unbosque.marketplaceInversionesLR.entity.Cancelacion;

/**
 * Acceso a las cancelaciones de reservas.
 */
public interface CancelacionRepository extends JpaRepository<Cancelacion, Long> {

	/**
	 * Cancelación de una reserva; hay a lo sumo una.
	 *
	 * @param idReserva reserva
	 * @return la cancelación, si la reserva se canceló
	 */
	Optional<Cancelacion> findByIdReserva(Long idReserva);

	/**
	 * Cancelaciones de varias reservas en una sola consulta.
	 *
	 * @param idsReserva reservas a consultar
	 * @return sus cancelaciones (una por reserva cancelada)
	 */
	List<Cancelacion> findByIdReservaIn(Collection<Long> idsReserva);

	/**
	 * Total devuelto en las cancelaciones, para los ingresos estimados del
	 * dashboard (HU-04).
	 *
	 * @return total devuelto; 0 si no hay cancelaciones
	 */
	@Query("select coalesce(sum(c.valorReembolso), 0) from Cancelacion c")
	BigDecimal sumarReembolsos();
}
