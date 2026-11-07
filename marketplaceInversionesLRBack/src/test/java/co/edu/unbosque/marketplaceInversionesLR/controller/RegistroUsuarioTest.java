package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

/**
 * Pruebas de HU-06 Registro de usuario con selección de rol sobre la API
 * completa y un PostgreSQL temporal en Docker: registro, correo con el código,
 * verificación y token. El servidor de correo se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class RegistroUsuarioTest {

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

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
	@DisplayName("CA-030 Con datos completos crea la cuenta con el rol elegido y envía el código al correo")
	void registroCreaLaCuentaYEnviaElCodigo_CA030() throws Exception {
		registrar("Ana Mora", "Ana@Correo.com", CONTRASENIA, "ANFITRION")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message", Matchers.startsWith("Usuario registrado.")))
				.andExpect(jsonPath("$.correo").value("ana@correo.com"))
				.andExpect(jsonPath("$.requiereVerificacion").value(true));

		Usuario usuario = buscar("ana@correo.com");
		assertThat(usuario.getRol()).isEqualTo(Role.ANFITRION);
		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(usuario.esCorreoVerificado()).isFalse();
		assertThat(AESUtil.decrypt(usuario.getCorreo())).isEqualTo("ana@correo.com");
		assertThat(passwordEncoder.matches(CONTRASENIA, usuario.getContrasenia())).isTrue();

		SimpleMailMessage correo = correoEnviado();
		assertThat(correo.getTo()).containsExactly("ana@correo.com");
		assertThat(correo.getFrom()).isEqualTo("pruebas@marketplace.local");
		assertThat(correo.getText()).contains("Hola Ana Mora");

		assertThat(auditoriaRepository.findByAccionOrderByFechaDesc(AuditoriaService.REGISTRO_USUARIO))
				.anySatisfy(evento -> {
					assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
					assertThat(evento.getIdEntidad()).isEqualTo(usuario.getIdUsuario());
				});
	}

	@Test
	@DisplayName("CA-030 Con el código del correo se verifica la cuenta y se entrega el token de sesión")
	void verificarEntregaElToken_CA030() throws Exception {
		registrar("Luis", "luis@correo.com", CONTRASENIA, null).andExpect(status().isCreated());
		String codigo = codigoDe(correoEnviado().getText());

		String respuesta = verificar("luis@correo.com", codigo)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Correo verificado correctamente."))
				.andExpect(jsonPath("$.rol").value("HUESPED"))
				.andExpect(jsonPath("$.correo").value("luis@correo.com"))
				.andReturn().getResponse().getContentAsString();

		String token = respuesta.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
		Usuario usuario = buscar("luis@correo.com");
		assertThat(usuario.esCorreoVerificado()).isTrue();
		assertThat(jwtUtil.validateToken(token, usuario)).isTrue();

		verificar("luis@correo.com", codigo).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("El correo ya está verificado. Inicia sesión."));
	}

	@Test
	@DisplayName("Un código incorrecto no verifica la cuenta")
	void codigoIncorrectoNoVerifica() throws Exception {
		registrar("Sara", "sara@correo.com", CONTRASENIA, null).andExpect(status().isCreated());
		String codigo = codigoDe(correoEnviado().getText());
		String incorrecto = codigo.equals("000000") ? "111111" : "000000";

		verificar("sara@correo.com", incorrecto).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El código no es válido o ya venció. Revísalo o solicita uno nuevo."));
		verificar("sara@correo.com", "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Debes enviar el correo y el código."));

		assertThat(buscar("sara@correo.com").esCorreoVerificado()).isFalse();
	}

	@Test
	@DisplayName("Solo se puede pedir otro código una vez por minuto")
	void reenviarCodigoUnaVezPorMinuto() throws Exception {
		registrar("Ana", "ana@correo.com", CONTRASENIA, null).andExpect(status().isCreated());

		mockMvc.perform(post("/auth/reenviarcodigo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"ana@correo.com\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("Espera un minuto antes de pedir otro código."));
		mockMvc.perform(post("/auth/reenviarcodigo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"nadie@correo.com\"}"))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("CA-031 Si faltan datos obligatorios se informa y no se crea la cuenta")
	void faltanDatosNoRegistra_CA031() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"rol\": \"HUESPED\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", Matchers.containsString("nombre")))
				.andExpect(jsonPath("$.message", Matchers.containsString("correo")))
				.andExpect(jsonPath("$.message", Matchers.containsString("contraseña")));

		assertThat(usuarioRepository.count()).isZero();
		verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
	}

	@ParameterizedTest(name = "contraseña \"{0}\"")
	@ValueSource(strings = { "Ab1!", "clave#2026", "CLAVE#2026", "Clave#Abcd", "Clave2026x" })
	@DisplayName("CA-135 Una contraseña que no cumple las condiciones mínimas se rechaza")
	void contraseniaDebilSeRechaza_CA135(String contrasenia) throws Exception {
		registrar("Ana", "ana@correo.com", contrasenia, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(
						"La contraseña debe tener entre 8 y 64 caracteres, una mayúscula, una minúscula, un número y un símbolo."));

		assertThat(usuarioRepository.count()).isZero();
	}

	@Test
	@DisplayName("No se permiten dos cuentas con el mismo correo, aunque cambien mayúsculas")
	void correoDuplicadoSeRechaza() throws Exception {
		registrar("Ana", "ana@correo.com", CONTRASENIA, null).andExpect(status().isCreated());

		registrar("Otra Ana", "ANA@correo.com", CONTRASENIA, null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Ya existe un usuario con ese correo electrónico."));
		assertThat(usuarioRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("El registro público no permite el rol ADMIN")
	void rolAdminNoSePermite() throws Exception {
		registrar("Ana", "ana@correo.com", CONTRASENIA, "ADMIN").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("Solo puedes registrarte como HUESPED o ANFITRION."));

		assertThat(usuarioRepository.count()).isZero();
	}

	@Test
	@DisplayName("Un rol que no existe o un cuerpo que no es JSON se rechazan")
	void cuerpoInvalidoSeRechaza() throws Exception {
		registrar("Ana", "ana@correo.com", CONTRASENIA, "DUENO").andExpect(status().isBadRequest());
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("esto no es json"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
	}

	@Test
	@DisplayName("CA-134 Una ruta protegida sin token responde 401 en el formato de la API")
	void rutaProtegidaSinTokenResponde401_CA134() throws Exception {
		mockMvc.perform(get("/usuarios/perfil"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.message").value("No autenticado: token ausente, inválido o expirado"));
	}

	/**
	 * Envía una petición de registro.
	 *
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions registrar(String nombre, String correo, String contrasenia, String rol) throws Exception {
		String cuerpo = "{\"nombre\": \"%s\", \"correo\": \"%s\", \"contrasenia\": \"%s\"%s}".formatted(nombre, correo,
				contrasenia, rol == null ? "" : ", \"rol\": \"" + rol + "\"");
		return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/**
	 * Envía una petición de verificación.
	 *
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions verificar(String correo, String codigo) throws Exception {
		return mockMvc.perform(post("/auth/verificar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"codigo\": \"%s\"}".formatted(correo, codigo)));
	}

	/**
	 * Último correo enviado.
	 *
	 * @return correo enviado
	 */
	private SimpleMailMessage correoEnviado() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, atLeastOnce()).send(captor.capture());
		return captor.getValue();
	}

	/**
	 * Saca el código de 6 dígitos del cuerpo del correo.
	 *
	 * @param cuerpo cuerpo del correo
	 * @return código
	 */
	private static String codigoDe(String cuerpo) {
		Matcher matcher = CODIGO.matcher(cuerpo);
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
