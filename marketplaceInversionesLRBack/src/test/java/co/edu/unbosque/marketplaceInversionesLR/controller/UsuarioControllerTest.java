package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;

/**
 * Pruebas unitarias del controlador del perfil: cada resultado del servicio se
 * convierte en la respuesta HTTP esperada.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

	/** Sesión del usuario de prueba. */
	private static final Authentication SESION = new UsernamePasswordAuthenticationToken("ana@correo.com", null,
			List.of());

	@Mock
	private UsuarioService usuarioService;

	private UsuarioController controlador;

	@BeforeEach
	void preparar() {
		controlador = new UsuarioController(usuarioService);
	}

	@Test
	@DisplayName("Consultar responde el perfil, o 404 si la cuenta no existe")
	void consultar() {
		UsuarioDTO perfil = new UsuarioDTO();
		perfil.setNombre("Ana");
		when(usuarioService.obtenerUsuarioPorCorreo("ana@correo.com")).thenReturn(perfil, (UsuarioDTO) null);

		ResponseEntity<Object> encontrado = controlador.miPerfil(SESION);
		ResponseEntity<Object> noEncontrado = controlador.miPerfil(SESION);

		assertThat(encontrado.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(encontrado.getBody()).isSameAs(perfil);
		assertThat(noEncontrado.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(noEncontrado.getBody()).isEqualTo(Map.of("message", "Usuario no encontrado."));
	}

	@Test
	@DisplayName("Actualizar responde 200, 404 o 400 según el resultado del servicio")
	void actualizar() {
		UsuarioDTO cambios = new UsuarioDTO();
		cambios.setNombre("Ana María");
		cambios.setTelefono("3001234567");
		when(usuarioService.actualizarPerfil("ana@correo.com", "Ana María", "3001234567")).thenReturn(0, 1, 2);

		assertThat(controlador.actualizarPerfil(cambios, SESION).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(controlador.actualizarPerfil(cambios, SESION).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		ResponseEntity<Object> invalido = controlador.actualizarPerfil(cambios, SESION);
		assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(invalido.getBody()).isEqualTo(Map.of("message",
				"Envía un nombre no vacío (máximo 255 caracteres) y/o un teléfono de 7 a 15 dígitos."));
	}
}
