package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CodigoVerificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.TotpUtil;

/**
 * Pruebas de HU-07 Inicio de sesión sobre la API completa y un PostgreSQL
 * temporal en Docker: credenciales, código por correo o segundo factor, rol,
 * token y acceso a rutas protegidas. El servidor de correo se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class InicioSesionTest {

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

	/** Mensaje de credenciales inválidas. */
	private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña inválidos.";

	/** Mensaje del 401 de las rutas protegidas. */
	private static final String NO_AUTENTICADO = "No autenticado: token ausente, inválido o expirado";

	/** Ruta protegida de ejemplo (aún no implementada: con token válido responde 404, no 401). */
	private static final String RUTA_PROTEGIDA = "/usuarios/perfil";

	/** Código de 6 dígitos dentro del cuerpo del correo. */
	private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AuditoriaRepository auditoriaRepository;

	@Autowired
	private CodigoVerificacionRepository codigoRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	@BeforeEach
	void limpiar() {
		usuarioRepository.deleteAll();
		codigoRepository.deleteAll();
		clearInvocations(mailSender);
	}

	@Test
	@DisplayName("CA-034 Un usuario registrado inicia sesión con credenciales válidas y el sistema identifica su rol")
	void loginConCredencialesValidasIdentificaElRol_CA034() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\": \"Ana\", \"correo\": \"ana@correo.com\", \"contrasenia\": \"%s\", \"rol\": \"ANFITRION\"}"
						.formatted(CONTRASENIA)))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/auth/verificar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"ana@correo.com\", \"codigo\": \"%s\"}".formatted(codigoEnviado())))
				.andExpect(status().isOk());

		// Primer paso: credenciales válidas, se envía un código al correo
		clearInvocations(mailSender);
		login(" ANA@correo.com ", CONTRASENIA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereCodigoCorreo").value(true))
				.andExpect(jsonPath("$.metodoSegundoFactor").value("CORREO"))
				.andExpect(jsonPath("$.token").doesNotExist());

		// Segundo paso: con el código del correo se entrega el token y el rol
		String respuesta = login(" ANA@correo.com ", CONTRASENIA, codigoEnviado())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.rol").value("ANFITRION"))
				.andExpect(jsonPath("$.correo").value("ana@correo.com"))
				.andReturn().getResponse().getContentAsString();

		String token = JsonPath.read(respuesta, "$.token");
		Usuario ana = buscar("ana@correo.com");
		assertThat(jwtUtil.validateToken(token, ana)).isTrue();

		// Con el token pasa la seguridad: la ruta aún no existe, así que responde 404 y no 401
		mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound());

		assertThat(auditoriaRepository.findByAccionOrderByFechaDesc(AuditoriaService.LOGIN)).anySatisfy(evento -> {
			assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
			assertThat(evento.getIdUsuario()).isEqualTo(ana.getIdUsuario());
		});
	}

	@Test
	@DisplayName("CA-035 Con credenciales incorrectas el sistema informa que no son válidas, sin revelar si el correo existe")
	void loginConCredencialesIncorrectasSeRechaza_CA035() throws Exception {
		crear("luis@correo.com", Role.HUESPED, EstadoUsuario.ACTIVO, true);

		login("luis@correo.com", "Incorrecta#1")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(CREDENCIALES_INVALIDAS))
				.andExpect(jsonPath("$.token").doesNotExist());
		login("nadie@correo.com", CONTRASENIA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(CREDENCIALES_INVALIDAS));

		assertThat(auditoriaRepository.findByAccionOrderByFechaDesc(AuditoriaService.LOGIN))
				.filteredOn(evento -> evento.getResultado() == ResultadoAuditoria.RECHAZADO).hasSizeGreaterThanOrEqualTo(2)
				.allSatisfy(evento -> assertThat(evento.getDetalle()).doesNotContain("Incorrecta#1"));
	}

	@Test
	@DisplayName("CA-037 Sin iniciar sesión no se puede acceder a una función restringida")
	void sinSesionNoSeAccedeAFuncionesRestringidas_CA037() throws Exception {
		Usuario ana = crear("ana@correo.com", Role.HUESPED, EstadoUsuario.ACTIVO, true);

		mockMvc.perform(get(RUTA_PROTEGIDA))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(NO_AUTENTICADO));
		mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer token.falso.123"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(NO_AUTENTICADO));
		mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + new JwtUtil(-1000).generateToken(ana)))
				.andExpect(status().isUnauthorized());

		// Un token emitido antes de restringir la cuenta deja de servir
		String token = jwtUtil.generateToken(ana);
		ana.setEstado(EstadoUsuario.RESTRINGIDO);
		usuarioRepository.save(ana);
		mockMvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Con un código del correo incorrecto no se entrega el token y al quinto intento se bloquea el código")
	void codigoDelCorreoIncorrecto() throws Exception {
		crear("luis@correo.com", Role.HUESPED, EstadoUsuario.ACTIVO, true);
		login("luis@correo.com", CONTRASENIA).andExpect(status().isUnauthorized());
		String codigo = codigoEnviado();
		String incorrecto = codigo.equals("000000") ? "111111" : "000000";

		// Pedirlo otra vez antes de un minuto no envía otro correo
		login("luis@correo.com", CONTRASENIA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(
						"Ya te enviamos un código al correo hace menos de un minuto. Revisa tu bandeja de entrada."));

		for (int i = 0; i < 4; i++) {
			login("luis@correo.com", CONTRASENIA, incorrecto)
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.requiereCodigoCorreo").value(true))
					.andExpect(jsonPath("$.token").doesNotExist());
		}
		login("luis@correo.com", CONTRASENIA, incorrecto).andExpect(status().isTooManyRequests());
		login("luis@correo.com", CONTRASENIA, codigo).andExpect(status().isTooManyRequests());

		assertThat(auditoriaRepository.findByAccionOrderByFechaDesc(AuditoriaService.LOGIN))
				.anySatisfy(evento -> assertThat(evento.getDetalle()).isEqualTo("Código de inicio de sesión inválido"));
	}

	@Test
	@DisplayName("Con segundo factor activo se pide el código de la app autenticadora en lugar del correo")
	void segundoFactorEnElInicioDeSesion() throws Exception {
		Usuario ana = crear("ana@correo.com", Role.HUESPED, EstadoUsuario.ACTIVO, true);
		String secreto = TotpUtil.generarSecreto();
		ana.setSecretoTotp(AESUtil.encrypt(secreto));
		ana.setTotpHabilitado(true);
		usuarioRepository.save(ana);

		login("ana@correo.com", CONTRASENIA)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereSegundoFactor").value(true))
				.andExpect(jsonPath("$.requiereCodigoCorreo").doesNotExist());
		login("ana@correo.com", CONTRASENIA, "abcdef")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("El código de verificación no es válido."));
		login("ana@correo.com", CONTRASENIA, TotpUtil.codigo(secreto, Instant.now()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.rol").value("HUESPED"));
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	@DisplayName("El administrador inicia sesión sin código por correo")
	void administradorSinCodigoPorCorreo() throws Exception {
		crear("admin@correo.com", Role.ADMIN, EstadoUsuario.ACTIVO, true);

		login("admin@correo.com", CONTRASENIA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("ADMIN"));
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	@DisplayName("Con el correo sin verificar no se entrega el token y se pide verificarlo")
	void correoSinVerificarNoIniciaSesion() throws Exception {
		crear("sara@correo.com", Role.HUESPED, EstadoUsuario.ACTIVO, false);

		login("sara@correo.com", CONTRASENIA)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.requiereVerificacion").value(true))
				.andExpect(jsonPath("$.token").doesNotExist());
	}

	@Test
	@DisplayName("Una cuenta restringida o desactivada no inicia sesión")
	void cuentaNoActivaNoIniciaSesion() throws Exception {
		crear("ana@correo.com", Role.HUESPED, EstadoUsuario.RESTRINGIDO, true);
		crear("luis@correo.com", Role.ANFITRION, EstadoUsuario.INACTIVO, true);

		login("ana@correo.com", CONTRASENIA).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("La cuenta está restringida o desactivada."));
		login("luis@correo.com", CONTRASENIA).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Si falta el correo o la contraseña se informa")
	void faltanDatos() throws Exception {
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"correo\": \"ana@correo.com\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Debes enviar el correo y la contraseña."));
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("no es json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("La documentación de Swagger incluye el inicio de sesión con sus respuestas")
	void documentacionIncluyeElLogin() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/auth/login'].post.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/auth/login'].post.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/auth/login'].post.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/auth/login'].post.responses['429']").exists());
	}

	/**
	 * Envía una petición de inicio de sesión.
	 *
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions login(String correo, String contrasenia) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasenia\": \"%s\"}".formatted(correo, contrasenia)));
	}

	/**
	 * Envía una petición de inicio de sesión con el código del correo o del
	 * segundo factor.
	 *
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions login(String correo, String contrasenia, String codigo) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasenia\": \"%s\", \"codigo\": \"%s\"}"
						.formatted(correo, contrasenia, codigo)));
	}

	/**
	 * Guarda un usuario directamente en la base de datos.
	 *
	 * @return usuario guardado
	 */
	private Usuario crear(String correo, Role rol, EstadoUsuario estado, boolean verificado) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia(passwordEncoder.encode(CONTRASENIA));
		usuario.setRol(rol);
		usuario.setEstado(estado);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(verificado);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}

	/**
	 * Código de 6 dígitos del último correo enviado.
	 *
	 * @return código
	 */
	private String codigoEnviado() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, atLeastOnce()).send(captor.capture());
		Matcher matcher = CODIGO.matcher(captor.getValue().getText());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	/**
	 * Busca un usuario por su correo.
	 *
	 * @param correo correo normalizado
	 * @return el usuario
	 */
	private Usuario buscar(String correo) {
		return usuarioRepository.findByCorreoHash(UsuarioService.hashCorreo(correo)).orElseThrow();
	}
}
