package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;

/**
 * Acceso a las reservas. Por ahora la disponibilidad (HU-20) solo consulta qué
 * fechas están ocupadas; las historias de reservas agregan lo demás.
 */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

	/**
	 * Condición de una reserva que ocupa fechas: está en uno de los estados
	 * activos, o está pendiente de pago y se creó después del límite.
	 */
	String OCUPA = "(r.estado in :activos or (r.estado = "
			+ "co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva.PENDIENTE_PAGO "
			+ "and r.fechaCreacion >= :limite))";

	/**
	 * Reservas que ocupan fechas de un alojamiento y terminan después de un
	 * día, de la más próxima a la más lejana.
	 *
	 * @param idAlojamiento alojamiento
	 * @param desde         día desde el que se buscan (la salida debe ser
	 *                      posterior)
	 * @param activos       estados que ocupan fechas
	 * @param limite        las reservas pendientes de pago creadas antes de
	 *                      este instante ya no cuentan
	 * @return reservas ordenadas por fecha de entrada
	 */
	@Query("select r from Reserva r where r.idAlojamiento = :id and r.fechaSalida > :desde and " + OCUPA
			+ " order by r.fechaEntrada")
	List<Reserva> buscarOcupadas(@Param("id") Long idAlojamiento, @Param("desde") LocalDate desde,
			@Param("activos") Collection<EstadoReserva> activos, @Param("limite") LocalDateTime limite);
}
