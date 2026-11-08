package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-20 Registrar períodos de disponibilidad sobre la API completa
 * y un PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class DisponibilidadTest {

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private PeriodoDisponibilidadRepository periodoRepository;

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

	/** Anfitrión dueño del alojamiento. */
	private Usuario anfitrion;

	/** Token del anfitrión dueño. */
	private String tokenAnfitrion;

	/** Alojamiento de prueba, en BORRADOR. */
	private Long idAlojamiento;

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = crearUsuario("anfitrion@correo.com", Role.ANFITRION);
		tokenAnfitrion = jwtUtil.generateToken(anfitrion);
		idAlojamiento = crearAlojamiento(anfitrion.getIdUsuario(), EstadoAlojamiento.BORRADOR);
	}

	/**
	 * Borra en orden de llaves foráneas: reservas y períodos antes que los
	 * alojamientos, y estos antes que los usuarios.
	 */
	@AfterEach
	void limpiar() {
		reservaRepository.deleteAll();
		periodoRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-115 El anfitrión registra un período válido y queda guardado y asociado al alojamiento")
	void registraPeriodo_CA115() throws Exception {
		registrar(tokenAnfitrion, idAlojamiento, BASE, BASE.plusDays(10))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idPeriodo").isNumber())
				.andExpect(jsonPath("$.idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.fechaInicio").value(BASE.toString()))
				.andExpect(jsonPath("$.fechaFin").value(BASE.plusDays(10).toString()));

		assertThat(periodoRepository.findByIdAlojamientoOrderByFechaInicio(idAlojamiento)).singleElement()
				.satisfies(p -> {
					assertThat(p.getFechaInicio()).isEqualTo(BASE);
					assertThat(p.getFechaFin()).isEqualTo(BASE.plusDays(10));
				});
		assertThat(auditoriaService.consultar(AuditoriaService.DISPONIBILIDAD, anfitrion.getIdUsuario(), null, null, 0,
				10).getContent()).isNotEmpty();

		// El dueño ve el período en el calendario aunque la publicación esté en BORRADOR
		calendario(tokenAnfitrion, idAlojamiento)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estadoAlojamiento").value("BORRADOR"))
				.andExpect(jsonPath("$.periodos.length()").value(1))
				.andExpect(jsonPath("$.reservados.length()").value(0));
	}

	@Test
	@DisplayName("Rechaza fechas inválidas y períodos que se cruzan, sin guardar nada")
	void rechazaFechasInvalidasYCruces() throws Exception {
		registrar(tokenAnfitrion, idAlojamiento, BASE, BASE.plusDays(10)).andExpect(status().isCreated());

		registrar(tokenAnfitrion, idAlojamiento, BASE.plusDays(5), BASE.plusDays(5))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message")
						.value("Las fechas son inválidas: el fin del período debe ser posterior al inicio."));
		// Empieza el mismo día en que termina el otro: las dos fechas cuentan, así que se cruzan
		registrar(tokenAnfitrion, idAlojamiento, BASE.plusDays(10), BASE.plusDays(15))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(CalendarioService.CRUCE_AL_CREAR));
		registrar(tokenAnfitrion, idAlojamiento, BASE.plusDays(11), BASE.plusDays(15)).andExpect(status().isCreated());

		assertThat(periodoRepository.findByIdAlojamientoOrderByFechaInicio(idAlojamiento)).hasSize(2);
	}

	@Test
	@DisplayName("CA-117 El anfitrión modifica un período no reservado y el calendario muestra la disponibilidad actualizada")
	void modificaPeriodo_CA117() throws Exception {
		Long idPeriodo = idDe(registrar(tokenAnfitrion, idAlojamiento, BASE, BASE.plusDays(10)));

		modificar(tokenAnfitrion, idAlojamiento, idPeriodo, BASE.plusDays(2), BASE.plusDays(20))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idPeriodo").value(idPeriodo))
				.andExpect(jsonPath("$.fechaInicio").value(BASE.plusDays(2).toString()))
				.andExpect(jsonPath("$.fechaFin").value(BASE.plusDays(20).toString()));

		calendario(tokenAnfitrion, idAlojamiento)
				.andExpect(jsonPath("$.periodos[0].fechaInicio").value(BASE.plusDays(2).toString()))
				.andExpect(jsonPath("$.periodos[0].fechaFin").value(BASE.plusDays(20).toString()));
	}

	@Test
	@DisplayName("CA-118 Si el cambio afecta una reserva, el sistema rechaza la modificación y no cambia el período")
	void rechazaCambioQueAfectaReserva_CA118() throws Exception {
		Long idPeriodo = idDe(registrar(tokenAnfitrion, idAlojamiento, BASE, BASE.plusDays(10)));
		Usuario huesped = crearUsuario("huesped@correo.com", Role.HUESPED);
		crearReserva(huesped.getIdUsuario(), BASE.plusDays(3), BASE.plusDays(6), EstadoReserva.CONFIRMADA);

		modificar(tokenAnfitrion, idAlojamiento, idPeriodo, BASE, BASE.plusDays(4))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("No puedes modificar el período: afecta la reserva del "
						+ BASE.plusDays(3) + " al " + BASE.plusDays(6) + "."));
		eliminar(tokenAnfitrion, idAlojamiento, idPeriodo).andExpect(status().isConflict());

		assertThat(periodoRepository.findById(idPeriodo)).get()
				.satisfies(p -> assertThat(p.getFechaFin()).isEqualTo(BASE.plusDays(10)));
		// El calendario muestra el rango reservado
		calendario(tokenAnfitrion, idAlojamiento)
				.andExpect(jsonPath("$.reservados[0].fechaEntrada").value(BASE.plusDays(3).toString()))
				.andExpect(jsonPath("$.reservados[0].fechaSalida").value(BASE.plusDays(6).toString()));

		// Un cambio que conserva la reserva dentro sí se acepta
		modificar(tokenAnfitrion, idAlojamiento, idPeriodo, BASE.plusDays(1), BASE.plusDays(8))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Una reserva cancelada no impide modificar ni eliminar el período")
	void reservaCanceladaNoBloquea() throws Exception {
		Long idPeriodo = idDe(registrar(tokenAnfitrion, idAlojamiento, BASE, BASE.plusDays(10)));
		Usuario huesped = crearUsuario("huesped@correo.com", Role.HUESPED);
		crearReserva(huesped.getIdUsuario(), BASE.plusDays(3), BASE.plusDays(6), EstadoReserva.CANCELADA);

		modificar(tokenAnfitrion, idAlojamiento, idPeriodo, BASE, BASE.plusDays(2)).andExpect(status().isOk());
		eliminar(tokenAnfitrion, idAlojamiento, idPeriodo)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("Período eliminado."));
		assertThat(periodoRepository.findById(idPeriodo)).isEmpty();
	}

	@Test
	@DisplayName("Solo el anfitrión dueño o un administrador registran períodos; sin sesión responde 401")
	void permisos() throws Exception {
		Usuario otro = crearUsuario("otro@correo.com", Role.ANFITRION);
		Usuario huesped = crearUsuario("huesped@correo.com", Role.HUESPED);
		Usuario admin = crearUsuario("admin@correo.com", Role.ADMIN);

		registrar(jwtUtil.generateToken(otro), idAlojamiento, BASE, BASE.plusDays(3))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(CalendarioService.SIN_PERMISO));
		registrar(jwtUtil.generateToken(huesped), idAlojamiento, BASE, BASE.plusDays(3))
				.andExpect(status().isForbidden());
		mockMvc.perform(post(ruta(idAlojamiento)).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo(BASE, BASE.plusDays(3)))).andExpect(status().isUnauthorized());
		registrar(jwtUtil.generateToken(admin), idAlojamiento, BASE, BASE.plusDays(3))
				.andExpect(status().isCreated());
		registrar(tokenAnfitrion, 999_999L, BASE, BASE.plusDays(3))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(CalendarioService.NO_EXISTE));
	}

	@Test
	@DisplayName("El calendario es público si la publicación está ACTIVA; si no, responde 404 a terceros")
	void calendarioPublico() throws Exception {
		Long idActiva = crearAlojamiento(anfitrion.getIdUsuario(), EstadoAlojamiento.ACTIVA);

		mockMvc.perform(get(ruta(idActiva))).andExpect(status().isOk())
				.andExpect(jsonPath("$.estadoAlojamiento").value("ACTIVA"));
		mockMvc.perform(get(ruta(idAlojamiento))).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("La documentación de Swagger incluye las rutas de disponibilidad")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/alojamientos/{idAlojamiento}/disponibilidad'].post").exists())
				.andExpect(jsonPath("$.paths['/alojamientos/{idAlojamiento}/disponibilidad/{idPeriodo}'].put"
						+ ".responses['409']").exists());
	}

	/**
	 * Ruta de la disponibilidad de un alojamiento.
	 *
	 * @param id alojamiento
	 * @return ruta
	 */
	private static String ruta(Long id) {
		return "/alojamientos/" + id + "/disponibilidad";
	}

	/**
	 * JSON con las fechas de un período.
	 *
	 * @param inicio primer día
	 * @param fin    último día
	 * @return cuerpo de la petición
	 */
	private static String cuerpo(LocalDate inicio, LocalDate fin) {
		return "{\"fechaInicio\": \"" + inicio + "\", \"fechaFin\": \"" + fin + "\"}";
	}

	private ResultActions registrar(String token, Long id, LocalDate inicio, LocalDate fin) throws Exception {
		return mockMvc.perform(post(ruta(id)).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo(inicio, fin)));
	}

	private ResultActions modificar(String token, Long id, Long idPeriodo, LocalDate inicio, LocalDate fin)
			throws Exception {
		return mockMvc.perform(put(ruta(id) + "/" + idPeriodo).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo(inicio, fin)));
	}

	private ResultActions eliminar(String token, Long id, Long idPeriodo) throws Exception {
		return mockMvc.perform(delete(ruta(id) + "/" + idPeriodo).header("Authorization", "Bearer " + token));
	}

	private ResultActions calendario(String token, Long id) throws Exception {
		return mockMvc.perform(get(ruta(id)).header("Authorization", "Bearer " + token));
	}

	/**
	 * Id del período creado.
	 *
	 * @param creado respuesta del registro
	 * @return id del período
	 * @throws Exception si la respuesta no se puede leer
	 */
	private static Long idDe(ResultActions creado) throws Exception {
		Number id = JsonPath.read(creado.andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.idPeriodo");
		return id.longValue();
	}

	/**
	 * Guarda un alojamiento del anfitrión con el estado indicado.
	 *
	 * @param idAnfitrion dueño
	 * @param estado      estado de la publicación
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(Long idAnfitrion, EstadoAlojamiento estado) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setIdAnfitrion(idAnfitrion);
		alojamiento.setEstado(estado);
		alojamiento.setFechaCreacion(LocalDateTime.now());
		alojamiento.setFechaActualizacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	/**
	 * Guarda una reserva del alojamiento de prueba.
	 *
	 * @param idHuesped huésped
	 * @param entrada   día de llegada
	 * @param salida    día de salida
	 * @param estado    estado de la reserva
	 */
	private void crearReserva(Long idHuesped, LocalDate entrada, LocalDate salida, EstadoReserva estado) {
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setIdHuesped(idHuesped);
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(entrada);
		reserva.setFechaSalida(salida);
		reserva.setNoches((int) (salida.toEpochDay() - entrada.toEpochDay()));
		reserva.setHuespedes(2);
		reserva.setEstado(estado);
		reserva.setTotal(new BigDecimal("500000"));
		reserva.setFechaCreacion(LocalDateTime.now());
		reservaRepository.save(reserva);
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
