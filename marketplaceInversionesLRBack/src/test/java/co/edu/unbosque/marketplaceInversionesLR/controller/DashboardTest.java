package co.edu.unbosque.marketplaceInversionesLR.controller;

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
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-04 Consultar dashboard de indicadores sobre la API completa y
 * un PostgreSQL temporal en Docker: los indicadores salen de la información
 * registrada (CA-022).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class DashboardTest {

	/** Ruta del dashboard. */
	private static final String RUTA = "/admin/dashboard";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

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

	/** Token del administrador. */
	private String tokenAdmin;

	/** Anfitrión dueño de los alojamientos. */
	private Usuario anfitrion;

	/** Huésped de las reservas. */
	private Usuario huesped;

	@BeforeEach
	void preparar() {
		limpiar();
		tokenAdmin = jwtUtil.generateToken(crearUsuario("admin.dashboard@correo.com", Role.ADMIN));
		anfitrion = crearUsuario("anfitrion.dashboard@correo.com", Role.ANFITRION);
		huesped = crearUsuario("huesped.dashboard@correo.com", Role.HUESPED);

		Long bogota = crearAlojamiento("Bogotá", EstadoAlojamiento.ACTIVA);
		Long medellin = crearAlojamiento("Medellín", EstadoAlojamiento.ACTIVA);
		crearAlojamiento("Cali", EstadoAlojamiento.BORRADOR);

		// Bogotá: dos reservas pagadas y una cancelada con devolución de 100.000
		reserva(bogota, EstadoReserva.CONFIRMADA, EstadoPago.APROBADO);
		reserva(bogota, EstadoReserva.CONFIRMADA, EstadoPago.APROBADO);
		Long cancelada = reserva(bogota, EstadoReserva.CANCELADA, EstadoPago.REEMBOLSADO);
		cancelacion(cancelada, new BigDecimal("100000"));
		// Medellín: una pendiente de pago (su pago no cuenta como ingreso)
		reserva(medellin, EstadoReserva.PENDIENTE_PAGO, EstadoPago.PENDIENTE);
	}

	/** Borra en orden de llaves foráneas. */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM notificacion");
		cancelacionRepository.deleteAll();
		pagoRepository.deleteAll();
		reservaRepository.deleteAll();
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-022 El dashboard muestra usuarios, publicaciones activas, reservas por estado, ingresos estimados y ciudades con mayor demanda")
	void indicadores_CA022() throws Exception {
		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalUsuarios").value(3))
				.andExpect(jsonPath("$.alojamientos.total").value(3))
				.andExpect(jsonPath("$.alojamientos.porEstado.ACTIVA").value(2))
				.andExpect(jsonPath("$.alojamientos.porEstado.BORRADOR").value(1))
				.andExpect(jsonPath("$.alojamientos.porEstado.PAUSADA").value(0))
				.andExpect(jsonPath("$.reservas.total").value(4))
				.andExpect(jsonPath("$.reservas.porEstado.CONFIRMADA").value(2))
				.andExpect(jsonPath("$.reservas.porEstado.CANCELADA").value(1))
				.andExpect(jsonPath("$.reservas.porEstado.PENDIENTE_PAGO").value(1))
				.andExpect(jsonPath("$.reservas.porEstado.EN_REVISION").value(0))
				// 3 pagos cobrados de 600.000 menos 100.000 devueltos
				.andExpect(jsonPath("$.ingresosEstimados").value(1700000.0))
				.andExpect(jsonPath("$.ciudadesTop[0].ciudad").value("Bogotá"))
				.andExpect(jsonPath("$.ciudadesTop[0].totalReservas").value(3))
				.andExpect(jsonPath("$.ciudadesTop[1].ciudad").value("Medellín"))
				.andExpect(jsonPath("$.ciudadesTop[1].totalReservas").value(1));
	}

	@Test
	@DisplayName("CA-022 El ranking de ciudades respeta la cantidad pedida")
	void limitaCiudades_CA022() throws Exception {
		mockMvc.perform(get(RUTA).param("topCiudades", "1").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ciudadesTop.length()").value(1))
				.andExpect(jsonPath("$.ciudadesTop[0].ciudad").value("Bogotá"));
	}

	@Test
	@DisplayName("CA-022 Solo el administrador consulta el dashboard")
	void soloAdministrador_CA022() throws Exception {
		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + jwtUtil.generateToken(anfitrion)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
	}

	private Long crearAlojamiento(String ciudad, EstadoAlojamiento estado) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en " + ciudad);
		alojamiento.setCiudad(ciudad);
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(estado);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	private Long reserva(Long idAlojamiento, EstadoReserva estado, EstadoPago estadoPago) {
		BigDecimal total = new BigDecimal("600000.00");
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setIdHuesped(huesped.getIdUsuario());
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(LocalDate.now().plusDays(10));
		reserva.setFechaSalida(LocalDate.now().plusDays(13));
		reserva.setNoches(3);
		reserva.setHuespedes(2);
		reserva.setEstado(estado);
		reserva.setTotal(total);
		reserva.setFechaCreacion(LocalDateTime.now());
		Long id = reservaRepository.save(reserva).getIdReserva();

		Pago pago = new Pago();
		pago.setIdReserva(id);
		pago.setValor(total);
		pago.setEstado(estadoPago);
		pago.setProveedor(PagoService.PROVEEDOR_SIMULADO);
		pago.setMetodo(PagoService.METODO_POR_DEFECTO);
		pago.setReferencia("sim_pi_dashboard_" + id);
		pago.setFecha(LocalDateTime.now());
		pagoRepository.save(pago);
		return id;
	}

	private void cancelacion(Long idReserva, BigDecimal devuelto) {
		Cancelacion cancelacion = new Cancelacion();
		cancelacion.setIdReserva(idReserva);
		cancelacion.setIdUsuario(huesped.getIdUsuario());
		cancelacion.setMotivo("Cambio de planes");
		cancelacion.setPolitica("MODERADA");
		cancelacion.setPorcentajeReembolso(50);
		cancelacion.setValorReembolso(devuelto);
		cancelacion.setEstado(EstadoCancelacion.COMPLETADA);
		cancelacion.setEstadoFinalReserva(EstadoReserva.CANCELADA);
		cancelacion.setFecha(LocalDateTime.now());
		cancelacionRepository.save(cancelacion);
	}

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
