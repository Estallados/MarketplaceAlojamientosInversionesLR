package co.edu.unbosque.marketplaceInversionesLR.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;

/**
 * Pruebas de la carga de usuarios por correo.
 */
class UserDetailsServiceImplTest {

	/** Repositorio simulado. */
	private final UsuarioRepository repositorio = mock(UsuarioRepository.class);

	/** Servicio bajo prueba. */
	private final UserDetailsServiceImpl servicio = new UserDetailsServiceImpl(repositorio);

	@Test
	@DisplayName("Busca el usuario por el hash del correo")
	void cargaPorHashDelCorreo() {
		Usuario usuario = new Usuario();
		when(repositorio.findByCorreoHash(UsuarioService.hashCorreo("ana@correo.com"))).thenReturn(Optional.of(usuario));

		assertThat(servicio.loadUserByUsername("ana@correo.com")).isSameAs(usuario);
	}

	@Test
	@DisplayName("Si el correo no existe o viene vacío lanza UsernameNotFoundException")
	void usuarioNoEncontrado() {
		when(repositorio.findByCorreoHash(UsuarioService.hashCorreo("nadie@correo.com"))).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.loadUserByUsername("nadie@correo.com"))
				.isInstanceOf(UsernameNotFoundException.class);
		assertThatThrownBy(() -> servicio.loadUserByUsername(" ")).isInstanceOf(UsernameNotFoundException.class);
		assertThatThrownBy(() -> servicio.loadUserByUsername(null)).isInstanceOf(UsernameNotFoundException.class);
	}
}
