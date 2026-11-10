package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import org.springframework.security.core.userdetails.UserDetailsService;

import co.edu.unbosque.marketplaceInversionesLR.dto.InicioGoogleDTO;
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
 * Pruebas unitarias de las rutas de Google del controlador: cada resultado del
 * servicio se convierte en la respuesta HTTP esperada.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerGoogleTest {

	@Mock
	private AuthenticationManager authenticationManager;

	@Mock
	private UsuarioService usuarioService;

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

	private AuthController controlador;

	@BeforeEach
	void preparar() {
		controlador = new AuthController(authenticationManager, usuarioService, verificacionCorreoService, jwtUtil,
				userDetailsService, auditoriaService, inicioGoogleService, authService);
	}

	@Test
	@DisplayName("La configuración indica si Google está habilitado y su client ID")
	void configuracion() {
		when(inicioGoogleService.clientId()).thenReturn("cliente", (String) null);

		assertThat(cuerpo(controlador.configuracionGoogle())).containsEntry("habilitado", true)
				.containsEntry("clientId", "cliente");
		assertThat(cuerpo(controlador.configuracionGoogle())).containsEntry("habilitado", false)
				.containsEntry("clientId", null);
	}

	@Test
	@DisplayName("Sin idToken responde 400 sin llamar al servicio")
	void sinIdToken() {
		assertThat(controlador.google(datos(null)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(controlador.google(datos(" ")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(inicioGoogleService);
	}

	@Test
	@DisplayName("Cuenta nueva responde 201 y cuenta existente 200, con el token y el rol")
	void sesionIniciada() {
		Usuario usuario = new Usuario();
		usuario.setRol(Role.ANFITRION);
		usuario.setCorreo(AESUtil.encrypt("ana@gmail.com"));
		when(jwtUtil.generateToken(usuario)).thenReturn("jwt");

		resultado(new InicioGoogleService.Inicio(InicioGoogleService.INICIADA, usuario, true));
		ResponseEntity<Object> creada = controlador.google(datos("t"));
		assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(cuerpo(creada)).containsEntry("message", "Cuenta creada con Google.").containsEntry("token", "jwt")
				.containsEntry("rol", "ANFITRION").containsEntry("correo", "ana@gmail.com")
				.containsEntry("cuentaNueva", true);

		resultado(new InicioGoogleService.Inicio(InicioGoogleService.INICIADA, usuario, false));
		ResponseEntity<Object> iniciada = controlador.google(datos("t"));
		assertThat(iniciada.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cuerpo(iniciada)).containsEntry("message", "Sesión iniciada con Google.")
				.containsEntry("cuentaNueva", false);
	}

	@Test
	@DisplayName("Cada rechazo del servicio tiene su estado HTTP")
	void rechazos() {
		assertThat(estadoPara(InicioGoogleService.NO_CONFIGURADO)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(estadoPara(InicioGoogleService.CUENTA_RESTRINGIDA)).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(estadoPara(InicioGoogleService.ROL_NO_PERMITIDO)).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(estadoPara(InicioGoogleService.TOKEN_INVALIDO)).isEqualTo(HttpStatus.UNAUTHORIZED);

		resultado(new InicioGoogleService.Inicio(InicioGoogleService.REQUIERE_SEGUNDO_FACTOR, null, false));
		assertThat(cuerpo(controlador.google(datos("t")))).containsEntry("requiereSegundoFactor", true)
				.containsEntry("message", "Ingresa el código de tu aplicación autenticadora.");
		resultado(new InicioGoogleService.Inicio(InicioGoogleService.SEGUNDO_FACTOR_INVALIDO, null, false));
		assertThat(cuerpo(controlador.google(datos("t")))).containsEntry("requiereSegundoFactor", true)
				.containsEntry("message", "El código de verificación no es válido.");
	}

	/**
	 * Estado HTTP de la respuesta para un resultado del servicio.
	 *
	 * @param codigo resultado del servicio
	 * @return estado HTTP
	 */
	private HttpStatus estadoPara(int codigo) {
		resultado(new InicioGoogleService.Inicio(codigo, null, false));
		return (HttpStatus) controlador.google(datos("t")).getStatusCode();
	}

	/**
	 * Hace que el servicio devuelva el resultado indicado.
	 *
	 * @param inicio resultado
	 */
	private void resultado(InicioGoogleService.Inicio inicio) {
		when(inicioGoogleService.iniciar("t", Role.ANFITRION, "123456")).thenReturn(inicio);
	}

	/**
	 * Cuerpo de la petición con el token indicado.
	 *
	 * @param idToken token; puede ser null
	 * @return DTO de la petición
	 */
	private static InicioGoogleDTO datos(String idToken) {
		InicioGoogleDTO dto = new InicioGoogleDTO();
		dto.setIdToken(idToken);
		dto.setRol(Role.ANFITRION);
		dto.setCodigo("123456");
		return dto;
	}

	/**
	 * Cuerpo JSON de la respuesta.
	 *
	 * @param respuesta respuesta del controlador
	 * @return cuerpo como mapa
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> cuerpo(ResponseEntity<Object> respuesta) {
		return (Map<String, Object>) respuesta.getBody();
	}
}
