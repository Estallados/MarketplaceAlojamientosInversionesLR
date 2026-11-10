package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.EventoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AuditoriaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConfiguracionTarifaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-26 Registro de operaciones relevantes de la plataforma sobre
 * la API completa y un PostgreSQL temporal en Docker. La tabla de auditoría
 * no se limpia entre pruebas (es de solo inserción), así que cada prueba
 * filtra los eventos por el usuario que crea.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class AuditoriaTest {

	/** Ruta de la configuración global de tarifas. */
	private static final String TARIFAS = "/admin/tarifas";

	/** Ruta de la consulta de la auditoría. */
	private static final String AUDITORIA = "/admin/auditoria";

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ConfiguracionTarifaRepository configuracionRepository;

	@Autowired
	private AuditoriaService auditoriaService;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/**
	 * Borra las configuraciones antes que los usuarios (llave foránea). También
	 * al terminar, para que las demás pruebas puedan borrar sus usuarios.
	 */
	@BeforeEach
	@AfterEach
	void limpiar() {
		configuracionRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-136 Registrarse y cambiar las tarifas generan cada uno su evento de auditoría")
	void operacionRelevanteGeneraEvento_CA136() throws Exception {
		mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\": \"Ana\", \"correo\": \"ana@correo.com\", \"contrasenia\": \"" + CONTRASENIA
						+ "\"}"))
				.andExpect(status().isCreated());
		Long idAna = usuarioRepository.findByCorreoHash(UsuarioService.hashCorreo("ana@correo.com")).orElseThrow()
				.getIdUsuario();

		assertThat(eventos(AuditoriaService.REGISTRO_USUARIO, idAna)).anySatisfy(evento -> {
			assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
			assertThat(evento.getEntidad()).isEqualTo(AuditoriaService.ENTIDAD_USUARIO);
			assertThat(evento.getIdEntidad()).isEqualTo(idAna);
		});

		Usuario admin = crear("admin@correo.com", Role.ADMIN);
		actualizarTarifas(jwtUtil.generateToken(admin), "{\"porcentajeLimpieza\": 6, \"porcentajeServicio\": 11}")
				.andExpect(status().isOk());

		assertThat(eventos(AuditoriaService.TARIFA_ACTUALIZADA, admin.getIdUsuario())).singleElement()
				.satisfies(evento -> {
					assertThat(evento.getEntidad()).isEqualTo(AuditoriaService.ENTIDAD_CONFIGURACION);
					assertThat(evento.getIdEntidad()).isNotNull();
					assertThat(evento.getDetalle()).isEqualTo("Limpieza 6%, servicio 11%");
				});
	}

	@Test
	@DisplayName("CA-137 El evento identifica al usuario autenticado, también cuando se le niega el acceso")
	void eventoIdentificaAlUsuario_CA137() throws Exception {
		Usuario anfitrion = crear("anfitrion@correo.com", Role.ANFITRION);
		Usuario huesped = crear("huesped@correo.com", Role.HUESPED);
		String tokenHuesped = jwtUtil.generateToken(huesped);

		// Publicación rechazada (sin imágenes): queda a nombre del anfitrión
		mockMvc.perform(multipart("/alojamientos").file(datos("{\"titulo\": \"Casa en la playa\"}"))
				.header("Authorization", "Bearer " + jwtUtil.generateToken(anfitrion)))
				.andExpect(status().isBadRequest());
		assertThat(eventos(AuditoriaService.ALOJAMIENTO_CREADO, anfitrion.getIdUsuario())).singleElement()
				.satisfies(evento -> {
					assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.RECHAZADO);
					assertThat(evento.getDetalle()).isNotBlank();
				});

		// 403 por la regla de la ruta (/admin/**) y por @PreAuthorize (solo anfitriones publican)
		actualizarTarifas(tokenHuesped, "{\"porcentajeLimpieza\": 5, \"porcentajeServicio\": 10}")
				.andExpect(status().isForbidden());
		mockMvc.perform(multipart("/alojamientos").file(datos("{\"titulo\": \"Casa\"}"))
				.header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isForbidden());

		assertThat(eventos(AuditoriaService.ACCESO_DENEGADO, huesped.getIdUsuario()))
				.extracting(EventoAuditoria::getDetalle)
				.containsExactlyInAnyOrder("POST /alojamientos", "PUT " + TARIFAS);
		assertThat(eventos(AuditoriaService.ACCESO_DENEGADO, huesped.getIdUsuario()))
				.allSatisfy(evento -> assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.RECHAZADO));
	}

	@Test
	@DisplayName("CA-138 El evento tiene la fecha de ejecución y el resultado de la operación")
	void eventoTieneFechaYResultado_CA138() throws Exception {
		Usuario admin = crear("admin@correo.com", Role.ADMIN);
		String token = jwtUtil.generateToken(admin);
		LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

		actualizarTarifas(token, "{\"porcentajeLimpieza\": 7, \"porcentajeServicio\": 12}").andExpect(status().isOk());
		actualizarTarifas(token, "{\"porcentajeLimpieza\": 7, \"porcentajeServicio\": 120}")
				.andExpect(status().isBadRequest());
		LocalDateTime despues = LocalDateTime.now().plusSeconds(1);

		List<EventoAuditoria> eventos = eventos(AuditoriaService.TARIFA_ACTUALIZADA, admin.getIdUsuario());
		assertThat(eventos).extracting(EventoAuditoria::getResultado)
				.containsExactlyInAnyOrder(ResultadoAuditoria.EXITOSO, ResultadoAuditoria.RECHAZADO);
		assertThat(eventos).allSatisfy(evento -> assertThat(evento.getFecha()).isBetween(antes, despues));

		EventoAuditoria detalle = auditoriaService.detalle(eventos.get(0).getIdEvento()).orElseThrow();
		assertThat(detalle.getFecha()).isNotNull();
		assertThat(detalle.getResultado()).isNotNull();
	}

	@Test
	@DisplayName("CA-139 Los eventos siguen disponibles para consultarlos después y no se pueden modificar ni borrar")
	void eventosDisponiblesParaRevision_CA139() throws Exception {
		Usuario admin = crear("admin@correo.com", Role.ADMIN);
		LocalDateTime inicio = LocalDateTime.now().minusSeconds(1);
		actualizarTarifas(jwtUtil.generateToken(admin), "{\"porcentajeLimpieza\": 3, \"porcentajeServicio\": 8}")
				.andExpect(status().isOk());

		// Una consulta posterior encuentra el evento, con filtros por acción, usuario y fechas
		assertThat(auditoriaService.consultar(AuditoriaService.TARIFA_ACTUALIZADA, admin.getIdUsuario(), inicio,
				LocalDateTime.now().plusSeconds(1), 0, 20).getContent()).hasSize(1);
		assertThat(auditoriaService.consultar(null, admin.getIdUsuario(), null, null, 0, 20).getContent())
				.isNotEmpty();
		assertThat(auditoriaService.consultar(AuditoriaService.TARIFA_ACTUALIZADA, admin.getIdUsuario(),
				LocalDateTime.now().plusDays(1), null, 0, 20).getContent()).isEmpty();

		// El repositorio solo inserta y consulta: no expone update ni delete
		assertThat(CrudRepository.class.isAssignableFrom(AuditoriaRepository.class)).isFalse();
		assertThat(Arrays.stream(AuditoriaRepository.class.getMethods()).map(Method::getName))
				.noneMatch(nombre -> nombre.startsWith("delete") || nombre.startsWith("update")
						|| nombre.startsWith("remove"));
	}

	@Test
	@DisplayName("CA-062 El administrador consulta los eventos con su información y el detalle de cada uno")
	void consultaLaAuditoria_CA062() throws Exception {
		Usuario admin = crear("admin@correo.com", Role.ADMIN);
		String token = jwtUtil.generateToken(admin);
		actualizarTarifas(token, "{\"porcentajeLimpieza\": 4, \"porcentajeServicio\": 9}").andExpect(status().isOk());

		String lista = mockMvc.perform(get(AUDITORIA).param("accion", "tarifa_actualizada")
				.param("idActor", admin.getIdUsuario().toString()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].accion").value(AuditoriaService.TARIFA_ACTUALIZADA))
				.andExpect(jsonPath("$.contenido[0].idUsuario").value(admin.getIdUsuario()))
				.andExpect(jsonPath("$.contenido[0].resultado").value("EXITOSO"))
				.andExpect(jsonPath("$.contenido[0].entidad").value(AuditoriaService.ENTIDAD_CONFIGURACION))
				.andExpect(jsonPath("$.contenido[0].detalle").value("Limpieza 4%, servicio 9%"))
				.andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		long idEvento = ((Number) JsonPath.read(lista, "$.contenido[0].idEvento")).longValue();

		mockMvc.perform(get(AUDITORIA + "/" + idEvento).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accion").value(AuditoriaService.TARIFA_ACTUALIZADA));
		mockMvc.perform(get(AUDITORIA + "/999999999").header("Authorization", "Bearer " + token))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(AuditoriaService.EVENTO_NO_EXISTE));
		mockMvc.perform(get(AUDITORIA).param("desde", "2099-01-01T00:00:00").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
		mockMvc.perform(get(AUDITORIA).param("desde", "ayer").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("CA-065 Sin permisos administrativos no se consulta la auditoría: 403 con otro rol y 401 sin token")
	void soloElAdministradorConsulta_CA065() throws Exception {
		String huesped = jwtUtil.generateToken(crear("huesped@correo.com", Role.HUESPED));
		String anfitrion = jwtUtil.generateToken(crear("anfitrion@correo.com", Role.ANFITRION));

		mockMvc.perform(get(AUDITORIA).header("Authorization", "Bearer " + huesped)).andExpect(status().isForbidden());
		mockMvc.perform(get(AUDITORIA).header("Authorization", "Bearer " + anfitrion)).andExpect(status().isForbidden());
		mockMvc.perform(get(AUDITORIA + "/1").header("Authorization", "Bearer " + huesped))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(AUDITORIA)).andExpect(status().isUnauthorized());
	}

	/**
	 * Eventos de una acción hechos por un usuario.
	 *
	 * @param accion    acción auditada
	 * @param idUsuario usuario que hizo la acción
	 * @return eventos encontrados
	 */
	private List<EventoAuditoria> eventos(String accion, Long idUsuario) {
		return auditoriaService.consultar(accion, idUsuario, null, null, 0, AuditoriaService.MAX_TAMANIO_PAGINA)
				.getContent();
	}

	/**
	 * Cambia los porcentajes globales.
	 *
	 * @param token  JWT del usuario
	 * @param cuerpo JSON con los porcentajes
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions actualizarTarifas(String token, String cuerpo)
			throws Exception {
		return mockMvc.perform(put(TARIFAS).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/**
	 * Campo {@code datos} del formulario de publicación.
	 *
	 * @param json JSON del alojamiento
	 * @return parte del formulario
	 */
	private static MockMultipartFile datos(String json) {
		return new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
				json.getBytes(StandardCharsets.UTF_8));
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
