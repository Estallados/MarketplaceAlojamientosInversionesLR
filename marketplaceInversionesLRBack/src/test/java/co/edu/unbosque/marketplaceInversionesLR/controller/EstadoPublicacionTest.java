package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-17 Enviar publicación a revisión y gestionar su estado sobre
 * la API completa y un PostgreSQL temporal en Docker. Cloudinary se simula.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class EstadoPublicacionTest {

	/** Primeros bytes de un JPEG válido. */
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private PeriodoDisponibilidadRepository periodoRepository;

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
		periodoRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		jdbc.update("DELETE FROM politica_cancelacion");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-157 Con los datos completos, enviar a revisión deja la publicación en PENDIENTE_REVISION")
	void debeEnviarARevision_CA157() throws Exception {
		Long id = crearCompleto();
		ofrecerFechas(id);

		cambiar(id, "enviarrevision")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));
		mockMvc.perform(get("/alojamientos/" + id + "/historial").header("Authorization", "Bearer " + anfitrion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[1].estadoAnterior").value("BORRADOR"))
				.andExpect(jsonPath("$[1].estadoNuevo").value("PENDIENTE_REVISION"));
	}

	@Test
	@DisplayName("Sin fechas de disponibilidad no se puede enviar a revisión")
	void sinFechasNoSePuedeEnviar() throws Exception {
		Long id = crearCompleto();

		cambiar(id, "enviarrevision")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("fechas de disponibilidad")));
	}

	@Test
	@DisplayName("CA-104 Solo se permiten las transiciones definidas")
	void soloSePermitenLasTransicionesDefinidas_CA104() throws Exception {
		Long id = crearCompleto();

		cambiar(id, "pausar")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("BORRADOR a PAUSADA")));
	}

	@Test
	@DisplayName("CA-108 Pausar una publicación ACTIVA la deja PAUSADA y se puede reanudar")
	void pausarCambiaAPausada_CA108() throws Exception {
		Long id = crearCompleto();
		Alojamiento activa = alojamientoRepository.findById(id).orElseThrow();
		activa.setEstado(EstadoAlojamiento.ACTIVA);
		alojamientoRepository.save(activa);

		cambiar(id, "pausar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAUSADA"));
		cambiar(id, "reanudar")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVA"));
	}

	/**
	 * Envía un cambio de estado como el anfitrión propietario.
	 *
	 * @param id     publicación
	 * @param accion enviarrevision, pausar o reanudar
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions cambiar(Long id, String accion) throws Exception {
		return mockMvc.perform(post("/alojamientos/" + id + "/" + accion).header("Authorization", "Bearer " + anfitrion));
	}

	/**
	 * Crea un borrador con la información mínima completa.
	 *
	 * @return id de la publicación
	 * @throws Exception si falla la petición
	 */
	private Long crearCompleto() throws Exception {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre("Flexible");
		politica.setCondiciones("Reembolso total hasta 7 días antes");
		politica.setDiasReembolsoTotal(7);
		politica.setDiasReembolsoParcial(2);
		politica.setPorcentajeParcial(50);
		Long idPolitica = politicaRepository.save(politica).getIdPolitica();
		String datos = """
				{"titulo":"Casa en el lago","descripcion":"Vista al lago","ubicacion":"Calle 1 # 2-3",
				 "ciudad":"Guatapé","latitud":6.23,"longitud":-75.15,"tipo":"Casa","capacidad":4,
				 "precioNoche":250000,"habitaciones":2,"camas":3,"banos":2,"horaEntrada":"15:00",
				 "horaSalida":"11:00","reglas":"No fumar","idPolitica":%d}
				""".formatted(idPolitica);
		String respuesta = mockMvc.perform(multipart("/alojamientos")
				.file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
						datos.getBytes(StandardCharsets.UTF_8)))
				.file(new MockMultipartFile("archivos", "a.jpg", "image/jpeg", JPEG))
				.header("Authorization", "Bearer " + anfitrion))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(respuesta, "$.idAlojamiento")).longValue();
	}

	/**
	 * Guarda un período disponible desde hoy.
	 *
	 * @param id publicación
	 */
	private void ofrecerFechas(Long id) {
		PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
		periodo.setIdAlojamiento(id);
		periodo.setFechaInicio(LocalDate.now());
		periodo.setFechaFin(LocalDate.now().plusDays(30));
		periodoRepository.save(periodo);
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
