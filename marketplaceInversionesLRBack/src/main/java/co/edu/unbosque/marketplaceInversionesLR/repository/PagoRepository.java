package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;

/**
 * Acceso a los pagos de las reservas.
 */
public interface PagoRepository extends JpaRepository<Pago, Long> {

	/**
	 * Último pago de una reserva.
	 *
	 * @param idReserva reserva
	 * @return el pago más reciente, si lo hay
	 */
	Optional<Pago> findTopByIdReservaOrderByFechaDescIdPagoDesc(Long idReserva);
}
