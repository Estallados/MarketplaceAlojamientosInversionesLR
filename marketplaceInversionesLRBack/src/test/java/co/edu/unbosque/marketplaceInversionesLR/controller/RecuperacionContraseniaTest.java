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

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.TokenRecuperacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-08 Recuperación de contraseña sobre la API completa y un
 * PostgreSQL temporal en Docker. El servidor de correo se simula: el proceso no
 * depende de un mecanismo externo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class RecuperacionContraseniaTest {

	/** Contraseña con la que se crea la cuenta. */
	private static final String ANTERIOR = "Clave#2026";

	/** Contraseña nueva que cumple las reglas. */
	private static final String NUEVA = "Nueva#2026";

	/** Correo de la cuenta de prueba. */
	private static final String CORREO = "ana@correo.com";

	/** Respuesta de /auth/recuperar, igual exista o no la cuenta. */
	private static final String SOLICITUD_RECIBIDA = "Si el correo está registrado, se generó un enlace de "
			+ "recuperación válido por 30 minutos.";

	/** Token dentro del cuerpo del correo (línea con sangría). */
	private static final Pattern TOKEN = Pattern.compile("\\n {4}(\\S+)\\r?\\n");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private TokenRecuperacionRepository tokenRepository;

	@Autowired
	private AuditoriaRepository auditoriaRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private JavaMailSender mailSender;

	@BeforeEach
	void limpiar() {
		usuarioRepository.deleteAll();
		tokenRepository.deleteAll();
		clearInvocations(mailSender);
	}

	@Test
	@DisplayName("CA-038 El usuario solicita la recuperación, recibe el token y restablece su contraseña")
	void recuperaLaContrasenia_CA038() throws Exception {
		crearCuenta();

		recuperar(" ANA@correo.com ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(SOLICITUD_RECIBIDA));
		String token = tokenEnviado();

		restablecer(token, NUEVA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Contraseña actualizada. Ya puedes iniciar sesión."));

		// La contraseña anterior ya no sirve y la nueva sí (el login pasa al paso del código por correo)
		login(ANTERIOR).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Correo o contraseña inválidos."));
		login(NUEVA).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereCodigoCorreo").value(true));

		// El token sirve una sola vez
		restablecer(token, "Otra#Clave1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El enlace de recuperación no es válido o ya venció."));

		assertThat(auditoriaRepository.findByAccionOrderByFechaDesc(AuditoriaService.CONTRASENIA_RESTABLECIDA))
				.anySatisfy(evento -> assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO));
	}

	@Test
	@DisplayName("Con un correo no registrado responde igual, sin enviar correo, para no revelar las cuentas")
	void correoNoRegistrado() throws Exception {
		recuperar("nadie@correo.com")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(SOLICITUD_RECIBIDA));

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
		assertThat(tokenRepository.count()).isZero();
	}

	@Test
	@DisplayName("Un token inválido, una contraseña débil o datos faltantes no cambian la contraseña")
	void datosInvalidos() throws Exception {
		crearCuenta();

		restablecer("token-falso", NUEVA).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El enlace de recuperación no es válido o ya venció."));
		restablecer("token-falso", "debil").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La contraseña debe tener entre 8 y 64 caracteres, una "
						+ "mayúscula, una minúscula, un número y un símbolo."));
		mockMvc.perform(post("/auth/restablecer").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Debes enviar el token y la contraseña nueva."));
		mockMvc.perform(post("/auth/recuperar").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Debes enviar el correo."));

		login(ANTERIOR).andExpect(jsonPath("$.requiereCodigoCorreo").value(true));
	}

	@Test
	@DisplayName("La documentación de Swagger incluye la recuperación de contraseña")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/auth/recuperar'].post.responses['200']").exists())
				.andExpect(jsonPath("$.paths['/auth/restablecer'].post.responses['400']").exists());
	}

	/**
	 * Pide la recuperación de contraseña.
	 *
	 * @param correo correo de la cuenta
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions recuperar(String correo) throws Exception {
		return mockMvc.perform(post("/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\"}".formatted(correo)));
	}

	/**
	 * Restablece la contraseña con un token.
	 *
	 * @param token       token de recuperación
	 * @param contrasenia contraseña nueva
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions restablecer(String token, String contrasenia) throws Exception {
		return mockMvc.perform(post("/auth/restablecer").contentType(MediaType.APPLICATION_JSON)
				.content("{\"token\": \"%s\", \"contrasenia\": \"%s\"}".formatted(token, contrasenia)));
	}

	/**
	 * Primer paso del inicio de sesión con la contraseña indicada.
	 *
	 * @param contrasenia contraseña
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions login(String contrasenia) throws Exception {
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\": \"%s\", \"contrasenia\": \"%s\"}".formatted(CORREO, contrasenia)));
	}

	/**
	 * Guarda la cuenta de prueba, activa y verificada.
	 *
	 * @return usuario guardado
	 */
	private Usuario crearCuenta() {
		Usuario usuario = new Usuario();
		usuario.setNombre("Ana");
		usuario.setCorreo(AESUtil.encrypt(CORREO));
		usuario.setCorreoHash(UsuarioService.hashCorreo(CORREO));
		usuario.setContrasenia(passwordEncoder.encode(ANTERIOR));
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}

	/**
	 * Token de recuperación del último correo enviado.
	 *
	 * @return token
	 */
	private String tokenEnviado() {
		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, atLeastOnce()).send(captor.capture());
		SimpleMailMessage mensaje = captor.getValue();
		assertThat(mensaje.getTo()).containsExactly(CORREO);
		assertThat(mensaje.getSubject()).isEqualTo("Recupera tu contraseña - Marketplace Inversiones LR");
		Matcher matcher = TOKEN.matcher(mensaje.getText());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
