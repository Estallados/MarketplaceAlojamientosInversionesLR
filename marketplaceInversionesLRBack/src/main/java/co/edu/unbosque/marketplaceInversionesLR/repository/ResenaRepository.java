package co.edu.unbosque.marketplaceInversionesLR.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;

/**
 * Acceso a las reseñas. Por ahora solo las consultas de la calificación de un
 * alojamiento.
 */
public interface ResenaRepository extends JpaRepository<Resena, Long> {

	/**
	 * Promedio de las calificaciones de un alojamiento en un estado.
	 *
	 * @param idAlojamiento alojamiento
	 * @param estado        estado de las reseñas que cuentan
	 * @return el promedio, o {@code null} si no tiene reseñas
	 */
	@Query("select avg(r.calificacion) from Resena r where r.idAlojamiento = :id and r.estado = :estado")
	Double promedio(@Param("id") Long idAlojamiento, @Param("estado") EstadoResena estado);

	/**
	 * Cantidad de reseñas de un alojamiento en un estado.
	 *
	 * @param idAlojamiento alojamiento
	 * @param estado        estado de las reseñas que cuentan
	 * @return la cantidad
	 */
	long countByIdAlojamientoAndEstado(Long idAlojamiento, EstadoResena estado);

	/**
	 * Indica si una reserva ya tiene reseña (hay una por reserva).
	 *
	 * @param idReserva reserva
	 * @return {@code true} si el huésped ya la reseñó
	 */
	boolean existsByIdReserva(Long idReserva);
}
