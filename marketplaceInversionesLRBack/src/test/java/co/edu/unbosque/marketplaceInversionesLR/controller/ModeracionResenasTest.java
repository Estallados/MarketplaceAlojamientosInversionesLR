package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.ResenaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;

/**
 * Pruebas de HU-14 Ocultar reseñas que incumplan reglas de moderación sobre la
 * API completa y un PostgreSQL temporal en Docker.
 * <p>
 * Datos: la casa de Carlos con dos reseñas PUBLICADA, la de Ana (5 estrellas)
 * y la de Beto (1 estrella, la que se modera).
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ModeracionResenasTest {

	/** Motivo de la moderación. */
	private static final String MOTIVO = "Lenguaje ofensivo";

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

	/** Administrador que modera. */
	private Usuario admin;

	/** Autora de la reseña de 5 estrellas. */
	private Usuario ana;

	/** Autor de la reseña que se oculta. */
	private Usuario beto;

	/** Anfitrión de la casa. */
	private Usuario carlos;

	/** Casa de Carlos. */
	private Long casa;

	/** Reseña de Beto, de 1 estrella. */
	private Long deBeto;

	@BeforeEach
	void preparar() {
		limpiar();
		admin = crearUsuario("admin.moderacion@correo.com", "Admin", Role.ADMIN);
		ana = crearUsuario("ana.moderacion@correo.com", "Ana Huésped", Role.HUESPED);
		beto = crearUsuario("beto.moderacion@correo.com", "Beto Huésped", Role.HUESPED);
		carlos = crearUsuario("carlos.moderacion@correo.com", "Carlos Anfitrión", Role.ANFITRION);
		casa = crearAlojamiento(carlos);
		LocalDate hoy = LocalDate.now();
		resena(ana, crearReserva(ana, hoy.minusDays(20)), 5, 2);
		deBeto = resena(beto, crearReserva(beto, hoy.minusDays(10)), 1, 1);
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
	@DisplayName("CA-066 El administrador oculta una reseña: no se borra, deja de mostrarse y de contar en el promedio, "
			+ "se avisa al autor y queda auditada")
	void ocultarYMostrar_CA066() throws Exception {
		consultaPublica().andExpect(jsonPath("$.cantidad").value(2)).andExpect(jsonPath("$.promedio").value(3.0));

		ocultar(admin, deBeto, "  " + MOTIVO + "  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idResena").value(deBeto))
				.andExpect(jsonPath("$.estado").value("OCULTA"))
				.andExpect(jsonPath("$.calificacion").value(1))
				.andExpect(jsonPath("$.nombreUsuario").value("Beto Huésped"));

		// Ya no se muestra ni cuenta en el promedio, pero no se borró
		consultaPublica().andExpect(jsonPath("$.cantidad").value(1))
				.andExpect(jsonPath("$.promedio").value(5.0))
				.andExpect(jsonPath("$.resenas.contenido[0].nombreUsuario").value("Ana Huésped"));
		Resena guardada = resenaRepository.findById(deBeto).orElseThrow();
		assertThat(guardada.getEstado()).isEqualTo(EstadoResena.OCULTA);
		assertThat(guardada.getCalificacion()).isEqualTo(1);
		assertThat(resenaRepository.count()).isEqualTo(2);

		// El autor recibe el motivo y la ve con su estado; queda en la auditoría
		assertThat(jdbc.queryForObject("SELECT mensaje FROM notificacion WHERE id_usuario = ? AND tipo = ?",
				String.class, beto.getIdUsuario(), NotificacionService.RESENA_OCULTA)).endsWith("Motivo: " + MOTIVO);
		como(beto, get("/resenas/mias")).andExpect(jsonPath("$.contenido[0].estado").value("OCULTA"));
		assertThat(auditoriaService.consultar(AuditoriaService.RESENA_MODERADA, admin.getIdUsuario(), null, null, 0, 10)
				.getContent()).hasSize(1);
		como(admin, get("/admin/resenas").param("estado", "OCULTA"))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].idResena").value(deBeto));
		como(admin, get("/admin/resenas")).andExpect(jsonPath("$.totalElementos").value(2));

		// Ocultarla otra vez no repite el aviso
		ocultar(admin, deBeto, MOTIVO).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OCULTA"));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = ?", Integer.class,
				NotificacionService.RESENA_OCULTA)).isEqualTo(1);

		// Volver a mostrarla
		como(admin, patch("/admin/resenas/" + deBeto + "/mostrar")).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PUBLICADA"));
		consultaPublica().andExpect(jsonPath("$.cantidad").value(2)).andExpect(jsonPath("$.promedio").value(3.0));
		assertThat(auditoriaService.consultar(AuditoriaService.RESENA_MODERADA, admin.getIdUsuario(), null, null, 0, 10)
				.getContent()).hasSize(2);
	}

	@Test
	@DisplayName("CA-069 Un usuario sin permisos administrativos no puede ocultar ni listar reseñas")
	void sinPermisos_CA069() throws Exception {
		ocultar(beto, deBeto, MOTIVO).andExpect(status().isForbidden());
		ocultar(carlos, deBeto, MOTIVO).andExpect(status().isForbidden());
		como(carlos, patch("/admin/resenas/" + deBeto + "/mostrar")).andExpect(status().isForbidden());
		como(ana, get("/admin/resenas")).andExpect(status().isForbidden());
		mockMvc.perform(patch("/admin/resenas/" + deBeto + "/ocultar").contentType(MediaType.APPLICATION_JSON)
				.content(motivo(MOTIVO))).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/admin/resenas")).andExpect(status().isUnauthorized());

		assertThat(resenaRepository.findById(deBeto).orElseThrow().getEstado()).isEqualTo(EstadoResena.PUBLICADA);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Integer.class)).isZero();
		consultaPublica().andExpect(jsonPath("$.cantidad").value(2));
	}

	@Test
	@DisplayName("CA-066 Sin motivo, con motivo muy largo, con una reseña inexistente o un estado inválido: 400 o 404")
	void validaciones_CA066() throws Exception {
		ocultar(admin, deBeto, "   ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ResenaService.MOTIVO_OBLIGATORIO));
		como(admin, patch("/admin/resenas/" + deBeto + "/ocultar")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ResenaService.MOTIVO_OBLIGATORIO));
		ocultar(admin, deBeto, "x".repeat(LimitesTexto.MOTIVO + 1)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(LimitesTexto.MOTIVO_LARGO));
		ocultar(admin, 999999L, MOTIVO).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(ResenaService.NO_EXISTE));
		como(admin, patch("/admin/resenas/999999/mostrar")).andExpect(status().isNotFound());
		como(admin, get("/admin/resenas").param("estado", "BORRADA")).andExpect(status().isBadRequest());

		assertThat(resenaRepository.findById(deBeto).orElseThrow().getEstado()).isEqualTo(EstadoResena.PUBLICADA);
	}

	/**
	 * Consulta pública de las reseñas de la casa.
	 *
	 * @return la respuesta
	 */
	private ResultActions consultaPublica() throws Exception {
		return mockMvc.perform(get("/alojamientos/" + casa + "/resenas")).andExpect(status().isOk());
	}

	/**
	 * Oculta una reseña como el usuario.
	 *
	 * @param usuario  usuario autenticado
	 * @param idResena reseña
	 * @param texto    motivo
	 * @return la respuesta
	 */
	private ResultActions ocultar(Usuario usuario, Long idResena, String texto) throws Exception {
		return como(usuario, patch("/admin/resenas/" + idResena + "/ocultar").contentType(MediaType.APPLICATION_JSON)
				.content(motivo(texto)));
	}

	/**
	 * JSON con el motivo.
	 *
	 * @param texto motivo
	 * @return cuerpo
	 */
	private static String motivo(String texto) {
		return "{\"motivo\": \"" + texto + "\"}";
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
	 * Guarda una reseña PUBLICADA.
	 *
	 * @param autor        huésped
	 * @param idReserva    reserva
	 * @param calificacion calificación
	 * @param diasAtras    hace cuántos días se escribió
	 * @return id de la reseña
	 */
	private Long resena(Usuario autor, Long idReserva, int calificacion, int diasAtras) {
		Resena resena = new Resena();
		resena.setIdUsuario(autor.getIdUsuario());
		resena.setIdReserva(idReserva);
		resena.setIdAlojamiento(casa);
		resena.setCalificacion(calificacion);
		resena.setComentario("Reseña de prueba");
		resena.setEstado(EstadoResena.PUBLICADA);
		resena.setFecha(LocalDateTime.now().minusDays(diasAtras));
		return resenaRepository.save(resena).getIdResena();
	}

	/**
	 * Guarda un alojamiento ACTIVA del anfitrión.
	 *
	 * @param anfitrion dueño
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(Usuario anfitrion) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa de Carlos");
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	/**
	 * Guarda una reserva CONFIRMADA de tres noches en la casa, ya completada.
	 *
	 * @param huesped huésped
	 * @param llegada día de llegada
	 * @return id de la reserva
	 */
	private Long crearReserva(Usuario huesped, LocalDate llegada) {
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(casa);
		reserva.setIdHuesped(huesped.getIdUsuario());
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(llegada);
		reserva.setFechaSalida(llegada.plusDays(3));
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
