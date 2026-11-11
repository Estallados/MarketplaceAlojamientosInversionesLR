package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.CancelacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-25 Consultar estado e historial de reservas sobre la API
 * completa y un PostgreSQL temporal en Docker.
 * <p>
 * Datos: dos anfitriones con un alojamiento cada uno. Ana reservó en los dos
 * (una CONFIRMADA pagada en el de Carlos y una PENDIENTE_PAGO en el de Diana);
 * Beto reservó en el de Carlos. Ana ve sus dos reservas (CA-078), Carlos ve
 * las dos de su alojamiento y nunca la de Diana (CA-079).
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class HistorialReservasTest {

	/** Total de cada reserva. */
	private static final BigDecimal TOTAL = new BigDecimal("600000.00");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private PoliticaCancelacionRepository politicaRepository;

	@Autowired
	private ReservaRepository reservaRepository;

	@Autowired
	private PagoRepository pagoRepository;

	@Autowired
	private CancelacionRepository cancelacionRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Huésped con dos reservas. */
	private Usuario ana;

	/** Huésped con una reserva en el alojamiento de Carlos. */
	private Usuario beto;

	/** Anfitrión del alojamiento con política MODERADA. */
	private Usuario carlos;

	/** Anfitrión del otro alojamiento, sin política. */
	private Usuario diana;

	/** Reserva CONFIRMADA y pagada de Ana en el alojamiento de Carlos (la más antigua). */
	private Long anaEnCarlos;

	/** Reserva PENDIENTE_PAGO de Ana en el alojamiento de Diana. */
	private Long anaEnDiana;

	/** Reserva CONFIRMADA de Beto en el alojamiento de Carlos (la más reciente). */
	private Long betoEnCarlos;

	@BeforeEach
	void preparar() {
		limpiar();
		ana = crearUsuario("ana.historial@correo.com", "Ana Huésped", Role.HUESPED);
		beto = crearUsuario("beto.historial@correo.com", "Beto Huésped", Role.HUESPED);
		carlos = crearUsuario("carlos.historial@correo.com", "Carlos Anfitrión", Role.ANFITRION);
		diana = crearUsuario("diana.historial@correo.com", "Diana Anfitriona", Role.ANFITRION);

		PoliticaCancelacion moderada = new PoliticaCancelacion();
		moderada.setNombre("MODERADA");
		moderada.setCondiciones("Política MODERADA");
		moderada.setDiasReembolsoTotal(5);
		moderada.setDiasReembolsoParcial(1);
		moderada.setPorcentajeParcial(50);
		Long idModerada = politicaRepository.save(moderada).getIdPolitica();

		Long deCarlos = crearAlojamiento(carlos, "Casa de Carlos", idModerada);
		Long deDiana = crearAlojamiento(diana, "Cabaña de Diana", null);

		LocalDate base = LocalDate.now().plusDays(10);
		anaEnCarlos = crearReserva(ana, deCarlos, base, EstadoReserva.CONFIRMADA, 3);
		pagoAprobado(anaEnCarlos);
		anaEnDiana = crearReserva(ana, deDiana, base, EstadoReserva.PENDIENTE_PAGO, 2);
		betoEnCarlos = crearReserva(beto, deCarlos, base.plusDays(5), EstadoReserva.CONFIRMADA, 1);
	}

	/** Borra en orden de llaves foráneas. */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM notificacion");
		cancelacionRepository.deleteAll();
		pagoRepository.deleteAll();
		jdbc.update("DELETE FROM cotizacion");
		reservaRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM servicio_adicional");
		jdbc.update("DELETE FROM alojamiento");
		jdbc.update("DELETE FROM politica_cancelacion");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-078 El huésped ve todas sus reservas con su estado actual, las más recientes primero")
	void historialDelHuesped_CA078() throws Exception {
		como(ana, get("/reservas/mias"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[*].idReserva")
						.value(contains(anaEnDiana.intValue(), anaEnCarlos.intValue())))
				.andExpect(jsonPath("$.contenido[0].estado").value("PENDIENTE_PAGO"))
				.andExpect(jsonPath("$.contenido[0].tituloAlojamiento").value("Cabaña de Diana"))
				.andExpect(jsonPath("$.contenido[0].nombrePolitica").value(CancelacionService.POLITICA_POR_DEFECTO))
				.andExpect(jsonPath("$.contenido[0].nombreAnfitrion").value("Diana Anfitriona"))
				.andExpect(jsonPath("$.contenido[0].pago").doesNotExist())
				.andExpect(jsonPath("$.contenido[1].estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.contenido[1].tituloAlojamiento").value("Casa de Carlos"))
				.andExpect(jsonPath("$.contenido[1].ciudad").value("Bogotá"))
				.andExpect(jsonPath("$.contenido[1].nombrePolitica").value("MODERADA"))
				.andExpect(jsonPath("$.contenido[1].idAnfitrion").value(carlos.getIdUsuario()))
				.andExpect(jsonPath("$.contenido[1].nombreHuesped").value("Ana Huésped"))
				.andExpect(jsonPath("$.contenido[1].total").value(600000))
				.andExpect(jsonPath("$.contenido[1].pago.estado").value("APROBADO"))
				.andExpect(jsonPath("$.contenido[1].completada").value(false))
				.andExpect(jsonPath("$.contenido[1].tieneResena").value(false));

		// Paginado: una por página
		como(ana, get("/reservas/mias").param("tamanio", "1").param("pagina", "1"))
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].idReserva").value(anaEnCarlos))
				.andExpect(jsonPath("$.totalPaginas").value(2));

		// Un usuario sin reservas recibe una página vacía, también si es anfitrión
		como(carlos, get("/reservas/mias")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0))
				.andExpect(jsonPath("$.contenido.length()").value(0));
	}

	@Test
	@DisplayName("CA-079 El anfitrión ve solo las reservas de sus alojamientos, nunca las de otros")
	void reservasDelAnfitrion_CA079() throws Exception {
		como(carlos, get("/reservas/anfitrion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[*].idReserva")
						.value(contains(betoEnCarlos.intValue(), anaEnCarlos.intValue())))
				.andExpect(jsonPath("$.contenido[0].nombreHuesped").value("Beto Huésped"))
				.andExpect(jsonPath("$.contenido[1].nombreHuesped").value("Ana Huésped"));

		como(diana, get("/reservas/anfitrion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].idReserva").value(anaEnDiana));

		// Un huésped no tiene vista de anfitrión
		como(ana, get("/reservas/anfitrion")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("El detalle lo ven el huésped, el anfitrión del alojamiento y el administrador; los demás reciben 404")
	void detalle() throws Exception {
		como(ana, get("/reservas/" + anaEnCarlos))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idReserva").value(anaEnCarlos))
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.nombrePolitica").value("MODERADA"))
				.andExpect(jsonPath("$.pago.estado").value("APROBADO"))
				.andExpect(jsonPath("$.cancelacion").doesNotExist());
		como(carlos, get("/reservas/" + anaEnCarlos)).andExpect(status().isOk())
				.andExpect(jsonPath("$.nombreHuesped").value("Ana Huésped"));
		Usuario admin = crearUsuario("admin.historial@correo.com", "Admin", Role.ADMIN);
		como(admin, get("/reservas/" + anaEnCarlos)).andExpect(status().isOk());

		// Otro huésped y el anfitrión de otro alojamiento no la ven
		como(beto, get("/reservas/" + anaEnCarlos)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(ReservaService.NO_EXISTE));
		como(diana, get("/reservas/" + anaEnCarlos)).andExpect(status().isNotFound());
		como(ana, get("/reservas/999999")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(ReservaService.NO_EXISTE));
	}

	@Test
	@DisplayName("Sin sesión las rutas del historial responden 401")
	void sinSesion() throws Exception {
		mockMvc.perform(get("/reservas/mias")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/reservas/anfitrion")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/reservas/" + anaEnCarlos)).andExpect(status().isUnauthorized());
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
	 * Guarda un alojamiento ACTIVA del anfitrión.
	 *
	 * @param anfitrion  dueño
	 * @param titulo     título
	 * @param idPolitica política; puede ser {@code null}
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(Usuario anfitrion, String titulo, Long idPolitica) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo(titulo);
		alojamiento.setCiudad("Bogotá");
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setIdPolitica(idPolitica);
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	/**
	 * Guarda una reserva de tres noches.
	 *
	 * @param huesped    huésped
	 * @param idAlojamiento alojamiento
	 * @param llegada    día de llegada
	 * @param estado     estado
	 * @param horasAtras hace cuántas horas se creó
	 * @return id de la reserva
	 */
	private Long crearReserva(Usuario huesped, Long idAlojamiento, LocalDate llegada, EstadoReserva estado,
			int horasAtras) {
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setIdHuesped(huesped.getIdUsuario());
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(llegada);
		reserva.setFechaSalida(llegada.plusDays(3));
		reserva.setNoches(3);
		reserva.setHuespedes(2);
		reserva.setEstado(estado);
		reserva.setTotal(TOTAL);
		reserva.setFechaCreacion(LocalDateTime.now().minusHours(horasAtras));
		return reservaRepository.save(reserva).getIdReserva();
	}

	/**
	 * Guarda el pago APROBADO de una reserva.
	 *
	 * @param idReserva reserva
	 */
	private void pagoAprobado(Long idReserva) {
		Pago pago = new Pago();
		pago.setIdReserva(idReserva);
		pago.setValor(TOTAL);
		pago.setEstado(EstadoPago.APROBADO);
		pago.setProveedor(PagoService.PROVEEDOR_SIMULADO);
		pago.setMetodo(PagoService.METODO_POR_DEFECTO);
		pago.setReferencia("sim_pi_historial");
		pago.setFecha(LocalDateTime.now());
		pagoRepository.save(pago);
	}

	/**
	 * Guarda un usuario activo y verificado.
	 *
	 * @param correo correo de la cuenta
	 * @param nombre nombre visible
	 * @param rol    rol de la cuenta
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
