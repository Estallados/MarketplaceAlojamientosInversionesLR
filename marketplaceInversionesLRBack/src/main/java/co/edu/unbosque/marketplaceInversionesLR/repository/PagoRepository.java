package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
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

	/**
	 * Suma de los pagos que están en alguno de los estados indicados, para los
	 * ingresos estimados del dashboard (HU-04).
	 *
	 * @param estados estados a incluir, por ejemplo APROBADO y REEMBOLSADO
	 * @return suma de los valores; 0 si no hay pagos
	 */
	@Query("select coalesce(sum(p.valor), 0) from Pago p where p.estado in :estados")
	BigDecimal sumarValores(@Param("estados") Collection<EstadoPago> estados);
}
