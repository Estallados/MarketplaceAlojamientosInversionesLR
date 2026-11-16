package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unbosque.marketplaceInversionesLR.entity.RegistroIdempotencia;

/**
 * Acceso a los registros de las claves {@code Idempotency-Key}.
 */
public interface RegistroIdempotenciaRepository extends JpaRepository<RegistroIdempotencia, Long> {

	/**
	 * Uso previo de una clave por el mismo usuario en la misma operación.
	 *
	 * @param idUsuario usuario
	 * @param operacion operación, por ejemplo CANCELAR_RESERVA
	 * @param clave     valor de la cabecera
	 * @return el registro, si la clave ya se usó
	 */
	Optional<RegistroIdempotencia> findByIdUsuarioAndOperacionAndClave(Long idUsuario, String operacion, String clave);

	/**
	 * Borra los registros creados antes del límite (usa el índice
	 * {@code idx_idempotencia_fecha}).
	 *
	 * @param limite fecha de creación más antigua que se conserva
	 * @return cantidad de registros borrados
	 */
	@Modifying
	@Query("delete from RegistroIdempotencia r where r.fechaCreacion < :limite")
	int borrarAnteriores(@Param("limite") LocalDateTime limite);
}
