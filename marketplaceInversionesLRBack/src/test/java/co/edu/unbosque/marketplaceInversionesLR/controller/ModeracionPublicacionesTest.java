package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-32 Revisar, aprobar o rechazar publicaciones sobre la API
 * completa y un PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ModeracionPublicacionesTest {

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

	/** Ruta base de la moderación. */
	private static final String RUTA = "/admin/alojamientos/";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private PeriodoDisponibilidadRepository periodoRepository;

	@Autowired
	private NotificacionRepository notificacionRepository;

	@Autowired
	private ReservaRepository reservaRepository;

	@Autowired
	private AuditoriaService auditoriaService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Administrador que decide. */
	private Usuario admin;

	/** Token del administrador. */
	private String tokenAdmin;

	/** Anfitrión dueño de la publicación. */
	private Usuario anfitrion;

	/** Token del anfitrión. */
	private String tokenAnfitrion;

	/** Publicación en PENDIENTE_REVISION con un período disponible. */
	private Long idAlojamiento;

	@BeforeEach
	void preparar() {
		limpiar();
		admin = crearUsuario("admin@correo.com", Role.ADMIN);
		tokenAdmin = jwtUtil.generateToken(admin);
		anfitrion = crearUsuario("anfitrion@correo.com", Role.ANFITRION);
		tokenAnfitrion = jwtUtil.generateToken(anfitrion);
		idAlojamiento = crearEnRevision(anfitrion.getIdUsuario());
	}

	/**
	 * Borra en orden de llaves foráneas: notificaciones, historial y períodos
	 * antes que los alojamientos, y estos antes que los usuarios.
	 */
	@AfterEach
	void limpiar() {
		reservaRepository.deleteAll();
		notificacionRepository.deleteAll();
		periodoRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-158 CA-196 El administrador ve la cola de revisión, aprueba la publicación y queda ACTIVA y "
			+ "disponible; se avisa al anfitrión y la decisión queda auditada")
	void apruebaLaPublicacion_CA158_CA196() throws Exception {
		mockMvc.perform(get("/admin/alojamientos").param("estado", "PENDIENTE_REVISION")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[0].idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.contenido[0].estado").value("PENDIENTE_REVISION"))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.pagina").value(0));

		decidir("aprobar", null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVA"));

		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.ACTIVA);
		// Queda disponible para cualquier huésped, sin sesión
		mockMvc.perform(get("/alojamientos/" + idAlojamiento + "/disponibilidad/consultar")
				.param("llegada", BASE.toString()).param("salida", BASE.plusDays(2).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(true));
		assertThat(notificacionRepository.findByIdUsuarioOrderByFechaDescIdNotificacionDesc(anfitrion.getIdUsuario()))
				.singleElement().satisfies(n -> {
					assertThat(n.getTipo()).isEqualTo(NotificacionService.ALOJAMIENTO_APROBADO);
					assertThat(n.getLeida()).isFalse();
				});
		// CA-196: administrador, publicación, fecha y resultado
		assertThat(auditoriaService.consultar(AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO, admin.getIdUsuario(), null,
				null, 0, 10).getContent()).anySatisfy(evento -> {
					assertThat(evento.getIdEntidad()).isEqualTo(idAlojamiento);
					assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITOSO);
					assertThat(evento.getFecha()).isNotNull();
				});
		// Ya no está en la cola
		mockMvc.perform(get("/admin/alojamientos").param("estado", "PENDIENTE_REVISION")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	@DisplayName("CA-195 CA-196 El administrador rechaza con motivo: vuelve a BORRADOR, el anfitrión ve el motivo y "
			+ "recibe el aviso, y la decisión queda auditada")
	void rechazaConMotivo_CA195_CA196() throws Exception {
		decidir("rechazar", "Las fotos no corresponden al alojamiento")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("BORRADOR"));

		mockMvc.perform(get("/alojamientos/" + idAlojamiento + "/historial")
				.header("Authorization", "Bearer " + tokenAnfitrion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].estadoNuevo").value("BORRADOR"))
				.andExpect(jsonPath("$[0].idUsuario").value(admin.getIdUsuario()))
				.andExpect(jsonPath("$[0].motivo")
						.value(PublicacionService.RECHAZADA + "Las fotos no corresponden al alojamiento"));
		assertThat(notificacionRepository.findByIdUsuarioOrderByFechaDescIdNotificacionDesc(anfitrion.getIdUsuario()))
				.singleElement().satisfies(n -> {
					assertThat(n.getTipo()).isEqualTo(NotificacionService.ALOJAMIENTO_RECHAZADO);
					assertThat(n.getMensaje()).contains("Las fotos no corresponden al alojamiento");
				});
		assertThat(auditoriaService.consultar(AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO, admin.getIdUsuario(), null,
				null, 0, 10).getContent()).anySatisfy(evento -> {
					assertThat(evento.getIdEntidad()).isEqualTo(idAlojamiento);
					assertThat(evento.getDetalle()).contains("Las fotos no corresponden al alojamiento");
				});

		// El anfitrión puede corregir y volver a enviar a revisión: el estado lo permite
		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.BORRADOR);
	}

	@Test
	@DisplayName("CA-195 Un motivo de 500 caracteres se guarda completo; sin motivo o con más, se responde 400")
	void motivoLargo_CA195() throws Exception {
		decidir("rechazar", "   ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.MOTIVO_INVALIDO));
		decidir("rechazar", "x".repeat(501)).andExpect(status().isBadRequest());
		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);

		decidir("rechazar", "x".repeat(500)).andExpect(status().isOk());
	}

	@Test
	@DisplayName("CA-196 Una decisión sobre algo que no está en revisión responde 409 y queda auditada")
	void soloDesdeRevision_CA196() throws Exception {
		decidir("aprobar", null).andExpect(status().isOk());

		decidir("aprobar", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("No se puede pasar de ACTIVA a ACTIVA."));
		decidir("rechazar", "Motivo").andExpect(status().isConflict());
		assertThat(auditoriaService.consultar(AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO, admin.getIdUsuario(), null,
				null, 0, 10).getContent())
				.anySatisfy(evento -> assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.RECHAZADO));
	}

	@Test
	@DisplayName("CA-158 No se aprueba si el anfitrión borró sus fechas mientras estaba en revisión")
	void sinFechasNoSeAprueba_CA158() throws Exception {
		periodoRepository.deleteAll();

		decidir("aprobar", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(PublicacionService.SIN_FECHAS));
	}

	@Test
	@DisplayName("Solo el administrador modera: el anfitrión recibe 403 y sin token 401")
	void soloElAdministrador() throws Exception {
		mockMvc.perform(post(RUTA + idAlojamiento + "/aprobar").header("Authorization", "Bearer " + tokenAnfitrion))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/alojamientos").header("Authorization", "Bearer " + tokenAnfitrion))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(RUTA + idAlojamiento + "/aprobar")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/alojamientos").param("estado", "NO_EXISTE")
				.header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest());
		decidirSobre(999999L, "aprobar", null).andExpect(status().isNotFound());
		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
	}

	@Test
	@DisplayName("CA-054 Al bloquear queda BLOQUEADA, sale de la búsqueda y ya no se puede cotizar para reservar")
	void bloquea_CA054() throws Exception {
		decidir("aprobar", null).andExpect(status().isOk());
		mockMvc.perform(get("/alojamientos")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1));

		decidir("bloquear", null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.MOTIVO_BLOQUEO));
		decidir("bloquear", "Publicidad falsa").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("BLOQUEADA"));

		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.BLOQUEADA);
		mockMvc.perform(get("/alojamientos")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
		String tokenHuesped = jwtUtil.generateToken(crearUsuario("huesped@correo.com", Role.HUESPED));
		mockMvc.perform(post("/cotizaciones").header("Authorization", "Bearer " + tokenHuesped)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idAlojamiento\":" + idAlojamiento + ",\"fechaEntrada\":\"" + BASE
						+ "\",\"fechaSalida\":\"" + BASE.plusDays(2) + "\",\"huespedes\":1}"))
				.andExpect(status().isConflict());
		decidir("bloquear", "Otra vez").andExpect(status().isConflict());
	}

	@Test
	@DisplayName("CA-112 Al bloquear se conservan las reservas futuras en revisión y las pasadas no cambian")
	void conservaReservas_CA112() throws Exception {
		decidir("aprobar", null).andExpect(status().isOk());
		Long idHuesped = crearUsuario("huesped@correo.com", Role.HUESPED).getIdUsuario();
		Long futura = crearReserva(idHuesped, BASE.plusDays(5), 1L);
		Long pasada = crearReserva(idHuesped, LocalDate.now().minusDays(10), 2L);

		decidir("bloquear", "Revisión").andExpect(status().isOk());

		assertThat(reservaRepository.findById(futura).orElseThrow().getEstado()).isEqualTo(EstadoReserva.EN_REVISION);
		assertThat(reservaRepository.findById(pasada).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(notificacionRepository.findAll()).extracting(Notificacion::getTipo)
				.contains(NotificacionService.RESERVA_EN_REVISION, NotificacionService.ALOJAMIENTO_BLOQUEADO);
	}

	@Test
	@DisplayName("CA-054 Solo el administrador bloquea: el anfitrión recibe 403 y sin token 401")
	void bloquearSoloAdministrador_CA054() throws Exception {
		mockMvc.perform(post(RUTA + idAlojamiento + "/bloquear").header("Authorization", "Bearer " + tokenAnfitrion)
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Motivo\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(RUTA + idAlojamiento + "/bloquear").contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\":\"Motivo\"}")).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Desbloquear pasa de BLOQUEADA a PAUSADA, queda en el historial y la auditoría; solo el administrador "
			+ "y solo desde BLOQUEADA")
	void desbloquea() throws Exception {
		decidir("aprobar", null).andExpect(status().isOk());
		decidir("desbloquear", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("No se puede pasar de ACTIVA a PAUSADA."));
		decidir("bloquear", "Publicidad falsa").andExpect(status().isOk());

		mockMvc.perform(post(RUTA + idAlojamiento + "/desbloquear").header("Authorization", "Bearer " + tokenAnfitrion))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(RUTA + idAlojamiento + "/desbloquear")).andExpect(status().isUnauthorized());
		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.BLOQUEADA);

		// Sin cuerpo, como lo envía el frontend
		mockMvc.perform(post(RUTA + idAlojamiento + "/desbloquear").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.estado").value("PAUSADA"));

		assertThat(alojamientoRepository.findById(idAlojamiento).orElseThrow().getEstado())
				.isEqualTo(EstadoAlojamiento.PAUSADA);
		mockMvc.perform(get("/alojamientos/" + idAlojamiento + "/historial")
				.header("Authorization", "Bearer " + tokenAnfitrion))
				// Del más antiguo al más reciente: aprobada, bloqueada y desbloqueada
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[2].estadoAnterior").value("BLOQUEADA"))
				.andExpect(jsonPath("$[2].estadoNuevo").value("PAUSADA"))
				.andExpect(jsonPath("$[2].idUsuario").value(admin.getIdUsuario()))
				.andExpect(jsonPath("$[2].motivo").value(PublicacionService.DESBLOQUEADA));
		assertThat(auditoriaService.consultar(AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO, admin.getIdUsuario(), null,
				null, 0, 20).getContent()).anySatisfy(evento -> {
					assertThat(evento.getIdEntidad()).isEqualTo(idAlojamiento);
					assertThat(evento.getDetalle()).startsWith("BLOQUEADA -> PAUSADA");
				});
		decidir("desbloquear", null).andExpect(status().isConflict());
		decidirSobre(999999L, "desbloquear", null).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("CA-112 El administrador ve en GET /admin/reservas las reservas que el bloqueo dejó EN_REVISION; al "
			+ "desbloquear siguen ahí")
	void reservasEnRevision_CA112() throws Exception {
		decidir("aprobar", null).andExpect(status().isOk());
		Long idHuesped = crearUsuario("huesped@correo.com", Role.HUESPED).getIdUsuario();
		Long futura = crearReserva(idHuesped, BASE.plusDays(5), 1L);
		Long pasada = crearReserva(idHuesped, LocalDate.now().minusDays(10), 2L);
		decidir("bloquear", "Revisión").andExpect(status().isOk());

		mockMvc.perform(get("/admin/reservas").param("estado", "EN_REVISION")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].idReserva").value(futura))
				.andExpect(jsonPath("$.contenido[0].estado").value("EN_REVISION"))
				.andExpect(jsonPath("$.contenido[0].nombreHuesped").value("Usuario de prueba"))
				.andExpect(jsonPath("$.contenido[0].tituloAlojamiento").value("Casa en el lago"))
				.andExpect(jsonPath("$.contenido[0].nombreAnfitrion").value("Usuario de prueba"))
				.andExpect(jsonPath("$.contenido[0].fechaEntrada").value(BASE.plusDays(5).toString()))
				.andExpect(jsonPath("$.contenido[0].noches").value(2))
				.andExpect(jsonPath("$.contenido[0].total").value(400000))
				.andExpect(jsonPath("$.contenido[0].completada").value(false));
		// Sin filtro trae todas; la pasada sigue CONFIRMADA y ya está completada
		mockMvc.perform(get("/admin/reservas").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(jsonPath("$.totalElementos").value(2));
		mockMvc.perform(get("/admin/reservas").param("estado", "CONFIRMADA")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(jsonPath("$.contenido[0].idReserva").value(pasada))
				.andExpect(jsonPath("$.contenido[0].completada").value(true));

		// Desbloquear no cambia las reservas en revisión
		decidir("desbloquear", null).andExpect(status().isOk());
		mockMvc.perform(get("/admin/reservas").param("estado", "EN_REVISION")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(jsonPath("$.totalElementos").value(1));

		mockMvc.perform(get("/admin/reservas").param("estado", "NO_EXISTE")
				.header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/admin/reservas").header("Authorization", "Bearer " + tokenAnfitrion))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/reservas")).andExpect(status().isUnauthorized());
	}

	private Long crearReserva(Long idHuesped, LocalDate entrada, Long idCotizacion) {
		Reserva reserva = new Reserva();
		reserva.setIdHuesped(idHuesped);
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setFechaEntrada(entrada);
		reserva.setFechaSalida(entrada.plusDays(2));
		reserva.setNoches(2);
		reserva.setHuespedes(1);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setTotal(new BigDecimal("400000"));
		reserva.setIdCotizacion(idCotizacion);
		reserva.setFechaCreacion(LocalDateTime.now());
		return reservaRepository.save(reserva).getIdReserva();
	}

	private ResultActions decidir(String accion, String motivo) throws Exception {
		return decidirSobre(idAlojamiento, accion, motivo);
	}

	private ResultActions decidirSobre(Long id, String accion, String motivo) throws Exception {
		String cuerpo = motivo == null ? "{}" : "{\"motivo\": \"" + motivo + "\"}";
		return mockMvc.perform(post(RUTA + id + "/" + accion).header("Authorization", "Bearer " + tokenAdmin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/**
	 * Guarda una publicación completa en PENDIENTE_REVISION con un período
	 * disponible de dos meses desde la fecha base.
	 *
	 * @param idAnfitrion dueño
	 * @return id del alojamiento
	 */
	private Long crearEnRevision(Long idAnfitrion) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setIdAnfitrion(idAnfitrion);
		alojamiento.setEstado(EstadoAlojamiento.PENDIENTE_REVISION);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		Long id = alojamientoRepository.save(alojamiento).getIdAlojamiento();

		PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
		periodo.setIdAlojamiento(id);
		periodo.setFechaInicio(BASE);
		periodo.setFechaFin(BASE.plusMonths(2));
		periodoRepository.save(periodo);
		return id;
	}

	/**
	 * Guarda un usuario activo y verificado con el rol indicado.
	 *
	 * @param correo correo de la cuenta
	 * @param rol    rol de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crearUsuario(String correo, Role rol) {
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
