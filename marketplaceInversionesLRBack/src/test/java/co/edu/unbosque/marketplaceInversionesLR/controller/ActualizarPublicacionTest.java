package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-16 Actualizar publicación y definir servicios adicionales con
 * costo sobre la API completa y un PostgreSQL temporal en Docker. Cloudinary
 * se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ActualizarPublicacionTest {

	/** Primeros bytes de un JPEG válido. */
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private CloudinaryService cloudinaryService;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Token del anfitrión propietario. */
	private String anfitrion;

	/** Token de otro anfitrión. */
	private String otro;

	/** Token del administrador. */
	private String admin;

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = jwtUtil.generateToken(crear("anfitrion@correo.com", Role.ANFITRION));
		otro = jwtUtil.generateToken(crear("otro@correo.com", Role.ANFITRION));
		admin = jwtUtil.generateToken(crear("admin@correo.com", Role.ADMIN));
		when(cloudinaryService.subir(any(), any(), any(), any())).thenAnswer(invocacion -> new ImagenSubida(
				"pruebas/" + System.nanoTime(), "https://res.cloudinary.com/img/" + System.nanoTime() + ".jpg"));
	}

	/**
	 * Borra los datos en el orden de las llaves foráneas, para no afectar a las
	 * demás pruebas.
	 */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM servicio_adicional");
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-100 CA-113 El propietario modifica su publicación y ve los cambios")
	void elPropietarioModificaYVeLosCambios_CA100_CA113() throws Exception {
		Long id = crearBorrador("Original");

		actualizar(anfitrion, id, "{\"titulo\":\"Nuevo título\",\"capacidad\":3}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Nuevo título"))
				.andExpect(jsonPath("$.capacidad").value(3));
	}

	@Test
	@DisplayName("CA-101 CA-113 CA-114 Otro anfitrión no puede modificar la publicación, pero un administrador sí")
	void otroAnfitrionNoPuedeModificarPeroUnAdministradorSi_CA101_CA113_CA114() throws Exception {
		Long id = crearBorrador("Ajeno");

		actualizar(otro, id, "{\"titulo\":\"Hackeado\"}")
				.andExpect(status().isForbidden());
		actualizar(admin, id, "{\"titulo\":\"Corregido por admin\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.titulo").value("Corregido por admin"));
	}

	@Test
	@DisplayName("CA-185 El propietario registra servicios adicionales con costo positivo y otros usuarios no los pueden tocar")
	void elPropietarioRegistraServiciosAdicionalesYOtrosNo_CA185() throws Exception {
		Long id = crearBorrador("Con servicios");

		String creado = servicio(post("/alojamientos/" + id + "/servicios"), anfitrion,
				"{\"nombre\":\"Desayuno\",\"costo\":20000}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		servicio(post("/alojamientos/" + id + "/servicios"), anfitrion, "{\"nombre\":\"Gratis\",\"costo\":0}")
				.andExpect(status().isBadRequest());
		Long idServicio = ((Number) JsonPath.read(creado, "$.idServicio")).longValue();
		servicio(put("/alojamientos/" + id + "/servicios/" + idServicio), otro, "{\"nombre\":\"Robado\",\"costo\":1}")
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/alojamientos/" + id + "/servicios/" + idServicio)
				.header("Authorization", "Bearer " + otro))
				.andExpect(status().isForbidden());

		actualizar(anfitrion, id, "{}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.serviciosAdicionales[0].nombre").value("Desayuno"));
	}

	/**
	 * Crea un borrador con una imagen a nombre del anfitrión propietario.
	 *
	 * @param titulo título de la publicación
	 * @return id de la publicación
	 * @throws Exception si falla la petición
	 */
	private Long crearBorrador(String titulo) throws Exception {
		String respuesta = mockMvc.perform(multipart("/alojamientos")
				.file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
						("{\"titulo\":\"" + titulo + "\"}").getBytes(StandardCharsets.UTF_8)))
				.file(new MockMultipartFile("archivos", "a.jpg", "image/jpeg", JPEG))
				.header("Authorization", "Bearer " + anfitrion))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(respuesta, "$.idAlojamiento")).longValue();
	}

	/**
	 * Envía la edición de una publicación.
	 *
	 * @param token JWT del usuario
	 * @param id    publicación
	 * @param json  datos nuevos
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions actualizar(String token, Long id, String json) throws Exception {
		return servicio(put("/alojamientos/" + id), token, json);
	}

	/**
	 * Envía una petición con cuerpo JSON.
	 *
	 * @param peticion petición a enviar
	 * @param token    JWT del usuario
	 * @param json     cuerpo
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions servicio(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion, String token,
			String json) throws Exception {
		return mockMvc.perform(peticion.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/**
	 * Guarda un usuario activo y verificado con el rol indicado.
	 *
	 * @param correo correo de la cuenta
	 * @param rol    rol de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crear(String correo, Role rol) {
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
