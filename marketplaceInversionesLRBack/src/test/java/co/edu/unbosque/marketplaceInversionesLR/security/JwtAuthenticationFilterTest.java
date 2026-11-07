package co.edu.unbosque.marketplaceInversionesLR.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas del filtro que autentica las peticiones con el JWT (HU-07).
 */
class JwtAuthenticationFilterTest {

	private final JwtUtil jwtUtil = new JwtUtil(3_600_000);

	private final UserDetailsService userDetailsService = mock(UserDetailsService.class);

	private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwtUtil, userDetailsService);

	@AfterEach
	void limpiarContexto() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("CA-037 Sin token, con un token alterado o vencido la petición sigue sin autenticar")
	void sinTokenValidoNoAutentica_CA037() throws Exception {
		Usuario ana = usuario("ana@correo.com", EstadoUsuario.ACTIVO);
		when(userDetailsService.loadUserByUsername("ana@correo.com")).thenReturn(ana);

		assertThat(filtrar("/usuarios/perfil", null)).isNull();
		assertThat(filtrar("/usuarios/perfil", "Basic abc")).isNull();
		assertThat(filtrar("/usuarios/perfil", "Bearer token.falso.123")).isNull();
		assertThat(filtrar("/usuarios/perfil", "Bearer " + jwtUtil.generateToken(ana) + "x")).isNull();
		assertThat(filtrar("/usuarios/perfil", "Bearer " + new JwtUtil(-1000).generateToken(ana))).isNull();
	}

	@Test
	@DisplayName("CA-037 Un token de una cuenta restringida, desactivada o que ya no existe no autentica")
	void cuentaNoActivaNoAutentica_CA037() throws Exception {
		Usuario restringida = usuario("ana@correo.com", EstadoUsuario.RESTRINGIDO);
		Usuario inactiva = usuario("luis@correo.com", EstadoUsuario.INACTIVO);
		Usuario borrada = usuario("sara@correo.com", EstadoUsuario.ACTIVO);
		when(userDetailsService.loadUserByUsername("ana@correo.com")).thenReturn(restringida);
		when(userDetailsService.loadUserByUsername("luis@correo.com")).thenReturn(inactiva);
		when(userDetailsService.loadUserByUsername("sara@correo.com"))
				.thenThrow(new UsernameNotFoundException("Usuario no encontrado"));

		assertThat(filtrar("/usuarios/perfil", "Bearer " + jwtUtil.generateToken(restringida))).isNull();
		assertThat(filtrar("/usuarios/perfil", "Bearer " + jwtUtil.generateToken(inactiva))).isNull();
		assertThat(filtrar("/usuarios/perfil", "Bearer " + jwtUtil.generateToken(borrada))).isNull();
	}

	@Test
	@DisplayName("CA-034 Con un token válido autentica la petición con el rol del usuario")
	void tokenValidoAutenticaConElRol_CA034() throws Exception {
		Usuario ana = usuario("ana@correo.com", EstadoUsuario.ACTIVO);
		when(userDetailsService.loadUserByUsername("ana@correo.com")).thenReturn(ana);

		Authentication autenticacion = filtrar("/usuarios/perfil", "Bearer " + jwtUtil.generateToken(ana));

		assertThat(autenticacion).isNotNull();
		assertThat(autenticacion.getPrincipal()).isSameAs(ana);
		assertThat(autenticacion.getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_ANFITRION");
	}

	@Test
	@DisplayName("Las rutas de /auth no leen el token")
	void rutasDeAuthNoSeFiltran() throws Exception {
		assertThat(filtrar("/auth/login", "Bearer token.falso.123")).isNull();

		verify(userDetailsService, never()).loadUserByUsername(anyString());
	}

	/**
	 * Pasa una petición por el filtro.
	 *
	 * @param ruta          ruta de la petición
	 * @param authorization encabezado Authorization, o {@code null}
	 * @return la autenticación que dejó el filtro, o {@code null}
	 * @throws Exception si falla el filtro
	 */
	private Authentication filtrar(String ruta, String authorization) throws Exception {
		SecurityContextHolder.clearContext();
		MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
		if (authorization != null) {
			peticion.addHeader("Authorization", authorization);
		}
		MockFilterChain cadena = new MockFilterChain();
		filtro.doFilter(peticion, new MockHttpServletResponse(), cadena);
		assertThat(cadena.getRequest()).as("la petición sigue por la cadena").isNotNull();
		return SecurityContextHolder.getContext().getAuthentication();
	}

	/**
	 * Arma un anfitrión con el correo verificado.
	 *
	 * @return usuario
	 */
	private static Usuario usuario(String correo, EstadoUsuario estado) {
		Usuario usuario = new Usuario();
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setContrasenia("x");
		usuario.setRol(Role.ANFITRION);
		usuario.setEstado(estado);
		usuario.setCorreoVerificado(true);
		return usuario;
	}
}
