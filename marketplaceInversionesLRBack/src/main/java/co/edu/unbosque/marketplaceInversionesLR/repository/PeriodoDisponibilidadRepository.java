package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;

/**
 * Acceso a los períodos de disponibilidad de los alojamientos (HU-20).
 */
public interface PeriodoDisponibilidadRepository extends JpaRepository<PeriodoDisponibilidad, Long> {

	/**
	 * Períodos de un alojamiento, ordenados por fecha de inicio.
	 *
	 * @param idAlojamiento alojamiento a consultar
	 * @return lista de períodos
	 */
	List<PeriodoDisponibilidad> findByIdAlojamientoOrderByFechaInicio(Long idAlojamiento);

	/**
	 * Indica si el alojamiento tiene algún período que termine después de una
	 * fecha, es decir, con disponibilidad futura.
	 *
	 * @param idAlojamiento alojamiento
	 * @param fecha         fecha de referencia (normalmente hoy)
	 * @return {@code true} si hay disponibilidad futura
	 */
	boolean existsByIdAlojamientoAndFechaFinAfter(Long idAlojamiento, LocalDate fecha);

	/**
	 * Indica si algún período del alojamiento se cruza con [inicio, fin]. Las
	 * dos fechas cuentan, igual que en la restricción
	 * {@code ex_periodo_sin_cruces} de Neon: un período que empieza el día en
	 * que termina otro también se cruza.
	 *
	 * @param idAlojamiento alojamiento
	 * @param inicio        inicio del período nuevo
	 * @param fin           fin del período nuevo
	 * @return {@code true} si hay cruce
	 */
	@Query("select case when count(p) > 0 then true else false end from PeriodoDisponibilidad p "
			+ "where p.idAlojamiento = :id and p.fechaInicio <= :fin and p.fechaFin >= :inicio")
	boolean existeCruce(@Param("id") Long idAlojamiento, @Param("inicio") LocalDate inicio,
			@Param("fin") LocalDate fin);

	/**
	 * Igual que {@link #existeCruce(Long, LocalDate, LocalDate)}, sin contar el
	 * período que se está modificando.
	 *
	 * @param idAlojamiento alojamiento
	 * @param inicio        inicio nuevo del período
	 * @param fin           fin nuevo del período
	 * @param excluir       período que se está modificando
	 * @return {@code true} si hay cruce con otro período
	 */
	@Query("select case when count(p) > 0 then true else false end from PeriodoDisponibilidad p "
			+ "where p.idAlojamiento = :id and p.fechaInicio <= :fin and p.fechaFin >= :inicio "
			+ "and p.idPeriodo <> :excluir")
	boolean existeCruceConOtro(@Param("id") Long idAlojamiento, @Param("inicio") LocalDate inicio,
			@Param("fin") LocalDate fin, @Param("excluir") Long excluir);
}
