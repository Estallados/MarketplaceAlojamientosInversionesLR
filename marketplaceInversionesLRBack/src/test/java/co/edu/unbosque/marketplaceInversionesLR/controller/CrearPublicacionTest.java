package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.exception.AlmacenamientoNoDisponibleException;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-15 Crear publicación de alojamiento sobre la API completa y un
 * PostgreSQL temporal en Docker. Cloudinary se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class CrearPublicacionTest {

	/** Primeros bytes de un JPEG válido. */
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private PoliticaCancelacionRepository politicaRepository;

	@Autowired
	private PublicacionService publicacionService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private CloudinaryService cloudinaryService;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Token del anfitrión de prueba. */
	private String anfitrion;

	/** Id del anfitrión de prueba. */
	private Long idAnfitrion;

	@BeforeEach
	void preparar() {
		limpiar();
		Usuario usuario = crear("anfitrion@correo.com", Role.ANFITRION);
		idAnfitrion = usuario.getIdUsuario();
		anfitrion = jwtUtil.generateToken(usuario);
		int[] contador = { 0 };
		when(cloudinaryService.subir(any(), any(), any(), any())).thenAnswer(invocacion -> {
			contador[0]++;
			return new ImagenSubida("pruebas/" + System.nanoTime() + "-" + contador[0],
					"https://res.cloudinary.com/img/" + contador[0] + ".jpg");
		});
	}

	/**
	 * Borra las publicaciones antes que los usuarios (llaves foráneas), para no
	 * afectar a las demás pruebas.
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
	@DisplayName("CA-094 El anfitrión crea la publicación con título, descripción, ubicación, tipo, capacidad, precio, servicios, reglas e imágenes y queda en BORRADOR")
	void creaLaPublicacionEnBorrador_CA094() throws Exception {
		Long idPolitica = politica("Flexible");
		String datos = """
				{"titulo":"Casa en el lago","descripcion":"Vista al lago","ubicacion":"Calle 1 # 2-3",
				 "ciudad":"Guatapé","latitud":6.23,"longitud":-75.15,"tipo":"Casa","capacidad":4,
				 "precioNoche":250000,"habitaciones":2,"camas":3,"banos":2,"horaEntrada":"15:00",
				 "horaSalida":"11:00","reglas":"No fumar","amenidades":["WiFi","Piscina"],"idPolitica":%d,
				 "estado":"ACTIVA","idAnfitrion":999}
				""".formatted(idPolitica);

		String respuesta = crear(anfitrion, datos, imagen("portada.jpg"), imagen("sala.jpg"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("BORRADOR"))
				.andExpect(jsonPath("$.idAnfitrion").value(idAnfitrion))
				.andExpect(jsonPath("$.titulo").value("Casa en el lago"))
				.andExpect(jsonPath("$.descripcion").value("Vista al lago"))
				.andExpect(jsonPath("$.ubicacion").value("Calle 1 # 2-3"))
				.andExpect(jsonPath("$.tipo").value("Casa"))
				.andExpect(jsonPath("$.capacidad").value(4))
				.andExpect(jsonPath("$.precioNoche").value(250000))
				.andExpect(jsonPath("$.reglas").value("No fumar"))
				.andExpect(jsonPath("$.horaEntrada").value("15:00"))
				.andExpect(jsonPath("$.amenidades.length()").value(2))
				.andExpect(jsonPath("$.nombrePolitica").value("Flexible"))
				.andExpect(jsonPath("$.nombreAnfitrion").value("Usuario de prueba"))
				.andExpect(jsonPath("$.imagenes.length()").value(2))
				.andExpect(jsonPath("$.detalleImagenes[0].principal").value(true))
				.andExpect(jsonPath("$.detalleImagenes[1].principal").value(false))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		Long id = ((Number) JsonPath.read(respuesta, "$.idAlojamiento")).longValue();
		Alojamiento guardado = alojamientoRepository.findById(id).orElseThrow();
		assertThat(guardado.getIdAnfitrion()).isEqualTo(idAnfitrion);
		assertThat(guardado.getAmenidades()).containsExactlyInAnyOrder("wifi", "piscina");
		assertThat(jdbc.queryForObject(
				"SELECT estado_nuevo FROM historial_estado_alojamiento WHERE id_alojamiento = ?", String.class, id))
				.isEqualTo("BORRADOR");
	}

	@Test
	@DisplayName("CA-097 Un borrador sin la información mínima indica los datos que le faltan para enviarlo a revisión")
	void indicaLosDatosFaltantes_CA097() throws Exception {
		String respuesta = crear(anfitrion, "{\"titulo\":\"Solo título\",\"ciudad\":\"Bogotá\"}", imagen("a.jpg"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Long id = ((Number) JsonPath.read(respuesta, "$.idAlojamiento")).longValue();

		assertThat(publicacionService.camposFaltantes(alojamientoRepository.findById(id).orElseThrow()))
				.containsExactly("descripción", "ubicación", "tipo", "reglas", "capacidad", "habitaciones", "baños",
						"hora de entrada", "hora de salida", "precio por noche", "política de cancelación",
						"fechas de disponibilidad desde hoy");
	}

	@Test
	@DisplayName("CA-098 Una capacidad o un precio iguales o menores que cero se rechazan e informan que deben ser mayores que cero")
	void capacidadYPrecioMayoresQueCero_CA098() throws Exception {
		crear(anfitrion, "{\"titulo\":\"Casa\",\"capacidad\":0}", imagen("a.jpg"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("La capacidad debe ser mayor que cero."));
		crear(anfitrion, "{\"titulo\":\"Casa\",\"precioNoche\":-5}", imagen("a.jpg"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El precio por noche debe ser positivo y de máximo 1.000.000.000."));
		crear(anfitrion, "{\"titulo\":\"Casa\",\"precioNoche\":0}", imagen("a.jpg"))
				.andExpect(status().isBadRequest());

		assertThat(alojamientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Solo un anfitrión con sesión puede crear publicaciones")
	void soloAnfitriones() throws Exception {
		String huesped = jwtUtil.generateToken(crear("huesped@correo.com", Role.HUESPED));

		mockMvc.perform(multipart("/alojamientos").file(datos("{\"titulo\":\"Casa\"}")).file(imagen("a.jpg")))
				.andExpect(status().isUnauthorized());
		crear(huesped, "{\"titulo\":\"Casa\"}", imagen("a.jpg"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("No tienes permisos para esta operación"));

		assertThat(alojamientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Rechaza el título faltante, el JSON inválido, la falta de imágenes y los archivos que no son imágenes")
	void rechazaDatosEImagenesInvalidos() throws Exception {
		crear(anfitrion, "{\"descripcion\":\"Sin título\"}", imagen("a.jpg"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("El título es obligatorio."));
		crear(anfitrion, "no es json", imagen("a.jpg"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers
						.startsWith("El campo 'datos' debe ser el JSON del alojamiento:")));
		crear(anfitrion, "{\"titulo\":\"Casa\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Debes enviar al menos una imagen."));
		crear(anfitrion, "{\"titulo\":\"Casa\"}",
				new MockMultipartFile("archivos", "archivo.pdf", "image/jpeg", "%PDF-1.4".getBytes()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Solo se aceptan imágenes JPG, PNG o WEBP. (archivo.pdf)"));
		mockMvc.perform(multipart("/alojamientos").file(imagen("a.jpg")).header("Authorization", "Bearer " + anfitrion))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Falta el campo datos con los archivos."));
		mockMvc.perform(post("/alojamientos").header("Authorization", "Bearer " + anfitrion)
				.contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"Casa\"}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.message").value("El cuerpo debe enviarse como multipart/form-data."));

		assertThat(alojamientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Si Cloudinary no recibe la imagen responde 503 y no queda la publicación a medias")
	void cloudinaryNoDisponible() throws Exception {
		when(cloudinaryService.subir(any(), any(), any(), any()))
				.thenThrow(new AlmacenamientoNoDisponibleException("No se pudo guardar la imagen. Intenta de nuevo."));

		crear(anfitrion, "{\"titulo\":\"Casa\"}", imagen("a.jpg"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("No se pudo guardar la imagen. Intenta de nuevo."));

		assertThat(alojamientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Las políticas de cancelación son públicas")
	void politicasPublicas() throws Exception {
		politica("Flexible");

		mockMvc.perform(get("/alojamientos/politicas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nombre").value("Flexible"))
				.andExpect(jsonPath("$[0].diasReembolsoTotal").value(7));
	}

	@Test
	@DisplayName("La documentación de Swagger incluye la creación de publicaciones y las políticas")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/alojamientos'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['/alojamientos/politicas'].get").exists());
	}

	/**
	 * Envía la creación de una publicación.
	 *
	 * @param token    JWT del usuario
	 * @param json     JSON del campo datos
	 * @param archivos imágenes
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions crear(String token, String json, MockMultipartFile... archivos) throws Exception {
		MockMultipartHttpServletRequestBuilder peticion = multipart("/alojamientos").file(datos(json));
		for (MockMultipartFile archivo : archivos) {
			peticion.file(archivo);
		}
		return mockMvc.perform(peticion.header("Authorization", "Bearer " + token));
	}

	/**
	 * Campo datos del formulario.
	 *
	 * @param json JSON del alojamiento
	 * @return parte del formulario
	 */
	private static MockMultipartFile datos(String json) {
		return new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Imagen JPEG de prueba.
	 *
	 * @param nombre nombre del archivo
	 * @return parte del formulario
	 */
	private static MockMultipartFile imagen(String nombre) {
		return new MockMultipartFile("archivos", nombre, "image/jpeg", JPEG);
	}

	/**
	 * Guarda una política de cancelación.
	 *
	 * @param nombre nombre de la política
	 * @return id de la política
	 */
	private Long politica(String nombre) {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre(nombre);
		politica.setCondiciones("Reembolso total hasta 7 días antes");
		politica.setDiasReembolsoTotal(7);
		politica.setDiasReembolsoParcial(2);
		politica.setPorcentajeParcial(50);
		return politicaRepository.save(politica).getIdPolitica();
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
