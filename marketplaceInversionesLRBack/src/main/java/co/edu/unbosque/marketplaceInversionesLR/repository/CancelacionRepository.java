package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
