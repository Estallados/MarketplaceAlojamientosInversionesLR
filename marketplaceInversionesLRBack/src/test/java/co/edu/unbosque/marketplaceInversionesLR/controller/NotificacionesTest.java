package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-02 Recibir y consultar notificaciones sobre la API completa y un
 * PostgreSQL temporal en Docker: un evento real crea la notificación (CA-010) y
 * cada usuario ve, cuenta y marca solo las suyas (CA-013).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class NotificacionesTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private NotificacionRepository notificacionRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Usuario dueño de tres notificaciones (dos sin leer). */
	private Usuario ana;

	/** Token de Ana. */
	private String tokenAna;

	/** Otro usuario con dos notificaciones sin leer. */
	private Usuario beto;

	/** La notificación más reciente de Ana, sin leer. */
	private Long reciente;

	/** Una notificación de Beto. */
	private Long deBeto;

	@BeforeEach
	void preparar() {
		limpiar();
		ana = crearUsuario("ana.notificaciones@correo.com", Role.HUESPED, EstadoUsuario.ACTIVO);
		tokenAna = jwtUtil.generateToken(ana);
		beto = crearUsuario("beto.notificaciones@correo.com", Role.ANFITRION, EstadoUsuario.ACTIVO);

		notificacion(ana, NotificacionService.PAGO_NO_CONFIRMADO, "El pago no se confirmó.", 3, true);
		notificacion(ana, NotificacionService.RESERVA_CONFIRMADA, "Tu reserva 15 está confirmada.", 2, false);
		reciente = notificacion(ana, NotificacionService.RESERVA_CANCELADA, "Cancelaste la reserva 15.", 1, false);
		deBeto = notificacion(beto, NotificacionService.ALOJAMIENTO_APROBADO, "Tu alojamiento fue aprobado.", 1,
				false);
		notificacion(beto, NotificacionService.RESERVA_CONFIRMADA, "Tienes una reserva confirmada.", 2, false);
	}

	/** Borra en orden de llaves foráneas: notificaciones antes que usuarios. */
	@AfterEach
	void limpiar() {
		notificacionRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-010 Un evento real registra la notificación y el usuario la ve primero en su lista")
	void eventoCreaLaNotificacion_CA010() throws Exception {
		Usuario inactivo = crearUsuario("caro.notificaciones@correo.com", Role.HUESPED, EstadoUsuario.INACTIVO);
		Usuario admin = crearUsuario("admin.notificaciones@correo.com", Role.ADMIN, EstadoUsuario.ACTIVO);

		mockMvc.perform(patch("/admin/usuarios/" + inactivo.getIdUsuario() + "/liberar")
				.header("Authorization", "Bearer " + jwtUtil.generateToken(admin)))
				.andExpect(status().isOk());

		comoUsuario(get("/notificaciones"), jwtUtil.generateToken(inactivo))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].tipo").value(NotificacionService.CUENTA_LIBERADA))
				.andExpect(jsonPath("$.contenido[0].mensaje").value(UsuarioService.AVISO_LIBERADA))
				.andExpect(jsonPath("$.contenido[0].leida").value(false))
				.andExpect(jsonPath("$.contenido[0].idUsuario").value(inactivo.getIdUsuario()))
				.andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty());
	}

	@Test
	@DisplayName("CA-013 Cada usuario ve solo las suyas, de la más reciente a la más antigua")
	void soloLasPropias_CA013() throws Exception {
		comoAna(get("/notificaciones"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.contenido[*].tipo").value(contains(NotificacionService.RESERVA_CANCELADA,
						NotificacionService.RESERVA_CONFIRMADA, NotificacionService.PAGO_NO_CONFIRMADO)))
				.andExpect(jsonPath("$.contenido[*].idUsuario").value(everyItem(is(ana.getIdUsuario().intValue()))));

		comoAna(get("/notificaciones").param("tamanio", "2").param("pagina", "1"))
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].tipo").value(NotificacionService.PAGO_NO_CONFIRMADO))
				.andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.tamanio").value(2));
	}

	@Test
	@DisplayName("CA-013 El contador de la campana cuenta solo las propias sin leer")
	void contador_CA013() throws Exception {
		comoAna(get("/notificaciones/noleidas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.cantidad").value(2));
	}

	@Test
	@DisplayName("CA-013 Se marca como leída una propia; la de otro usuario responde 404 y no cambia")
	void marcarUna_CA013() throws Exception {
		comoAna(patch("/notificaciones/" + reciente + "/leida"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idNotificacion").value(reciente))
				.andExpect(jsonPath("$.leida").value(true));
		comoAna(get("/notificaciones/noleidas")).andExpect(jsonPath("$.cantidad").value(1));

		comoAna(patch("/notificaciones/" + deBeto + "/leida"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(NotificacionService.NO_EXISTE));
		comoAna(patch("/notificaciones/999999/leida")).andExpect(status().isNotFound());
		assertThat(notificacionRepository.findById(deBeto).orElseThrow().getLeida()).isFalse();
	}

	@Test
	@DisplayName("CA-013 Marcar todas solo cambia las propias y responde cuántas se marcaron")
	void marcarTodas_CA013() throws Exception {
		comoAna(patch("/notificaciones/leidas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.actualizadas").value(2));
		comoAna(get("/notificaciones/noleidas")).andExpect(jsonPath("$.cantidad").value(0));
		comoAna(patch("/notificaciones/leidas")).andExpect(jsonPath("$.actualizadas").value(0));

		assertThat(notificacionRepository.countByIdUsuarioAndLeidaFalse(beto.getIdUsuario())).isEqualTo(2);
	}

	@Test
	@DisplayName("Sin sesión las rutas de notificaciones responden 401")
	void sinSesion() throws Exception {
		mockMvc.perform(get("/notificaciones")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/notificaciones/noleidas")).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/notificaciones/leidas")).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/notificaciones/" + reciente + "/leida")).andExpect(status().isUnauthorized());
		assertThat(notificacionRepository.countByIdUsuarioAndLeidaFalse(ana.getIdUsuario())).isEqualTo(2);
	}

	/**
	 * Ejecuta la petición como Ana.
	 *
	 * @param peticion petición
	 * @return la respuesta
	 */
	private ResultActions comoAna(MockHttpServletRequestBuilder peticion) throws Exception {
		return comoUsuario(peticion, tokenAna);
	}

	/**
	 * Ejecuta la petición con el token indicado.
	 *
	 * @param peticion petición
	 * @param token    token JWT
	 * @return la respuesta
	 */
	private ResultActions comoUsuario(MockHttpServletRequestBuilder peticion, String token) throws Exception {
		return mockMvc.perform(peticion.header("Authorization", "Bearer " + token));
	}

	/**
	 * Guarda una notificación.
	 *
	 * @param usuario    destinatario
	 * @param tipo       evento
	 * @param mensaje    texto
	 * @param horasAtras hace cuántas horas se creó
	 * @param leida      si ya se leyó
	 * @return id de la notificación
	 */
	private Long notificacion(Usuario usuario, String tipo, String mensaje, int horasAtras, boolean leida) {
		Notificacion notificacion = new Notificacion();
		notificacion.setIdUsuario(usuario.getIdUsuario());
		notificacion.setTipo(tipo);
		notificacion.setMensaje(mensaje);
		notificacion.setFecha(LocalDateTime.now().minusHours(horasAtras));
		notificacion.setLeida(leida);
		return notificacionRepository.save(notificacion).getIdNotificacion();
	}

	/**
	 * Guarda un usuario verificado con el rol y el estado indicados.
	 *
	 * @param correo correo de la cuenta
	 * @param rol    rol de la cuenta
	 * @param estado estado de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crearUsuario(String correo, Role rol, EstadoUsuario estado) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("no-se-usa");
		usuario.setRol(rol);
		usuario.setEstado(estado);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
