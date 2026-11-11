package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.ResenaService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-03 Calificar, reseñar y consultar reseñas sobre la API completa
 * y un PostgreSQL temporal en Docker.
 * <p>
 * Datos: la casa de Carlos y la cabaña de Diana. Ana tiene en la casa una
 * reserva completada sin reseña y una futura. Beto tiene una reserva
 * completada en la casa con una reseña OCULTA y otra en la cabaña con una
 * reseña PUBLICADA de 4 estrellas.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ResenasTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private ReservaRepository reservaRepository;

	@Autowired
	private ResenaRepository resenaRepository;

	@Autowired
	private AuditoriaService auditoriaService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Huésped que reseña. */
	private Usuario ana;

	/** Huésped con reseñas previas. */
	private Usuario beto;

	/** Anfitrión de la casa. */
	private Usuario carlos;

	/** Anfitriona de la cabaña. */
	private Usuario diana;

	/** Casa de Carlos. */
	private Long casa;

	/** Cabaña de Diana. */
	private Long cabana;

	/** Reserva completada de Ana en la casa, sin reseña. */
	private Long anaCompletada;

	/** Reserva futura de Ana en la casa. */
	private Long anaFutura;

	/** Reserva completada de Beto en la casa (su reseña está OCULTA). */
	private Long betoEnCasa;

	@BeforeEach
	void preparar() {
		limpiar();
		ana = crearUsuario("ana.resenas@correo.com", "Ana Huésped", Role.HUESPED);
		beto = crearUsuario("beto.resenas@correo.com", "Beto Huésped", Role.HUESPED);
		carlos = crearUsuario("carlos.resenas@correo.com", "Carlos Anfitrión", Role.ANFITRION);
		diana = crearUsuario("diana.resenas@correo.com", "Diana Anfitriona", Role.ANFITRION);
		casa = crearAlojamiento(carlos, "Casa de Carlos");
		cabana = crearAlojamiento(diana, "Cabaña de Diana");

		LocalDate hoy = LocalDate.now();
		anaCompletada = crearReserva(ana, casa, hoy.minusDays(5), hoy.minusDays(2));
		anaFutura = crearReserva(ana, casa, hoy.plusDays(10), hoy.plusDays(13));
		betoEnCasa = crearReserva(beto, casa, hoy.minusDays(20), hoy.minusDays(17));
		Long betoEnCabana = crearReserva(beto, cabana, hoy.minusDays(9), hoy.minusDays(6));
		guardarResena(beto, betoEnCasa, casa, 1, EstadoResena.OCULTA, 3);
		guardarResena(beto, betoEnCabana, cabana, 4, EstadoResena.PUBLICADA, 2);
	}

	/** Borra en orden de llaves foráneas. */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM notificacion");
		resenaRepository.deleteAll();
		jdbc.update("DELETE FROM pago");
		jdbc.update("DELETE FROM cotizacion");
		reservaRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM servicio_adicional");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-016 CA-018 El huésped reseña su estancia completada: queda asociada a huésped, reserva, "
			+ "alojamiento, fecha y contenido, y se avisa al anfitrión")
	void reseniaCompletada_CA016_CA018() throws Exception {
		// El cliente intenta fijar otro alojamiento y otro autor: el servidor los ignora (CA-018)
		reseniar(ana, "{\"idReserva\": " + anaCompletada + ", \"calificacion\": 5, \"comentario\": \"  Excelente  \", "
				+ "\"idAlojamiento\": " + cabana + ", \"idUsuario\": " + beto.getIdUsuario() + "}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idResena").isNumber())
				.andExpect(jsonPath("$.idReserva").value(anaCompletada))
				.andExpect(jsonPath("$.idAlojamiento").value(casa))
				.andExpect(jsonPath("$.idUsuario").value(ana.getIdUsuario()))
				.andExpect(jsonPath("$.calificacion").value(5))
				.andExpect(jsonPath("$.comentario").value("Excelente"))
				.andExpect(jsonPath("$.estado").value("PUBLICADA"))
				.andExpect(jsonPath("$.fecha").isNotEmpty())
				.andExpect(jsonPath("$.nombreUsuario").value("Ana Huésped"))
				.andExpect(jsonPath("$.tituloAlojamiento").value("Casa de Carlos"));

		Resena guardada = resenaRepository.findAll().stream()
				.filter(r -> r.getIdReserva().equals(anaCompletada)).findFirst().orElseThrow();
		assertThat(guardada.getIdUsuario()).isEqualTo(ana.getIdUsuario());
		assertThat(guardada.getIdAlojamiento()).isEqualTo(casa);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE id_usuario = ? AND tipo = ?",
				Integer.class, carlos.getIdUsuario(), NotificacionService.RESENA_RECIBIDA)).isEqualTo(1);
		assertThat(auditoriaService.consultar(AuditoriaService.RESENA_CREADA, ana.getIdUsuario(), null, null, 0, 10)
				.getContent()).isNotEmpty();

		// El historial de reservas (HU-25) ya la muestra como reseñada
		como(ana, get("/reservas/" + anaCompletada)).andExpect(jsonPath("$.tieneResena").value(true))
				.andExpect(jsonPath("$.completada").value(true));

		// Solo una por reserva
		reseniar(ana, cuerpo(anaCompletada, 3)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(ResenaService.YA_RESENADA));
		assertThat(resenaRepository.count()).isEqualTo(3);
	}

	@Test
	@DisplayName("CA-016 No se reseña una reserva sin completar, ajena o con datos inválidos; el anfitrión no reseña")
	void rechazos_CA016() throws Exception {
		reseniar(ana, cuerpo(anaFutura, 5)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(ResenaService.NO_COMPLETADA));
		reseniar(ana, cuerpo(betoEnCasa, 5)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(ReservaService.NO_EXISTE));
		reseniar(ana, cuerpo(999999L, 5)).andExpect(status().isNotFound());
		reseniar(ana, cuerpo(anaCompletada, 6)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ResenaService.DATOS_INVALIDOS));
		reseniar(ana, "{\"calificacion\": 4}").andExpect(status().isBadRequest());
		reseniar(ana, "{\"idReserva\": " + anaCompletada + ", \"calificacion\": 4, \"comentario\": \""
				+ "x".repeat(2001) + "\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ResenaService.COMENTARIO_LARGO));
		reseniar(carlos, cuerpo(anaCompletada, 5)).andExpect(status().isForbidden());
		mockMvc.perform(post("/resenas").contentType(MediaType.APPLICATION_JSON).content(cuerpo(anaCompletada, 5)))
				.andExpect(status().isUnauthorized());

		assertThat(resenaRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("CA-019 La consulta pública de un alojamiento muestra solo sus reseñas PUBLICADA con promedio y cantidad")
	void consultaPorAlojamiento_CA019() throws Exception {
		mockMvc.perform(get("/alojamientos/" + cabana + "/resenas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idAlojamiento").value(cabana))
				.andExpect(jsonPath("$.promedio").value(4.0))
				.andExpect(jsonPath("$.cantidad").value(1))
				.andExpect(jsonPath("$.resenas.totalElementos").value(1))
				.andExpect(jsonPath("$.resenas.contenido[0].calificacion").value(4))
				.andExpect(jsonPath("$.resenas.contenido[0].nombreUsuario").value("Beto Huésped"))
				.andExpect(jsonPath("$.resenas.contenido[*].idAlojamiento").value(everyItem(is(cabana.intValue()))));

		// La de la casa está OCULTA: no se muestra ni cuenta en el promedio
		mockMvc.perform(get("/alojamientos/" + casa + "/resenas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cantidad").value(0))
				.andExpect(jsonPath("$.promedio").doesNotExist())
				.andExpect(jsonPath("$.resenas.contenido.length()").value(0));

		// Después de la reseña de Ana, la casa muestra solo la suya
		reseniar(ana, cuerpo(anaCompletada, 5)).andExpect(status().isCreated());
		mockMvc.perform(get("/alojamientos/" + casa + "/resenas"))
				.andExpect(jsonPath("$.cantidad").value(1))
				.andExpect(jsonPath("$.promedio").value(5.0))
				.andExpect(jsonPath("$.resenas.contenido[0].idReserva").value(anaCompletada));

		mockMvc.perform(get("/alojamientos/999999/resenas")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(CalendarioService.NO_EXISTE));
	}

	@Test
	@DisplayName("El huésped ve todas sus reseñas, incluidas las ocultas; el anfitrión solo las visibles de lo suyo")
	void misResenasYAnfitrion() throws Exception {
		como(beto, get("/resenas/mias"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[0].estado").value("PUBLICADA"))
				.andExpect(jsonPath("$.contenido[1].estado").value("OCULTA"));
		como(ana, get("/resenas/mias")).andExpect(jsonPath("$.totalElementos").value(0));

		como(diana, get("/resenas/anfitrion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cantidad").value(1))
				.andExpect(jsonPath("$.promedio").value(4.0))
				.andExpect(jsonPath("$.idAlojamiento").doesNotExist());
		como(carlos, get("/resenas/anfitrion")).andExpect(jsonPath("$.cantidad").value(0));
		como(diana, get("/resenas/anfitrion").param("idAlojamiento", cabana.toString()))
				.andExpect(jsonPath("$.idAlojamiento").value(cabana))
				.andExpect(jsonPath("$.cantidad").value(1));
		como(diana, get("/resenas/anfitrion").param("idAlojamiento", casa.toString()))
				.andExpect(status().isNotFound());

		como(ana, get("/resenas/anfitrion")).andExpect(status().isForbidden());
		como(carlos, get("/resenas/mias")).andExpect(status().isForbidden());
		mockMvc.perform(get("/resenas/mias")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/resenas/anfitrion")).andExpect(status().isUnauthorized());
	}

	/**
	 * Cuerpo con la reserva y la calificación.
	 *
	 * @param idReserva    reserva
	 * @param calificacion calificación
	 * @return JSON
	 */
	private static String cuerpo(Long idReserva, int calificacion) {
		return "{\"idReserva\": " + idReserva + ", \"calificacion\": " + calificacion + "}";
	}

	/**
	 * Envía una reseña como el usuario.
	 *
	 * @param usuario usuario autenticado
	 * @param json    cuerpo
	 * @return la respuesta
	 */
	private ResultActions reseniar(Usuario usuario, String json) throws Exception {
		return como(usuario, post("/resenas").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	/**
	 * Ejecuta la petición con el token del usuario.
	 *
	 * @param usuario  usuario autenticado
	 * @param peticion petición
	 * @return la respuesta
	 */
	private ResultActions como(Usuario usuario, MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header("Authorization", "Bearer " + jwtUtil.generateToken(usuario)));
	}

	/**
	 * Guarda una reseña directamente.
	 *
	 * @param autor         huésped
	 * @param idReserva     reserva
	 * @param idAlojamiento alojamiento
	 * @param calificacion  calificación
	 * @param estado        estado
	 * @param diasAtras     hace cuántos días se escribió
	 */
	private void guardarResena(Usuario autor, Long idReserva, Long idAlojamiento, int calificacion,
			EstadoResena estado, int diasAtras) {
		Resena resena = new Resena();
		resena.setIdUsuario(autor.getIdUsuario());
		resena.setIdReserva(idReserva);
		resena.setIdAlojamiento(idAlojamiento);
		resena.setCalificacion(calificacion);
		resena.setComentario("Reseña de prueba");
		resena.setEstado(estado);
		resena.setFecha(LocalDateTime.now().minusDays(diasAtras));
		resenaRepository.save(resena);
	}

	/**
	 * Guarda un alojamiento ACTIVA del anfitrión.
	 *
	 * @param anfitrion dueño
	 * @param titulo    título
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(Usuario anfitrion, String titulo) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo(titulo);
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	/**
	 * Guarda una reserva CONFIRMADA.
	 *
	 * @param huesped       huésped
	 * @param idAlojamiento alojamiento
	 * @param llegada       día de llegada
	 * @param salida        día de salida
	 * @return id de la reserva
	 */
	private Long crearReserva(Usuario huesped, Long idAlojamiento, LocalDate llegada, LocalDate salida) {
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setIdHuesped(huesped.getIdUsuario());
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(llegada);
		reserva.setFechaSalida(salida);
		reserva.setNoches(3);
		reserva.setHuespedes(2);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setTotal(new BigDecimal("600000.00"));
		reserva.setFechaCreacion(LocalDateTime.now());
		return reservaRepository.save(reserva).getIdReserva();
	}

	/**
	 * Guarda un usuario activo y verificado.
	 *
	 * @param correo correo de la cuenta
	 * @param nombre nombre visible
	 * @param rol    rol
	 * @return usuario guardado
	 */
	private Usuario crearUsuario(String correo, String nombre, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
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
