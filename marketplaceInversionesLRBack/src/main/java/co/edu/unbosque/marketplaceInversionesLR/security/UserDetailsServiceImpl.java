package co.edu.unbosque.marketplaceInversionesLR.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;

/**
 * Carga los usuarios para Spring Security. Se busca por el hash del correo
 * normalizado, porque el correo cifrado cambia en cada cifrado.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

	/** Repositorio de usuarios. */
	private final UsuarioRepository userRepository;

	/**
	 * Crea el servicio.
	 *
	 * @param userRepository repositorio de usuarios
	 */
	public UserDetailsServiceImpl(UsuarioRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Carga un usuario por su correo.
	 *
	 * @param correo correo del usuario
	 * @return el usuario
	 * @throws UsernameNotFoundException si no existe
	 */
	@Override
	public UserDetails loadUserByUsername(String correo) {
		if (correo == null || correo.isBlank()) {
			throw new UsernameNotFoundException("Usuario no encontrado");
		}
		return userRepository.findByCorreoHash(UsuarioService.hashCorreo(correo))
				.orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
	}
}
