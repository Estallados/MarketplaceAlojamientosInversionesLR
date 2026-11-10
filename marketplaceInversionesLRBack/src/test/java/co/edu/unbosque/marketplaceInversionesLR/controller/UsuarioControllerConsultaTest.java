package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;

/**
 * Pruebas unitarias de la consulta de usuarios (HU-12): un usuario solo se
 * consulta a sí mismo; el administrador consulta a cualquiera.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioControllerConsultaTest {

	@Mock
	private UsuarioService usuarioService;

	private UsuarioController controlador;

	@BeforeEach
	void preparar() {
		controlador = new UsuarioController(usuarioService);
	}

	@Test
	@DisplayName("CA-058 El administrador consulta a cualquier usuario por id")
	void adminConsultaCualquiera_CA058() {
		UsuarioDTO otro = new UsuarioDTO();
		when(usuarioService.obtenerPorId(9L)).thenReturn(otro);

		ResponseEntity<Object> respuesta = controlador.obtenerUsuarioPorId(9L, sesion(1L, "ROLE_ADMIN"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(otro);
	}

	@Test
	@DisplayName("Un usuario solo consulta su propio id; con otro responde 403 sin consultar")
	void soloElPropio() {
		UsuarioDTO propio = new UsuarioDTO();
		when(usuarioService.obtenerPorId(5L)).thenReturn(propio);

		assertThat(controlador.obtenerUsuarioPorId(5L, sesion(5L, "ROLE_HUESPED")).getBody()).isSameAs(propio);
		ResponseEntity<Object> ajeno = controlador.obtenerUsuarioPorId(9L, sesion(5L, "ROLE_HUESPED"));

		assertThat(ajeno.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(ajeno.getBody()).isEqualTo(Map.of("message", UsuarioController.SIN_PERMISO_CONSULTA));
		verify(usuarioService, never()).obtenerPorId(9L);
	}

	@Test
	@DisplayName("Consultar un id que no existe responde 404")
	void noExiste() {
		when(usuarioService.obtenerPorId(99L)).thenReturn(null);

		ResponseEntity<Object> respuesta = controlador.obtenerUsuarioPorId(99L, sesion(1L, "ROLE_ADMIN"));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", UsuarioService.NO_ENCONTRADO));
	}

	@Test
	@DisplayName("Buscar por correo: 200, 404 si no existe y 400 si falta el correo")
	void porCorreo() {
		UsuarioDTO ana = new UsuarioDTO();
		when(usuarioService.obtenerUsuarioPorCorreo(" Ana@Correo.com ")).thenReturn(ana);
		when(usuarioService.obtenerUsuarioPorCorreo("nadie@correo.com")).thenReturn(null);

		assertThat(controlador.obtenerUsuarioPorCorreo(" Ana@Correo.com ").getBody()).isSameAs(ana);
		assertThat(controlador.obtenerUsuarioPorCorreo("nadie@correo.com").getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
		ResponseEntity<Object> sinCorreo = controlador.obtenerUsuarioPorCorreo("  ");
		assertThat(sinCorreo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(sinCorreo.getBody()).isEqualTo(Map.of("message", UsuarioController.FALTA_CORREO));
		assertThat(controlador.obtenerUsuarioPorCorreo(null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("Listar devuelve todos los usuarios del servicio")
	void listar() {
		List<UsuarioDTO> todos = List.of(new UsuarioDTO(), new UsuarioDTO());
		when(usuarioService.listarTodos()).thenReturn(todos);

		assertThat(controlador.listarTodos().getBody()).isSameAs(todos);
	}

	/**
	 * Sesión de un usuario con el rol indicado.
	 *
	 * @param id  id del usuario
	 * @param rol autoridad, por ejemplo ROLE_ADMIN
	 * @return autenticación
	 */
	private static Authentication sesion(Long id, String rol) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(id);
		return new UsernamePasswordAuthenticationToken(usuario, null, List.of(new SimpleGrantedAuthority(rol)));
	}
}
