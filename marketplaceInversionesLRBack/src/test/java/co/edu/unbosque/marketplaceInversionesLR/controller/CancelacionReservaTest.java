package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.CancelacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.IdempotenciaService;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-01 Cancelar reserva aplicando la política de cancelación sobre
 * la API completa y un PostgreSQL temporal en Docker.
 * <p>
 * Datos: un alojamiento ACTIVA con política MODERADA (100 % con 5 o más días,
 * 50 % con 1 a 4) y otro con ESTRICTA (50 % con 7 o más días, nada después),
 * y reservas CONFIRMADA pagadas de 600.000.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class CancelacionReservaTest {

	/** Total de cada reserva. */
	private static final BigDecimal TOTAL = new BigDecimal("600000.00");

	/** Motivo de las cancelaciones. */
	private static final String MOTIVO = "Cambio de planes";

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
	private ReservaRepository reservaRepository;

	@Autowired
	private PagoRepository pagoRepository;

	@Autowired
	private CancelacionRepository cancelacionRepository;

	@Autowired
	private AuditoriaService auditoriaService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Huésped dueño de las reservas. */
	private Usuario huesped;

	/** Token del huésped. */
	private String tokenHuesped;

	/** Anfitrión de los alojamientos. */
	private Usuario anfitrion;

	/** Alojamiento con política MODERADA. */
	private Long moderado;

	/** Alojamiento con política ESTRICTA. */
	private Long estricto;

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = crearUsuario("anfitrion.cancelacion@correo.com", Role.ANFITRION);
		huesped = crearUsuario("huesped.cancelacion@correo.com", Role.HUESPED);
		tokenHuesped = jwtUtil.generateToken(huesped);
		moderado = crearAlojamiento(politica("MODERADA", 5, 1, 50));
		estricto = crearAlojamiento(politica("ESTRICTA", null, 7, 50));
	}

	/**
	 * Borra en orden de llaves foráneas: idempotencia, avisos, cancelaciones,
	 * pagos y reservas antes que los alojamientos, y estos antes que las
	 * políticas y los usuarios.
	 */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM registro_idempotencia");
		jdbc.update("DELETE FROM notificacion");
		cancelacionRepository.deleteAll();
		pagoRepository.deleteAll();
		jdbc.update("DELETE FROM cotizacion");
		reservaRepository.deleteAll();
		periodoRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM servicio_adicional");
		jdbc.update("DELETE FROM alojamiento");
		jdbc.update("DELETE FROM politica_cancelacion");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-001 CA-004 CA-007 Cancelar aplica la política, registra todo, devuelve el pago y libera las fechas")
	void cancelaYLiberaLasFechas_CA001_CA004_CA007() throws Exception {
		LocalDate llegada = LocalDate.now().plusDays(10);
		Long reserva = reservaPagada(moderado, llegada);
		disponible(moderado, llegada, false);

		cancelar(reserva, "clave-1", "  " + MOTIVO + "  ")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idCancelacion").isNumber())
				.andExpect(jsonPath("$.idReserva").value(reserva))
				.andExpect(jsonPath("$.idUsuario").value(huesped.getIdUsuario()))
				.andExpect(jsonPath("$.motivo").value(MOTIVO))
				.andExpect(jsonPath("$.politica").value("MODERADA"))
				.andExpect(jsonPath("$.porcentajeReembolso").value(100))
				.andExpect(jsonPath("$.valorReembolso").value(600000))
				.andExpect(jsonPath("$.estado").value("COMPLETADA"))
				.andExpect(jsonPath("$.estadoFinalReserva").value("CANCELADA"))
				.andExpect(jsonPath("$.fecha").isNotEmpty());

		assertThat(reservaRepository.findById(reserva).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(cancelacionRepository.findByIdReserva(reserva)).isPresent();
		Pago pago = pagoRepository.findTopByIdReservaOrderByFechaDescIdPagoDesc(reserva).orElseThrow();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
		assertThat(pago.getValorReembolsado()).isEqualByComparingTo(TOTAL);
		assertThat(pago.getReferenciaReembolso()).startsWith(PagoService.PREFIJO_DEVOLUCION);
		// Avisos al huésped y al anfitrión, y queda en la auditoría
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo = ?", Integer.class,
				NotificacionService.RESERVA_CANCELADA)).isEqualTo(2);
		assertThat(auditoriaService.consultar(AuditoriaService.CANCELACION, huesped.getIdUsuario(), null, null, 0, 10)
				.getContent()).isNotEmpty();
		// CA-007: las fechas vuelven a estar disponibles
		disponible(moderado, llegada, true);
	}

	@Test
	@DisplayName("CA-001 Con 3 días de anticipación la política MODERADA devuelve el 50 %")
	void devolucionParcial_CA001() throws Exception {
		Long reserva = reservaPagada(moderado, LocalDate.now().plusDays(3));

		cancelar(reserva, "clave-parcial", MOTIVO).andExpect(status().isOk())
				.andExpect(jsonPath("$.porcentajeReembolso").value(50))
				.andExpect(jsonPath("$.valorReembolso").value(300000));

		assertThat(pagoRepository.findTopByIdReservaOrderByFechaDescIdPagoDesc(reserva).orElseThrow()
				.getValorReembolsado()).isEqualByComparingTo("300000.00");
	}

	@Test
	@DisplayName("CA-009 Si la política no da devolución, se respeta: se cancela sin devolver y el pago sigue APROBADO")
	void respetaLaPolitica_CA009() throws Exception {
		Long reserva = reservaPagada(estricto, LocalDate.now().plusDays(3));

		cancelar(reserva, "clave-estricta", MOTIVO).andExpect(status().isOk())
				.andExpect(jsonPath("$.politica").value("ESTRICTA"))
				.andExpect(jsonPath("$.porcentajeReembolso").value(0))
				.andExpect(jsonPath("$.valorReembolso").value(0))
				.andExpect(jsonPath("$.estadoFinalReserva").value("CANCELADA"));

		Pago pago = pagoRepository.findTopByIdReservaOrderByFechaDescIdPagoDesc(reserva).orElseThrow();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(pago.getValorReembolsado()).isNull();
	}

	@Test
	@DisplayName("CA-001 Simular muestra la política, los días, el porcentaje y el valor sin cancelar nada")
	void simula_CA001() throws Exception {
		Long reserva = reservaPagada(moderado, LocalDate.now().plusDays(3));

		mockMvc.perform(get("/reservas/" + reserva + "/simularcancelacion")
				.header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idReserva").value(reserva))
				.andExpect(jsonPath("$.politica").value("MODERADA"))
				.andExpect(jsonPath("$.diasAnticipacion").value(3))
				.andExpect(jsonPath("$.porcentajeReembolso").value(50))
				.andExpect(jsonPath("$.valorReembolso").value(300000))
				.andExpect(jsonPath("$.totalPagado").value(600000));

		assertThat(reservaRepository.findById(reserva).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(cancelacionRepository.count()).isZero();
	}

	@Test
	@DisplayName("CA-140 CA-141 Reintentar con la misma Idempotency-Key devuelve la misma respuesta sin cancelar dos veces")
	void idempotente_CA140_CA141() throws Exception {
		Long reserva = reservaPagada(moderado, LocalDate.now().plusDays(10));
		String primera = cancelar(reserva, "clave-repetida", MOTIVO).andExpect(status().isOk())
				.andExpect(header().doesNotExist(IdempotenciaService.CABECERA_REPETIDA))
				.andReturn().getResponse().getContentAsString();

		Number idCancelacion = JsonPath.read(primera, "$.idCancelacion");
		String fecha = JsonPath.read(primera, "$.fecha");

		cancelar(reserva, "clave-repetida", MOTIVO).andExpect(status().isOk())
				.andExpect(header().string(IdempotenciaService.CABECERA_REPETIDA, "true"))
				.andExpect(jsonPath("$.idCancelacion").value(idCancelacion.longValue()))
				.andExpect(jsonPath("$.fecha").value(fecha))
				.andExpect(jsonPath("$.porcentajeReembolso").value(100))
				.andExpect(jsonPath("$.valorReembolso").value(600000));

		assertThat(cancelacionRepository.count()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM pago WHERE estado = 'REEMBOLSADO'", Integer.class))
				.isEqualTo(1);
		// La misma clave con otro motivo, 422; sin clave, 400; con otra clave, la reserva ya está cancelada
		cancelar(reserva, "clave-repetida", "Otro motivo").andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.message").value(IdempotenciaService.CLAVE_REUTILIZADA));
		cancelar(reserva, null, MOTIVO).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(IdempotenciaService.CLAVE_INVALIDA));
		cancelar(reserva, "clave-nueva", MOTIVO).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(CancelacionService.ESTADO_NO_CANCELABLE + "CANCELADA."));
		assertThat(cancelacionRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Solo se cancelan reservas futuras propias, con motivo; los demás casos responden 400, 403, 404 o 409")
	void validaciones() throws Exception {
		Long pasada = reservaPagada(moderado, LocalDate.now().minusDays(2));
		Long futura = reservaPagada(moderado, LocalDate.now().plusDays(20));

		cancelar(pasada, "clave-pasada", MOTIVO).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(CancelacionService.SOLO_FUTURAS));
		cancelar(futura, "clave-sin-motivo", "  ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CancelacionService.MOTIVO_INVALIDO));
		cancelar(futura, "clave-largo", "x".repeat(501)).andExpect(status().isBadRequest());
		cancelar(999999L, "clave-no-existe", MOTIVO).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(CancelacionService.NO_EXISTE));

		// Otro huésped no ve la reserva; el anfitrión no es huésped; sin sesión, 401
		String tokenOtro = jwtUtil.generateToken(crearUsuario("otro.cancelacion@correo.com", Role.HUESPED));
		mockMvc.perform(post("/reservas/" + futura + "/cancelar").header("Authorization", "Bearer " + tokenOtro)
				.header(IdempotenciaService.CABECERA, "clave-otro").contentType(MediaType.APPLICATION_JSON)
				.content(motivo(MOTIVO))).andExpect(status().isNotFound());
		mockMvc.perform(get("/reservas/" + futura + "/simularcancelacion").header("Authorization", "Bearer " + tokenOtro))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/reservas/" + futura + "/cancelar")
				.header("Authorization", "Bearer " + jwtUtil.generateToken(anfitrion))
				.header(IdempotenciaService.CABECERA, "clave-anfitrion").contentType(MediaType.APPLICATION_JSON)
				.content(motivo(MOTIVO))).andExpect(status().isForbidden());
		mockMvc.perform(post("/reservas/" + futura + "/cancelar").header(IdempotenciaService.CABECERA, "clave-anonima")
				.contentType(MediaType.APPLICATION_JSON).content(motivo(MOTIVO))).andExpect(status().isUnauthorized());

		assertThat(reservaRepository.findById(futura).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(cancelacionRepository.count()).isZero();
	}

	/**
	 * Cancela como el huésped de prueba.
	 *
	 * @param idReserva reserva
	 * @param clave     Idempotency-Key; {@code null} para no enviarla
	 * @param motivo    motivo de la cancelación
	 * @return la respuesta
	 */
	private ResultActions cancelar(Long idReserva, String clave, String motivo) throws Exception {
		var peticion = post("/reservas/" + idReserva + "/cancelar").header("Authorization", "Bearer " + tokenHuesped)
				.contentType(MediaType.APPLICATION_JSON).content(motivo(motivo));
		if (clave != null) {
			peticion.header(IdempotenciaService.CABECERA, clave);
		}
		return mockMvc.perform(peticion);
	}

	/**
	 * Consulta la disponibilidad de una noche desde la llegada, sin sesión.
	 *
	 * @param idAlojamiento alojamiento
	 * @param llegada       día de llegada
	 * @param esperado      disponibilidad esperada
	 */
	private void disponible(Long idAlojamiento, LocalDate llegada, boolean esperado) throws Exception {
		ResultActions respuesta = mockMvc.perform(get("/alojamientos/" + idAlojamiento + "/disponibilidad/consultar")
				.param("llegada", llegada.toString()).param("salida", llegada.plusDays(1).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(esperado));
		if (!esperado) {
			respuesta.andExpect(jsonPath("$.motivo").value(CalendarioService.CRUCE_CON_RESERVA));
		}
	}

	/**
	 * JSON con el motivo.
	 *
	 * @param motivo motivo
	 * @return cuerpo de la petición
	 */
	private static String motivo(String motivo) {
		return "{\"motivo\": \"" + motivo + "\"}";
	}

	/**
	 * Guarda una política de cancelación.
	 *
	 * @param nombre           nombre
	 * @param diasTotal        días para devolver el 100 %; puede ser
	 *                         {@code null}
	 * @param diasParcial      días para devolver el parcial; puede ser
	 *                         {@code null}
	 * @param porcentajeParcial porcentaje parcial
	 * @return id de la política
	 */
	private Long politica(String nombre, Integer diasTotal, Integer diasParcial, Integer porcentajeParcial) {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre(nombre);
		politica.setCondiciones("Política " + nombre);
		politica.setDiasReembolsoTotal(diasTotal);
		politica.setDiasReembolsoParcial(diasParcial);
		politica.setPorcentajeParcial(porcentajeParcial);
		return politicaRepository.save(politica).getIdPolitica();
	}

	/**
	 * Guarda un alojamiento ACTIVA del anfitrión con la política indicada y un
	 * período disponible amplio.
	 *
	 * @param idPolitica política de cancelación
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(Long idPolitica) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa de prueba");
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setIdPolitica(idPolitica);
		alojamiento.setFechaCreacion(LocalDateTime.now());
		Long id = alojamientoRepository.save(alojamiento).getIdAlojamiento();

		PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
		periodo.setIdAlojamiento(id);
		periodo.setFechaInicio(LocalDate.now().minusDays(10));
		periodo.setFechaFin(LocalDate.now().plusMonths(2));
		periodoRepository.save(periodo);
		return id;
	}

	/**
	 * Guarda una reserva CONFIRMADA de tres noches del huésped con su pago
	 * APROBADO por el total.
	 *
	 * @param idAlojamiento alojamiento
	 * @param llegada       día de llegada
	 * @return id de la reserva
	 */
	private Long reservaPagada(Long idAlojamiento, LocalDate llegada) {
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setIdHuesped(huesped.getIdUsuario());
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(llegada);
		reserva.setFechaSalida(llegada.plusDays(3));
		reserva.setNoches(3);
		reserva.setHuespedes(2);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setTotal(TOTAL);
		reserva.setFechaCreacion(LocalDateTime.now());
		Long id = reservaRepository.save(reserva).getIdReserva();

		Pago pago = new Pago();
		pago.setIdReserva(id);
		pago.setValor(TOTAL);
		pago.setEstado(EstadoPago.APROBADO);
		pago.setProveedor(PagoService.PROVEEDOR_SIMULADO);
		pago.setMetodo(PagoService.METODO_POR_DEFECTO);
		pago.setReferencia("sim_pi_prueba");
		pago.setFecha(LocalDateTime.now());
		pagoRepository.save(pago);
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
