package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.CodigoVerificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de la creación de administradores ({@code POST
 * /usuarios/crearadministrador}) sobre la API completa y un PostgreSQL temporal
 * en Docker: solo un administrador puede crear otro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class CrearAdministradorTest {

	/** Ruta para crear administradores. */
	private static final String RUTA = "/usuarios/crearadministrador";

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private CodigoVerificacionRepository codigoRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Token del administrador que crea. */
	private String tokenAdmin;

	@BeforeEach
	void preparar() {
		limpiar();
		tokenAdmin = jwtUtil.generateToken(crearUsuario("admin.principal@correo.com", Role.ADMIN));
		clearInvocations(mailSender);
	}

	/** Borra en orden de llaves foráneas: los códigos antes que los usuarios. */
	@AfterEach
	void limpiar() {
		codigoRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("Un administrador crea otro administrador que inicia sesión de inmediato, sin verificar el correo")
	void administradorCreaAdministrador() throws Exception {
		crear("Laura Admin", "Laura@Correo.com", CONTRASENIA, "HUESPED", tokenAdmin)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value(UsuarioController.ADMIN_CREADO));

		Usuario nuevo = usuarioRepository.findByCorreoHash(UsuarioService.hashCorreo("laura@correo.com"))
				.orElseThrow();
		assertThat(nuevo.getRol()).isEqualTo(Role.ADMIN);
		assertThat(nuevo.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(nuevo.esCorreoVerificado()).isTrue();
		verify(mailSender, never()).send(any(SimpleMailMessage.class));

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"laura@correo.com\", \"contrasenia\": \"%s\"}".formatted(CONTRASENIA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("ADMIN"))
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	@DisplayName("Solo un administrador crea administradores: otro rol recibe 403 y sin token 401")
	void soloAdministrador() throws Exception {
		String tokenAnfitrion = jwtUtil.generateToken(crearUsuario("anfitrion@correo.com", Role.ANFITRION));
		String tokenHuesped = jwtUtil.generateToken(crearUsuario("huesped@correo.com", Role.HUESPED));

		crear("Intruso", "intruso@correo.com", CONTRASENIA, null, tokenAnfitrion).andExpect(status().isForbidden());
		crear("Intruso", "intruso@correo.com", CONTRASENIA, null, tokenHuesped).andExpect(status().isForbidden());
		mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		assertThat(usuarioRepository.findByCorreoHash(UsuarioService.hashCorreo("intruso@correo.com"))).isEmpty();
	}

	@Test
	@DisplayName("Crear un administrador valida los datos, la contraseña y el correo repetido")
	void validaciones() throws Exception {
		crear("Laura", "laura@correo.com", "debil", null, tokenAdmin)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(UsuarioController.CONTRASENIA_DEBIL));
		crear("", "laura@correo.com", CONTRASENIA, null, tokenAdmin)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(UsuarioController.DATOS_INVALIDOS));
		crear("Otro", "admin.principal@correo.com", CONTRASENIA, null, tokenAdmin)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(UsuarioController.CORREO_DUPLICADO));
	}

	private ResultActions crear(String nombre, String correo, String contrasenia, String rol, String token)
			throws Exception {
		String cuerpo = "{\"nombre\": \"%s\", \"correo\": \"%s\", \"contrasenia\": \"%s\"%s}".formatted(nombre, correo,
				contrasenia, rol == null ? "" : ", \"rol\": \"" + rol + "\"");
		return mockMvc.perform(post(RUTA).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}



	private Usuario crearUsuario(String correo, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("no-se-usa");
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
