package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;

/**
 * Acceso a los usuarios. El correo se guarda cifrado con un IV aleatorio, así
 * que las búsquedas usan {@code correoHash}, el HMAC del correo normalizado.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/**
	 * Busca un usuario por el hash de su correo.
	 *
	 * @param correoHash HMAC del correo normalizado
	 * @return el usuario, si existe
	 */
	Optional<Usuario> findByCorreoHash(String correoHash);

	/**
	 * Indica si ya existe un usuario con ese correo.
	 *
	 * @param correoHash HMAC del correo normalizado
	 * @return {@code true} si existe
	 */
	boolean existsByCorreoHash(String correoHash);
}
