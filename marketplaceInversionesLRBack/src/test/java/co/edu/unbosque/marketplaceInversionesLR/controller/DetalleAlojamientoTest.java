package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-18 Consultar información detallada de un alojamiento, con
 * mapa, sobre la API completa y un PostgreSQL temporal en Docker. Cloudinary
 * se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class DetalleAlojamientoTest {

	/** Primeros bytes de un JPEG válido. */
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

	/** Ruta base de las publicaciones. */
	private static final String ALOJAMIENTOS = "/alojamientos";

	/** Cabecera de autenticación. */
	private static final String AUTORIZACION = "Authorization";

	/** Prefijo del token en la cabecera. */
	private static final String BEARER = "Bearer ";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private PoliticaCancelacionRepository politicaRepository;

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

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = jwtUtil.generateToken(crear("anfitrion@correo.com", Role.ANFITRION));
		when(cloudinaryService.subir(any(), any(), any(), any())).thenAnswer(invocacion -> new ImagenSubida(
				"pruebas/" + System.nanoTime(), "https://res.cloudinary.com/img/" + System.nanoTime() + ".jpg"));
	}

	/**
	 * Borra los datos en el orden de las llaves foráneas, para no afectar a las
	 * demás pruebas.
	 */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		jdbc.update("DELETE FROM politica_cancelacion");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-093 Una publicación ACTIVA muestra su información detallada, incluso sin iniciar sesión")
	void detalleDePublicacionActiva_CA093() throws Exception {
		Long id = crearPublicacion();
		activar(id);

		consultar(id, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idAlojamiento").value(id))
				.andExpect(jsonPath("$.estado").value("ACTIVA"))
				.andExpect(jsonPath("$.titulo").value("Casa en el lago"))
				.andExpect(jsonPath("$.descripcion").value("Vista al lago"))
				.andExpect(jsonPath("$.ciudad").value("Guatapé"))
				.andExpect(jsonPath("$.capacidad").value(4))
				.andExpect(jsonPath("$.precioNoche").value(250000))
				.andExpect(jsonPath("$.imagenes.length()").value(1))
				.andExpect(jsonPath("$.serviciosAdicionales").isArray())
				.andExpect(jsonPath("$.nombrePolitica").value("Flexible"))
				.andExpect(jsonPath("$.nombreAnfitrion").value("Usuario de prueba"));
	}

	@Test
	@DisplayName("CA-164 El detalle trae la ubicación registrada para mostrarla en el mapa")
	void detalleConUbicacion_CA164() throws Exception {
		Long id = crearPublicacion();
		activar(id);

		consultar(id, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ubicacion").value("Calle 1 # 2-3"))
				.andExpect(jsonPath("$.latitud").value(6.23))
				.andExpect(jsonPath("$.longitud").value(-75.15));
	}

	@Test
	@DisplayName("Un borrador no se muestra sin sesión ni a otros usuarios, pero sí a su anfitrión y a un administrador")
	void borradorSoloParaDuenoYAdmin() throws Exception {
		Long id = crearPublicacion();

		consultar(id, null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("El alojamiento no existe."));
		consultar(id, jwtUtil.generateToken(crear("huesped@correo.com", Role.HUESPED)))
				.andExpect(status().isNotFound());
		consultar(id, anfitrion)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("BORRADOR"));
		consultar(id, jwtUtil.generateToken(crear("admin@correo.com", Role.ADMIN)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Una publicación que no existe responde 404")
	void publicacionInexistente() throws Exception {
		consultar(999999L, null)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("El alojamiento no existe."));
	}

	@Test
	@DisplayName("CA-103 El anfitrión consulta solo sus publicaciones, con el estado de cada una")
	void misPublicaciones_CA103() throws Exception {
		Long activa = crearPublicacion();
		activar(activa);
		Long borrador = crearPublicacion();
		crearPublicacion(jwtUtil.generateToken(crear("otro@correo.com", Role.ANFITRION)));

		misPublicaciones(anfitrion)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[*].idAlojamiento")
						.value(containsInAnyOrder(activa.intValue(), borrador.intValue())))
				.andExpect(jsonPath("$.contenido[*].estado").value(containsInAnyOrder("ACTIVA", "BORRADOR")));
	}

	@Test
	@DisplayName("CA-103 Solo el anfitrión consulta sus publicaciones: sin token 401 y con otro rol 403")
	void misPublicacionesSoloAnfitrion_CA103() throws Exception {
		misPublicaciones(null).andExpect(status().isUnauthorized());
		misPublicaciones(jwtUtil.generateToken(crear("huesped@correo.com", Role.HUESPED)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("La documentación de Swagger incluye el detalle de un alojamiento")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/alojamientos/{id}'].get.responses['404']").exists());
	}

	/**
	 * Consulta el detalle de una publicación.
	 *
	 * @param id  publicación
	 * @param jwt token del usuario; {@code null} para consultar sin sesión
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions consultar(Long id, String jwt) throws Exception {
		var peticion = get(ALOJAMIENTOS + "/" + id);
		return mockMvc.perform(jwt == null ? peticion : peticion.header(AUTORIZACION, BEARER + jwt));
	}

	/**
	 * Consulta las publicaciones del usuario del token.
	 *
	 * @param jwt token del usuario; {@code null} para consultar sin sesión
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions misPublicaciones(String jwt) throws Exception {
		var peticion = get(ALOJAMIENTOS + "/mios");
		return mockMvc.perform(jwt == null ? peticion : peticion.header(AUTORIZACION, BEARER + jwt));
	}

	/**
	 * Deja una publicación en estado ACTIVA.
	 *
	 * @param id publicación
	 */
	private void activar(Long id) {
		jdbc.update("UPDATE alojamiento SET estado = 'ACTIVA' WHERE id_alojamiento = ?", id);
	}

	/**
	 * Crea una publicación en borrador con una imagen.
	 *
	 * @return id de la publicación
	 * @throws Exception si falla la petición
	 */
	private Long crearPublicacion() throws Exception {
		return crearPublicacion(anfitrion);
	}

	/**
	 * Crea una publicación en borrador con una imagen para el anfitrión del
	 * token.
	 *
	 * @param jwt token del anfitrión
	 * @return id de la publicación
	 * @throws Exception si falla la petición
	 */
	private Long crearPublicacion(String jwt) throws Exception {
		Long idPolitica = politicaRepository.findByNombre("Flexible").orElseGet(this::crearPolitica).getIdPolitica();
		String datos = """
				{"titulo":"Casa en el lago","descripcion":"Vista al lago","ubicacion":"Calle 1 # 2-3",
				 "ciudad":"Guatapé","latitud":6.23,"longitud":-75.15,"tipo":"Casa","capacidad":4,
				 "precioNoche":250000,"habitaciones":2,"camas":3,"banos":2,"horaEntrada":"15:00",
				 "horaSalida":"11:00","reglas":"No fumar","idPolitica":%d}
				""".formatted(idPolitica);
		String respuesta = mockMvc.perform(multipart(ALOJAMIENTOS)
				.file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
						datos.getBytes(StandardCharsets.UTF_8)))
				.file(new MockMultipartFile("archivos", "a.jpg", "image/jpeg", JPEG))
				.header(AUTORIZACION, BEARER + jwt))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(respuesta, "$.idAlojamiento")).longValue();
	}

	/**
	 * Guarda la política de cancelación de las publicaciones de prueba.
	 *
	 * @return política guardada
	 */
	private PoliticaCancelacion crearPolitica() {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre("Flexible");
		politica.setCondiciones("Reembolso total hasta 7 días antes");
		politica.setDiasReembolsoTotal(7);
		politica.setDiasReembolsoParcial(2);
		politica.setPorcentajeParcial(50);
		return politicaRepository.save(politica);
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
