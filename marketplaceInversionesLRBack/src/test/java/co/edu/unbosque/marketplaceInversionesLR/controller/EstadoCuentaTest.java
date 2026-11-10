package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-10 Activar o desactivar cuenta sobre la API completa y un
 * PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class EstadoCuentaTest {

	/** Ruta para desactivar la cuenta propia. */
	private static final String DESACTIVAR = "/usuarios/perfil/desactivar";

	/** Ruta del perfil propio. */
	private static final String PERFIL = "/usuarios/perfil";

	/** Correo de la cuenta que inicia sesión. */
	private static final String CORREO = "ana@correo.com";

	/** Contraseña de las cuentas de prueba. */
	private static final String CLAVE = "Segura#2026";

	/** Cabecera de autenticación. */
	private static final String AUTORIZACION = "Authorization";

	/** Prefijo del token en la cabecera. */
	private static final String BEARER = "Bearer ";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Cuenta que inicia sesión. */
	private Usuario ana;

	/** Token de la cuenta que inicia sesión. */
	private String token;

	@BeforeEach
	void preparar() {
		limpiar();
		ana = crear(CORREO, "Ana", Role.ANFITRION);
		token = jwtUtil.generateToken(ana);
	}

	/** Borra los usuarios para no afectar a las demás pruebas. */
	@AfterEach
	void limpiar() {
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-050 La cuenta pasa de ACTIVA a INACTIVA y de INACTIVA a ACTIVA, y se muestra el nuevo estado")
	void cambiaAlEstadoContrario_CA050() throws Exception {
		desactivar(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Cuenta desactivada correctamente."))
				.andExpect(jsonPath("$.estado").value("INACTIVO"));
		assertThat(estadoDe(ana)).isEqualTo(EstadoUsuario.INACTIVO);

		String nuevoToken = reactivar(CORREO, CLAVE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Cuenta reactivada correctamente."))
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.rol").value("ANFITRION"))
				.andExpect(jsonPath("$.correo").value(CORREO))
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andReturn().getResponse().getContentAsString()
				.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get(PERFIL).header(AUTORIZACION, BEARER + nuevoToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVO"));
	}

	@Test
	@DisplayName("CA-053 Un usuario autenticado no puede cambiar el estado de otra cuenta: solo cambia la suya")
	void noCambiaOtraCuenta_CA053() throws Exception {
		Usuario luis = crear("luis@correo.com", "Luis", Role.HUESPED);

		// Aunque envíe el id y el correo de otra cuenta, solo se desactiva la del token
		mockMvc.perform(patch(DESACTIVAR).header(AUTORIZACION, BEARER + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idUsuario\":%d,\"correo\":\"luis@correo.com\"}".formatted(luis.getIdUsuario())))
				.andExpect(status().isOk());

		assertThat(estadoDe(luis)).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(estadoDe(ana)).isEqualTo(EstadoUsuario.INACTIVO);
	}

	@Test
	@DisplayName("Con la cuenta desactivada el token deja de servir y no puede iniciar sesión")
	void cuentaDesactivadaSinAcceso() throws Exception {
		desactivar(token).andExpect(status().isOk());

		mockMvc.perform(get(PERFIL).header(AUTORIZACION, BEARER + token))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(credenciales(CORREO, CLAVE)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("La cuenta está restringida o desactivada."));
	}

	@Test
	@DisplayName("Sin iniciar sesión no puede desactivar ninguna cuenta")
	void sinSesionNoDesactiva() throws Exception {
		mockMvc.perform(patch(DESACTIVAR)).andExpect(status().isUnauthorized());

		assertThat(estadoDe(ana)).isEqualTo(EstadoUsuario.ACTIVO);
	}

	@Test
	@DisplayName("Un administrador no puede desactivar su cuenta")
	void administradorNoSeDesactiva() throws Exception {
		Usuario admin = crear("admin@correo.com", "Admin", Role.ADMIN);

		desactivar(jwtUtil.generateToken(admin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Esta cuenta no se puede desactivar."));

		assertThat(estadoDe(admin)).isEqualTo(EstadoUsuario.ACTIVO);
	}

	@Test
	@DisplayName("Con segundo factor la cuenta se reactiva sin entregar token")
	void reactivarConSegundoFactor() throws Exception {
		ana.setEstado(EstadoUsuario.INACTIVO);
		ana.setTotpHabilitado(true);
		usuarioRepository.save(ana);

		reactivar(CORREO, CLAVE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Cuenta reactivada correctamente. Inicia sesión con tu código."))
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andExpect(jsonPath("$.token").doesNotExist());

		assertThat(estadoDe(ana)).isEqualTo(EstadoUsuario.ACTIVO);
	}

	@Test
	@DisplayName("Una cuenta restringida por un administrador no puede reactivarse sola")
	void restringidaNoSeReactiva() throws Exception {
		ana.setEstado(EstadoUsuario.RESTRINGIDO);
		usuarioRepository.save(ana);

		reactivar(CORREO, CLAVE)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message")
						.value("La cuenta fue restringida por un administrador y no puede reactivarse."));

		assertThat(estadoDe(ana)).isEqualTo(EstadoUsuario.RESTRINGIDO);
	}

	@Test
	@DisplayName("Reactivar responde el error que corresponde según las credenciales y el estado")
	void erroresAlReactivar() throws Exception {
		reactivar(CORREO, CLAVE).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("La cuenta ya está activa."));
		reactivar(CORREO, "Otra#2026").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Correo o contraseña inválidos."));
		mockMvc.perform(post("/auth/reactivar").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Debes enviar el correo y la contraseña."));
	}

	@Test
	@DisplayName("La documentación de Swagger incluye desactivar y reactivar la cuenta")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/usuarios/perfil/desactivar'].patch.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/auth/reactivar'].post.responses['409']").exists());
	}

	/**
	 * Desactiva la cuenta del token.
	 *
	 * @param jwt token del usuario
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions desactivar(String jwt) throws Exception {
		return mockMvc.perform(patch(DESACTIVAR).header(AUTORIZACION, BEARER + jwt));
	}

	/**
	 * Pide reactivar una cuenta.
	 *
	 * @param correo      correo de la cuenta
	 * @param contrasenia contraseña enviada
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions reactivar(String correo, String contrasenia) throws Exception {
		return mockMvc.perform(post("/auth/reactivar").contentType(MediaType.APPLICATION_JSON)
				.content(credenciales(correo, contrasenia)));
	}

	/**
	 * Arma el JSON de credenciales.
	 *
	 * @param correo      correo
	 * @param contrasenia contraseña
	 * @return cuerpo de la petición
	 */
	private static String credenciales(String correo, String contrasenia) {
		return "{\"correo\":\"%s\",\"contrasenia\":\"%s\"}".formatted(correo, contrasenia);
	}

	/**
	 * Lee de la base de datos el estado de una cuenta.
	 *
	 * @param usuario cuenta
	 * @return estado guardado
	 */
	private EstadoUsuario estadoDe(Usuario usuario) {
		return usuarioRepository.findById(usuario.getIdUsuario()).orElseThrow().getEstado();
	}

	/**
	 * Guarda un usuario activo y verificado.
	 *
	 * @param correo correo de la cuenta
	 * @param nombre nombre
	 * @param rol    rol de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crear(String correo, String nombre, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setTelefono("3001234567");
		usuario.setContrasenia(passwordEncoder.encode(CLAVE));
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
