package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import co.edu.unbosque.marketplaceInversionesLR.security.GoogleTokenVerificador;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.TotpUtil;
import io.jsonwebtoken.Jwts;

/**
 * Inicio de sesión con Google sobre la API completa y un PostgreSQL temporal en
 * Docker. Los ID token se firman con una clave RSA de prueba que reemplaza a
 * las claves públicas de Google.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ ContenedorPostgres.class, InicioGoogleTest.GoogleDePrueba.class })
@Testcontainers(disabledWithoutDocker = true)
class InicioGoogleTest {

	/** Client ID de prueba. */
	private static final String CLIENTE = "cliente-prueba.apps.googleusercontent.com";

	/** Identificador de la clave de prueba. */
	private static final String ID_CLAVE = "clave-prueba";

	/** Claves con las que "firma Google". */
	private static final KeyPair CLAVES = generarClaves();

	/** Claves de un tercero que no es Google. */
	private static final KeyPair OTRAS_CLAVES = generarClaves();

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

	/** Reemplaza el verificador por uno que conoce la clave de prueba. */
	@TestConfiguration
	static class GoogleDePrueba {

		/**
		 * Verificador de prueba.
		 *
		 * @return verificador con el client ID y la clave de prueba
		 */
		@Bean
		@Primary
		GoogleTokenVerificador verificadorDePrueba() {
			return new GoogleTokenVerificador(CLIENTE, id -> ID_CLAVE.equals(id) ? CLAVES.getPublic() : null);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private JavaMailSender mailSender;

	@BeforeEach
	void limpiar() {
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("La configuración entrega el client ID para el botón de Google")
	void configuracion() throws Exception {
		mockMvc.perform(get("/auth/google/configuracion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.habilitado").value(true))
				.andExpect(jsonPath("$.clientId").value(CLIENTE));
	}

	@Test
	@DisplayName("La primera vez crea la cuenta verificada como HUESPED y la segunda inicia sesión con ella")
	void creaYLuegoIniciaSesion() throws Exception {
		google(Map.of("idToken", token("ana@gmail.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.cuentaNueva").value(true))
				.andExpect(jsonPath("$.rol").value("HUESPED"))
				.andExpect(jsonPath("$.correo").value("ana@gmail.com"))
				.andExpect(jsonPath("$.token").isNotEmpty());

		Usuario ana = buscar("ana@gmail.com");
		assertThat(ana.getNombre()).isEqualTo("Ana María Prieto");
		assertThat(ana.esCorreoVerificado()).isTrue();

		google(Map.of("idToken", token("ANA@gmail.com")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cuentaNueva").value(false));
		assertThat(usuarioRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Se puede registrar como ANFITRION pero nunca como ADMIN")
	void rolesPermitidos() throws Exception {
		google(Map.of("idToken", token("luis@gmail.com"), "rol", "ANFITRION"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.rol").value("ANFITRION"));
		google(Map.of("idToken", token("eva@gmail.com"), "rol", "ADMIN"))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Una cuenta con contraseña sin verificar entra con Google, conserva su rol y queda verificada")
	void cuentaConContrasenia() throws Exception {
		crear("sara@gmail.com", Role.ANFITRION, EstadoUsuario.ACTIVO, false);

		google(Map.of("idToken", token("sara@gmail.com"), "rol", "HUESPED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("ANFITRION"));
		assertThat(buscar("sara@gmail.com").esCorreoVerificado()).isTrue();
	}

	@Test
	@DisplayName("Rechaza tokens que no son de Google para esta aplicación y no crea la cuenta")
	void tokensInvalidos() throws Exception {
		String correo = "eva@gmail.com";
		google(Map.of("idToken", token(correo, Map.of("aud", "otra-app"), CLAVES.getPrivate())))
				.andExpect(status().isUnauthorized());
		google(Map.of("idToken", token(correo, Map.of("iss", "https://evil.example.com"), CLAVES.getPrivate())))
				.andExpect(status().isUnauthorized());
		google(Map.of("idToken", token(correo, Map.of("email_verified", false), CLAVES.getPrivate())))
				.andExpect(status().isUnauthorized());
		google(Map.of("idToken", token(correo, Map.of(), OTRAS_CLAVES.getPrivate())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("No se pudo verificar tu cuenta de Google. Intenta de nuevo."));
		mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(usuarioRepository.count()).isZero();
	}

	@Test
	@DisplayName("Una cuenta restringida no entra y una con segundo factor debe enviar su código")
	void restringidaYSegundoFactor() throws Exception {
		crear("rita@gmail.com", Role.HUESPED, EstadoUsuario.RESTRINGIDO, true);
		google(Map.of("idToken", token("rita@gmail.com")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("La cuenta está restringida."));

		Usuario tomas = crear("tomas@gmail.com", Role.HUESPED, EstadoUsuario.ACTIVO, true);
		String secreto = TotpUtil.generarSecreto();
		tomas.setSecretoTotp(AESUtil.encrypt(secreto));
		tomas.setTotpHabilitado(true);
		usuarioRepository.save(tomas);

		google(Map.of("idToken", token("tomas@gmail.com")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereSegundoFactor").value(true));
		google(Map.of("idToken", token("tomas@gmail.com"), "codigo", "abcdef"))
				.andExpect(status().isUnauthorized());
		google(Map.of("idToken", token("tomas@gmail.com"), "codigo", TotpUtil.codigo(secreto, Instant.now())))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("La documentación de Swagger incluye las rutas de Google")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/auth/google'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/auth/google/configuracion'].get").exists());
	}

	/**
	 * Envía una petición a {@code POST /auth/google}.
	 *
	 * @param cuerpo campos del cuerpo
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions google(Map<String, String> cuerpo) throws Exception {
		StringBuilder json = new StringBuilder("{");
		cuerpo.forEach((clave, valor) -> json.append(json.length() > 1 ? "," : "").append('"').append(clave)
				.append("\":\"").append(valor).append('"'));
		return mockMvc.perform(post("/auth/google").contentType(MediaType.APPLICATION_JSON).content(json.append('}')
				.toString()));
	}

	/**
	 * ID token válido para el correo.
	 *
	 * @param correo correo de la cuenta de Google
	 * @return token firmado
	 */
	private static String token(String correo) {
		return token(correo, Map.of(), CLAVES.getPrivate());
	}

	/**
	 * ID token como el que entrega Google, con los cambios indicados.
	 *
	 * @param correo  correo de la cuenta de Google
	 * @param cambios claims que se reemplazan o agregan
	 * @param clave   clave privada con la que se firma
	 * @return token firmado
	 */
	private static String token(String correo, Map<String, Object> cambios, PrivateKey clave) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("iss", "https://accounts.google.com");
		claims.put("aud", CLIENTE);
		claims.put("sub", "1098" + correo.hashCode());
		claims.put("email", correo);
		claims.put("email_verified", true);
		claims.put("name", "Ana María Prieto");
		Instant ahora = Instant.now();
		claims.put("iat", ahora.getEpochSecond());
		claims.put("exp", ahora.plusSeconds(3600).getEpochSecond());
		claims.putAll(cambios);
		return Jwts.builder().header().keyId(ID_CLAVE).and().claims(claims).signWith(clave).compact();
	}

	/**
	 * Guarda un usuario con contraseña directamente en la base de datos.
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
	 * Busca un usuario por su correo.
	 *
	 * @param correo correo normalizado
	 * @return el usuario
	 */
	private Usuario buscar(String correo) {
		return usuarioRepository.findByCorreoHash(UsuarioService.hashCorreo(correo)).orElseThrow();
	}

	/**
	 * Genera un par de claves RSA de 2048 bits.
	 *
	 * @return par de claves
	 */
	private static KeyPair generarClaves() {
		try {
			KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
			generador.initialize(2048);
			return generador.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
