package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import jakarta.persistence.LockModeType;

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

	/**
	 * Indica si alguna reserva que ocupa fechas se cruza con la estancia
	 * [llegada, salida). Una reserva que sale el día en que llega la otra no se
	 * cruza (HU-21).
	 *
	 * @param idAlojamiento alojamiento
	 * @param llegada       día de llegada
	 * @param salida        día de salida
	 * @param activos       estados que ocupan fechas
	 * @param limite        las reservas pendientes de pago creadas antes de
	 *                      este instante ya no cuentan
	 * @return {@code true} si hay cruce
	 */
	@Query("select case when count(r) > 0 then true else false end from Reserva r where r.idAlojamiento = :id "
			+ "and r.fechaEntrada < :salida and r.fechaSalida > :llegada and " + OCUPA)
	boolean existeCruce(@Param("id") Long idAlojamiento, @Param("llegada") LocalDate llegada,
			@Param("salida") LocalDate salida, @Param("activos") Collection<EstadoReserva> activos,
			@Param("limite") LocalDateTime limite);

	/**
	 * Reservas futuras de un alojamiento en los estados indicados. Se usa al
	 * bloquear un alojamiento para pasar sus reservas a revisión (HU-11).
	 *
	 * @param idAlojamiento alojamiento
	 * @param fecha         fecha a partir de la cual se buscan las llegadas
	 * @param estados       estados a incluir
	 * @return lista de reservas
	 */
	List<Reserva> findByIdAlojamientoAndFechaEntradaAfterAndEstadoIn(Long idAlojamiento, LocalDate fecha,
			Collection<EstadoReserva> estados);

	/**
	 * Reservas de un huésped, en cualquier estado: su historial (HU-25,
	 * CA-078) y el detalle de usuario del administrador (HU-12).
	 *
	 * @param idHuesped huésped
	 * @param pagina    página y orden
	 * @return página de reservas
	 */
	Page<Reserva> findByIdHuesped(Long idHuesped, Pageable pagina);

	/**
	 * Reservas de los alojamientos de un anfitrión, en cualquier estado
	 * (HU-25, CA-079). Nunca incluye las de alojamientos de otros.
	 *
	 * @param idAnfitrion anfitrión
	 * @param pagina      página y orden
	 * @return página de reservas
	 */
	@Query(value = "select r from Reserva r where r.idAlojamiento in "
			+ "(select a.idAlojamiento from Alojamiento a where a.idAnfitrion = :idAnfitrion)",
			countQuery = "select count(r) from Reserva r where r.idAlojamiento in "
					+ "(select a.idAlojamiento from Alojamiento a where a.idAnfitrion = :idAnfitrion)")
	Page<Reserva> buscarDeAnfitrion(@Param("idAnfitrion") Long idAnfitrion, Pageable pagina);

	/**
	 * Lee la reserva con bloqueo de escritura para cancelarla (HU-01). Así dos
	 * cancelaciones que llegan al mismo tiempo se procesan una después de la
	 * otra, y la segunda ya la ve CANCELADA.
	 *
	 * @param id reserva a bloquear
	 * @return la reserva, si existe
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reserva r where r.idReserva = :id")
	Optional<Reserva> buscarParaCancelar(@Param("id") Long id);
}
