package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.CodigoVerificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.SegundoFactorService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.TotpUtil;

/**
 * Pruebas de HU-30 Autenticación multifactor sobre la API completa y un
 * PostgreSQL temporal en Docker: activar, confirmar y desactivar el segundo
 * factor, y su efecto en el inicio de sesión. El servidor de correo se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class SegundoFactorTest {

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

	/** Ruta base del segundo factor. */
	private static final String RUTA = "/usuarios/segundofactor";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private CodigoVerificacionRepository codigoRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuditoriaService auditoriaService;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Huésped que habilita el segundo factor. */
	private Usuario ana;

	/** Token de Ana. */
	private String tokenAna;

	@BeforeEach
	void preparar() {
		codigoRepository.deleteAll();
		usuarioRepository.deleteAll();
		clearInvocations(mailSender);
		ana = crear("ana@correo.com");
		tokenAna = jwtUtil.generateToken(ana);
	}

	@Test
	@DisplayName("CA-174 CA-178 El usuario activa el segundo factor y desde entonces el login pide un código válido "
			+ "de su app")
	void activaYElLoginPideElCodigo_CA174_CA178() throws Exception {
		String secreto = activarYConfirmar(tokenAna);

		Usuario guardado = usuarioRepository.findById(ana.getIdUsuario()).orElseThrow();
		assertThat(guardado.getTotpHabilitado()).isTrue();
		// El secreto se guarda cifrado, nunca en claro
		assertThat(guardado.getSecretoTotp()).isNotEqualTo(secreto);
		assertThat(AESUtil.decrypt(guardado.getSecretoTotp())).isEqualTo(secreto);

		// CA-174: credenciales válidas no bastan; un código incorrecto se rechaza
		login("ana@correo.com", null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereSegundoFactor").value(true))
				.andExpect(jsonPath("$.requiereCodigoCorreo").doesNotExist())
				.andExpect(jsonPath("$.token").doesNotExist());
		login("ana@correo.com", "000000")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereSegundoFactor").value(true))
				.andExpect(jsonPath("$.token").doesNotExist());
		login("ana@correo.com", TotpUtil.codigo(secreto, Instant.now()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
		// Con app autenticadora no se envía código por correo
		verify(mailSender, never()).send(any(SimpleMailMessage.class));
		assertThat(auditoriaService.consultar(AuditoriaService.SEGUNDO_FACTOR, ana.getIdUsuario(), null, null, 0, 10)
				.getContent()).isNotEmpty();
	}

	@Test
	@DisplayName("CA-178 La configuración queda solo en la cuenta de quien la activa")
	void configuracionExclusivaDeLaCuenta_CA178() throws Exception {
		Usuario luis = crear("luis@correo.com");
		activarYConfirmar(tokenAna);

		Usuario otro = usuarioRepository.findById(luis.getIdUsuario()).orElseThrow();
		assertThat(otro.getTotpHabilitado()).isFalse();
		assertThat(otro.getSecretoTotp()).isNull();
		// Luis sigue con el mecanismo normal: código por correo
		login("luis@correo.com", null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereCodigoCorreo").value(true))
				.andExpect(jsonPath("$.metodoSegundoFactor").value("CORREO"));
		// Y no puede confirmar ni desactivar el de Ana con su propio token
		String tokenLuis = jwtUtil.generateToken(luis);
		enviar(tokenLuis, "/desactivar", "123456")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(SegundoFactorService.NO_HABILITADO));
	}

	@Test
	@DisplayName("CA-177 Sin segundo factor, o después de desactivarlo, el login sigue el mecanismo normal")
	void sinSegundoFactorLoginNormal_CA177() throws Exception {
		login("ana@correo.com", null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereCodigoCorreo").value(true))
				.andExpect(jsonPath("$.metodoSegundoFactor").value("CORREO"));

		String secreto = activarYConfirmar(tokenAna);
		enviar(tokenAna, "/desactivar", "000000")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(SegundoFactorService.CODIGO_INVALIDO));
		enviar(tokenAna, "/desactivar", TotpUtil.codigo(secreto, Instant.now()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(SegundoFactorController.DESHABILITADO));

		Usuario guardado = usuarioRepository.findById(ana.getIdUsuario()).orElseThrow();
		assertThat(guardado.getTotpHabilitado()).isFalse();
		assertThat(guardado.getSecretoTotp()).isNull();
		login("ana@correo.com", null)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereCodigoCorreo").value(true))
				.andExpect(jsonPath("$.metodoSegundoFactor").value("CORREO"));
	}

	@Test
	@DisplayName("Confirmar con un código inválido no habilita el segundo factor; los pasos fuera de orden responden "
			+ "409")
	void pasosInvalidos() throws Exception {
		enviar(tokenAna, "/confirmar", "123456")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(SegundoFactorService.SIN_ACTIVACION));
		enviar(tokenAna, "/desactivar", "123456")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(SegundoFactorService.NO_HABILITADO));

		activar(tokenAna);
		enviar(tokenAna, "/confirmar", "000000")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(SegundoFactorService.CODIGO_INVALIDO));
		enviar(tokenAna, "/confirmar", null).andExpect(status().isBadRequest());
		assertThat(usuarioRepository.findById(ana.getIdUsuario()).orElseThrow().getTotpHabilitado()).isFalse();

		String secreto = activar(tokenAna);
		enviar(tokenAna, "/confirmar", TotpUtil.codigo(secreto, Instant.now())).andExpect(status().isOk());
		mockMvc.perform(post(RUTA + "/activar").header("Authorization", "Bearer " + tokenAna))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(SegundoFactorService.YA_HABILITADO));
	}

	@Test
	@DisplayName("Sin token no se puede gestionar el segundo factor")
	void sinToken() throws Exception {
		mockMvc.perform(post(RUTA + "/activar")).andExpect(status().isUnauthorized());
		mockMvc.perform(post(RUTA + "/confirmar").contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\": \"123456\"}")).andExpect(status().isUnauthorized());
	}

	/**
	 * Activa y confirma el segundo factor con un código válido.
	 *
	 * @param token token del usuario
	 * @return el secreto en Base32
	 * @throws Exception si falla la petición
	 */
	private String activarYConfirmar(String token) throws Exception {
		String secreto = activar(token);
		enviar(token, "/confirmar", TotpUtil.codigo(secreto, Instant.now()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(SegundoFactorController.HABILITADO));
		return secreto;
	}

	/**
	 * Pide el secreto (paso 1) y revisa la respuesta.
	 *
	 * @param token token del usuario
	 * @return el secreto en Base32
	 * @throws Exception si falla la petición
	 */
	private String activar(String token) throws Exception {
		String respuesta = mockMvc.perform(post(RUTA + "/activar").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.secreto").isNotEmpty())
				.andExpect(jsonPath("$.uri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
				.andExpect(jsonPath("$.codigo").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(respuesta, "$.secreto");
	}

	private ResultActions enviar(String token, String accion, String codigo) throws Exception {
		String cuerpo = codigo == null ? "{}" : "{\"codigo\": \"" + codigo + "\"}";
		return mockMvc.perform(post(RUTA + accion).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions login(String correo, String codigo) throws Exception {
		String cuerpo = codigo == null
				? "{\"correo\": \"%s\", \"contrasenia\": \"%s\"}".formatted(correo, CONTRASENIA)
				: "{\"correo\": \"%s\", \"contrasenia\": \"%s\", \"codigo\": \"%s\"}".formatted(correo, CONTRASENIA,
						codigo);
		return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/**
	 * Guarda un huésped activo y verificado, sin segundo factor.
	 *
	 * @param correo correo de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crear(String correo) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia(passwordEncoder.encode(CONTRASENIA));
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
