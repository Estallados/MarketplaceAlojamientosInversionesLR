package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
