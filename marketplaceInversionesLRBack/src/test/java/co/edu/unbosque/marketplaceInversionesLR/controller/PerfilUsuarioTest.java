package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-09 Consultar y actualizar perfil sobre la API completa y un
 * PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class PerfilUsuarioTest {

	/** Ruta del perfil propio. */
	private static final String PERFIL = "/usuarios/perfil";

	/** Mensaje de datos inválidos. */
	private static final String DATOS_INVALIDOS = "Envía un nombre no vacío (máximo 255 caracteres) y/o un teléfono "
			+ "de 7 a 15 dígitos.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AuditoriaRepository auditoriaRepository;

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
		ana = crear("ana@correo.com", "Ana", Role.ANFITRION);
		token = jwtUtil.generateToken(ana);
	}

	/** Borra los usuarios para no afectar a las demás pruebas. */
	@AfterEach
	void limpiar() {
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-042 Con sesión iniciada consulta su perfil y ve la información básica de su cuenta")
	void consultaElPerfil_CA042() throws Exception {
		consultar(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idUsuario").value(ana.getIdUsuario()))
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.correo").value("ana@correo.com"))
				.andExpect(jsonPath("$.telefono").value("3001234567"))
				.andExpect(jsonPath("$.rol").value("ANFITRION"))
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andExpect(jsonPath("$.correoVerificado").value(true))
				.andExpect(jsonPath("$.fechaRegistro").isNotEmpty())
				// Nunca salen la contraseña, el hash del correo ni el secreto del segundo factor
				.andExpect(jsonPath("$.contrasenia").doesNotExist())
				.andExpect(jsonPath("$.correoHash").doesNotExist())
				.andExpect(jsonPath("$.secretoTotp").doesNotExist());
	}

	@Test
	@DisplayName("CA-044 Sin iniciar sesión no puede consultar ni modificar el perfil")
	void sinSesionNoAccede_CA044() throws Exception {
		mockMvc.perform(get(PERFIL))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("No autenticado: token ausente, inválido o expirado"));
		mockMvc.perform(get(PERFIL).header("Authorization", "Bearer token.falso.123"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(put(PERFIL).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otro\"}"))
				.andExpect(status().isUnauthorized());

		assertThat(usuarioRepository.findById(ana.getIdUsuario()).orElseThrow().getNombre()).isEqualTo("Ana");
	}

	@Test
	@DisplayName("CA-046 Al modificar datos válidos se actualiza el perfil y se ve la información nueva al consultarlo")
	void actualizaElPerfil_CA046() throws Exception {
		actualizar(token, "{\"nombre\":\"Ana María\",\"telefono\":\"+57 310 987 6543\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Perfil actualizado correctamente."));

		consultar(token)
				.andExpect(jsonPath("$.nombre").value("Ana María"))
				.andExpect(jsonPath("$.telefono").value("+573109876543"));

		assertThat(auditoriaRepository.findByAccionOrderByFechaDesc(AuditoriaService.PERFIL_ACTUALIZADO))
				.anySatisfy(evento -> {
					assertThat(evento.getIdUsuario()).isEqualTo(ana.getIdUsuario());
					assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
				});
	}

	@Test
	@DisplayName("CA-047 Con datos inválidos o incompletos se informa el error y no se guardan los cambios")
	void datosInvalidos_CA047() throws Exception {
		actualizar(token, "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(DATOS_INVALIDOS));
		actualizar(token, "{\"nombre\":\"  \"}").andExpect(status().isBadRequest());
		actualizar(token, "{\"telefono\":\"123\"}").andExpect(status().isBadRequest());
		actualizar(token, "{\"nombre\":\"" + "x".repeat(256) + "\"}").andExpect(status().isBadRequest());
		actualizar(token, "no es json").andExpect(status().isBadRequest());

		consultar(token)
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.telefono").value("3001234567"));
	}

	@Test
	@DisplayName("CA-049 Un usuario autenticado no puede modificar otra cuenta: solo cambia la suya")
	void noModificaOtraCuenta_CA049() throws Exception {
		Usuario luis = crear("luis@correo.com", "Luis", Role.HUESPED);

		// Aunque envíe el id, el correo y el rol de otra cuenta, solo se cambia la del token
		actualizar(token, """
				{"idUsuario":%d,"correo":"luis@correo.com","rol":"ADMIN","estado":"RESTRINGIDO",
				 "nombre":"Nombre cambiado"}
				""".formatted(luis.getIdUsuario())).andExpect(status().isOk());

		Usuario luisDespues = usuarioRepository.findById(luis.getIdUsuario()).orElseThrow();
		assertThat(luisDespues.getNombre()).isEqualTo("Luis");
		assertThat(luisDespues.getRol()).isEqualTo(Role.HUESPED);
		Usuario anaDespues = usuarioRepository.findById(ana.getIdUsuario()).orElseThrow();
		assertThat(anaDespues.getNombre()).isEqualTo("Nombre cambiado");
		assertThat(anaDespues.getRol()).isEqualTo(Role.ANFITRION);
		assertThat(anaDespues.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(AESUtil.decrypt(anaDespues.getCorreo())).isEqualTo("ana@correo.com");
	}

	@Test
	@DisplayName("Si la cuenta del token ya no existe, el token deja de servir")
	void cuentaEliminada() throws Exception {
		usuarioRepository.deleteAll();

		mockMvc.perform(get(PERFIL).header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("La documentación de Swagger incluye la consulta y la actualización del perfil")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/usuarios/perfil'].get").exists())
				.andExpect(jsonPath("$.paths['/usuarios/perfil'].put.responses['400']").exists());
	}

	/**
	 * Consulta el perfil.
	 *
	 * @param jwt token del usuario
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions consultar(String jwt) throws Exception {
		return mockMvc.perform(get(PERFIL).header("Authorization", "Bearer " + jwt));
	}

	/**
	 * Actualiza el perfil.
	 *
	 * @param jwt    token del usuario
	 * @param cuerpo JSON con los cambios
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions actualizar(String jwt, String cuerpo) throws Exception {
		return mockMvc.perform(put(PERFIL).header("Authorization", "Bearer " + jwt)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
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
		usuario.setContrasenia("no-se-usa");
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
