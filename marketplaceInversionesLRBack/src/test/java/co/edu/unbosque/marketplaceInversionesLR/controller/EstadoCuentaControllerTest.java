package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.AuthService;
import co.edu.unbosque.marketplaceInversionesLR.service.InicioGoogleService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.VerificacionCorreoService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas unitarias de las rutas de HU-10, con los servicios simulados. El
 * flujo completo está en {@link EstadoCuentaTest}.
 */
@ExtendWith(MockitoExtension.class)
class EstadoCuentaControllerTest {

	/** Correo de la cuenta del token. */
	private static final String CORREO = "ana@correo.com";

	/** Contraseña enviada al reactivar. */
	private static final String CLAVE = "Segura#2026";

	/** Clave del mensaje en las respuestas. */
	private static final String MENSAJE = "message";

	/** Clave del estado en las respuestas. */
	private static final String ESTADO = "estado";

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private VerificacionCorreoService verificacionCorreoService;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private UserDetailsService userDetailsService;

	@Mock
	private AuditoriaService auditoriaService;


	@Mock
	private InicioGoogleService inicioGoogleService;

	@Mock
	private AuthService authService;

	private UsuarioController usuarioController;

	private AuthController authController;

	/** Autenticación de la cuenta del token. */
	private Authentication autenticacion;

	/** Datos enviados al reactivar. */
	private UsuarioDTO credenciales;

	@BeforeEach
	void preparar() {
		usuarioController = new UsuarioController(usuarioService);
		authController = new AuthController(authenticationManager, usuarioService, verificacionCorreoService, jwtUtil,
				userDetailsService, auditoriaService, inicioGoogleService, authService);
		autenticacion = new UsernamePasswordAuthenticationToken(CORREO, null, List.of());
		credenciales = new UsuarioDTO();
		credenciales.setCorreo(" ANA@correo.com ");
		credenciales.setContrasenia(CLAVE);
	}

	@Test
	@DisplayName("CA-050 Al desactivar responde con el nuevo estado INACTIVO")
	void desactivaYMuestraElEstado_CA050() {
		when(usuarioService.desactivarCuenta(CORREO)).thenReturn(0);

		ResponseEntity<Object> respuesta = usuarioController.desactivarCuenta(autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isEqualTo(Map.of(MENSAJE, "Cuenta desactivada correctamente.", ESTADO, "INACTIVO"));
	}

	@Test
	@DisplayName("CA-053 Desactivar usa siempre el correo del token")
	void desactivaLaCuentaDelToken_CA053() {
		when(usuarioService.desactivarCuenta(CORREO)).thenReturn(0);

		usuarioController.desactivarCuenta(autenticacion);

		verify(usuarioService).desactivarCuenta(CORREO);
	}

	@Test
	@DisplayName("Desactivar informa si la cuenta no existe o no se puede desactivar")
	void erroresAlDesactivar() {
		when(usuarioService.desactivarCuenta(CORREO)).thenReturn(1, 2);

		assertRespuesta(usuarioController.desactivarCuenta(autenticacion), HttpStatus.NOT_FOUND, "Usuario no encontrado.");
		assertRespuesta(usuarioController.desactivarCuenta(autenticacion), HttpStatus.FORBIDDEN,
				"Esta cuenta no se puede desactivar.");
	}

	@Test
	@DisplayName("CA-050 Al reactivar entrega la sesión y el nuevo estado ACTIVO")
	void reactivaYMuestraElEstado_CA050() {
		when(authService.reactivarCuenta(credenciales.getCorreo(), CLAVE)).thenReturn(0);
		Usuario usuario = cuenta(false);
		when(userDetailsService.loadUserByUsername(CORREO)).thenReturn(usuario);
		when(jwtUtil.generateToken(usuario)).thenReturn("token-nuevo");

		ResponseEntity<Object> respuesta = authController.reactivar(credenciales);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isEqualTo(Map.of(MENSAJE, "Cuenta reactivada correctamente.", "token",
				"token-nuevo", "rol", "HUESPED", "correo", CORREO, ESTADO, "ACTIVO"));
	}

	@Test
	@DisplayName("CA-050 Con segundo factor reactiva y muestra el estado, pero no entrega token")
	void reactivaConSegundoFactor_CA050() {
		when(authService.reactivarCuenta(any(), any())).thenReturn(0);
		when(userDetailsService.loadUserByUsername(CORREO)).thenReturn(cuenta(true));

		ResponseEntity<Object> respuesta = authController.reactivar(credenciales);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody())
				.isEqualTo(Map.of(MENSAJE, "Cuenta reactivada correctamente. Inicia sesión con tu código.", ESTADO, "ACTIVO"));
	}

	@Test
	@DisplayName("Reactivar traduce cada resultado del servicio a su respuesta")
	void erroresAlReactivar() {
		when(authService.reactivarCuenta(any(), any())).thenReturn(1, 2, 3, 4);

		assertRespuesta(authController.reactivar(credenciales), HttpStatus.UNAUTHORIZED, "Correo o contraseña inválidos.");
		assertRespuesta(authController.reactivar(credenciales), HttpStatus.BAD_REQUEST,
				"Debes enviar el correo y la contraseña.");
		assertRespuesta(authController.reactivar(credenciales), HttpStatus.FORBIDDEN,
				"La cuenta fue restringida por un administrador y no puede reactivarse.");
		assertRespuesta(authController.reactivar(credenciales), HttpStatus.CONFLICT, "La cuenta ya está activa.");
	}

	/**
	 * Comprueba el estado HTTP y el mensaje de una respuesta.
	 *
	 * @param respuesta respuesta del controlador
	 * @param estado    estado esperado
	 * @param texto     mensaje esperado
	 */
	private static void assertRespuesta(ResponseEntity<Object> respuesta, HttpStatus estado, String texto) {
		assertThat(respuesta.getStatusCode()).isEqualTo(estado);
		assertThat(respuesta.getBody()).isEqualTo(Map.of(MENSAJE, texto));
	}

	/**
	 * Crea la cuenta que se reactiva.
	 *
	 * @param segundoFactor si tiene habilitado el segundo factor
	 * @return cuenta de prueba
	 */
	private static Usuario cuenta(boolean segundoFactor) {
		Usuario usuario = new Usuario();
		usuario.setCorreo(AESUtil.encrypt(CORREO));
		usuario.setRol(Role.HUESPED);
		usuario.setTotpHabilitado(segundoFactor);
		return usuario;
	}
}
