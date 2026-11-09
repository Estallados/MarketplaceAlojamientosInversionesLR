package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-22 Crear reserva con bloqueo automático de fechas sobre la
 * API completa y un PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ReservaTest {

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

	/** Huéspedes que intentan reservar las mismas fechas a la vez (CA-072). */
	private static final int SIMULTANEOS = 5;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private PeriodoDisponibilidadRepository periodoRepository;

	@Autowired
	private CotizacionRepository cotizacionRepository;

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

	/** Token del anfitrión dueño. */
	private String tokenAnfitrion;

	/** Huésped que reserva. */
	private Usuario huesped;

	/** Token del huésped. */
	private String tokenHuesped;

	/** Alojamiento ACTIVA de prueba: 200.000 por noche, hasta 4 huéspedes. */
	private Long idAlojamiento;

	@BeforeEach
	void preparar() {
		limpiar();
		Usuario anfitrion = crearUsuario("anfitrion@correo.com", Role.ANFITRION);
		tokenAnfitrion = jwtUtil.generateToken(anfitrion);
		huesped = crearUsuario("huesped@correo.com", Role.HUESPED);
		tokenHuesped = jwtUtil.generateToken(huesped);
		idAlojamiento = crearAlojamiento(anfitrion.getIdUsuario());
	}

	/**
	 * Borra en orden de llaves foráneas: cotizaciones y reservas antes que los
	 * alojamientos, y estos antes que los usuarios.
	 */
	@AfterEach
	void limpiar() {
		cotizacionRepository.deleteAll();
		reservaRepository.deleteAll();
		periodoRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-070 CA-075 CA-076 CA-086 El huésped reserva con la cotización: queda CONFIRMADA con el monto "
			+ "cotizado y las fechas se bloquean")
	void reservaYBloquea_CA070_CA075_CA076_CA086() throws Exception {
		Long idCotizacion = cotizar(tokenHuesped, BASE, BASE.plusDays(3), 2)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.noches").value(3))
				.andExpect(jsonPath("$.precioBase").value(600000))
				.andExpect(jsonPath("$.valorLimpieza").value(30000))
				.andExpect(jsonPath("$.valorServicio").value(60000))
				.andExpect(jsonPath("$.total").value(690000))
				.andExpect(jsonPath("$.fechaExpiracion").isNotEmpty())
				.andReturn().getResponse().getContentAsString().transform(ReservaTest::idCotizacion);

		reservar(tokenHuesped, idCotizacion)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idReserva").isNumber())
				.andExpect(jsonPath("$.estado").value("CONFIRMADA"))
				.andExpect(jsonPath("$.total").value(690000))
				.andExpect(jsonPath("$.idHuesped").value(huesped.getIdUsuario()))
				.andExpect(jsonPath("$.idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.idCotizacion").value(idCotizacion))
				.andExpect(jsonPath("$.fechaEntrada").value(BASE.toString()))
				.andExpect(jsonPath("$.fechaSalida").value(BASE.plusDays(3).toString()))
				.andExpect(jsonPath("$.tituloAlojamiento").value("Casa en el lago"));

		assertThat(reservaRepository.findAll()).singleElement().satisfies(r -> {
			assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
			assertThat(r.getTotal()).isEqualByComparingTo("690000");
			assertThat(r.getDesglose()).contains("690000");
		});
		assertThat(cotizacionRepository.findById(idCotizacion).orElseThrow().getIdReserva()).isNotNull();
		assertThat(auditoriaService.consultar(AuditoriaService.RESERVA_CREADA, huesped.getIdUsuario(), null, null, 0,
				10).getContent()).isNotEmpty();

		// CA-076: las fechas reservadas ya no están disponibles para nadie
		mockMvc.perform(get("/alojamientos/" + idAlojamiento + "/disponibilidad/consultar")
				.param("llegada", BASE.plusDays(1).toString()).param("salida", BASE.plusDays(2).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.disponible").value(false))
				.andExpect(jsonPath("$.motivo").value(CalendarioService.CRUCE_CON_RESERVA));
		mockMvc.perform(get("/alojamientos/" + idAlojamiento + "/disponibilidad"))
				.andExpect(jsonPath("$.reservados[0].fechaEntrada").value(BASE.toString()));
		Usuario otro = crearUsuario("otro@correo.com", Role.HUESPED);
		cotizar(jwtUtil.generateToken(otro), BASE.plusDays(2), BASE.plusDays(5), 1)
				.andExpect(status().isConflict());
		// El día de salida queda libre para la siguiente reserva
		cotizar(jwtUtil.generateToken(otro), BASE.plusDays(3), BASE.plusDays(5), 1)
				.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("CA-072 Varias reservas simultáneas sobre las mismas fechas: solo una se confirma")
	void reservasSimultaneas_CA072() throws Exception {
		List<String> tokens = new ArrayList<>();
		List<Long> cotizaciones = new ArrayList<>();
		for (int i = 0; i < SIMULTANEOS; i++) {
			String token = jwtUtil.generateToken(crearUsuario("huesped" + i + "@correo.com", Role.HUESPED));
			tokens.add(token);
			cotizaciones.add(idCotizacion(cotizar(token, BASE, BASE.plusDays(2), 1).andExpect(status().isCreated())
					.andReturn().getResponse().getContentAsString()));
		}

		CountDownLatch salida = new CountDownLatch(1);
		ExecutorService hilos = Executors.newFixedThreadPool(SIMULTANEOS);
		try {
			List<Future<Integer>> respuestas = new ArrayList<>();
			for (int i = 0; i < SIMULTANEOS; i++) {
				String token = tokens.get(i);
				Long idCotizacion = cotizaciones.get(i);
				Callable<Integer> intento = () -> {
					salida.await();
					return reservar(token, idCotizacion).andReturn().getResponse().getStatus();
				};
				respuestas.add(hilos.submit(intento));
			}
			salida.countDown();

			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> respuesta : respuestas) {
				estados.add(respuesta.get(30, TimeUnit.SECONDS));
			}
			assertThat(estados).containsOnlyOnce(201).filteredOn(e -> e != 201).containsOnly(409)
					.hasSize(SIMULTANEOS - 1);
		} finally {
			hilos.shutdownNow();
		}
		assertThat(reservaRepository.findAll()).singleElement()
				.satisfies(r -> assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA));
	}

	@Test
	@DisplayName("CA-074 Si otra reserva tomó las fechas después de cotizar, la revalidación rechaza la reserva")
	void revalidaAntesDeConfirmar_CA074() throws Exception {
		Long primera = idCotizacion(cotizar(tokenHuesped, BASE, BASE.plusDays(4), 2)
				.andReturn().getResponse().getContentAsString());
		String tokenOtro = jwtUtil.generateToken(crearUsuario("otro@correo.com", Role.HUESPED));
		Long segunda = idCotizacion(cotizar(tokenOtro, BASE.plusDays(2), BASE.plusDays(6), 1)
				.andReturn().getResponse().getContentAsString());

		reservar(tokenOtro, segunda).andExpect(status().isCreated());
		reservar(tokenHuesped, primera)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(ReservaService.PERIODO_TOMADO));

		assertThat(reservaRepository.findAll()).hasSize(1);
		assertThat(cotizacionRepository.findById(primera).orElseThrow().getIdReserva()).isNull();
	}

	@Test
	@DisplayName("CA-071 Si la publicación dejó de estar ACTIVA después de cotizar, no se puede reservar")
	void publicacionNoActiva_CA071() throws Exception {
		Long idCotizacion = idCotizacion(cotizar(tokenHuesped, BASE, BASE.plusDays(2), 1)
				.andReturn().getResponse().getContentAsString());
		Alojamiento alojamiento = alojamientoRepository.findById(idAlojamiento).orElseThrow();
		alojamiento.setEstado(EstadoAlojamiento.PAUSADA);
		alojamientoRepository.save(alojamiento);

		reservar(tokenHuesped, idCotizacion)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(
						"El alojamiento no está disponible para nuevas reservas (estado PAUSADA)."));
		cotizar(tokenHuesped, BASE, BASE.plusDays(2), 1).andExpect(status().isConflict());
		assertThat(reservaRepository.count()).isZero();
	}

	@Test
	@DisplayName("Una cotización solo sirve para una reserva")
	void cotizacionUsada() throws Exception {
		Long idCotizacion = idCotizacion(cotizar(tokenHuesped, BASE, BASE.plusDays(2), 1)
				.andReturn().getResponse().getContentAsString());
		reservar(tokenHuesped, idCotizacion).andExpect(status().isCreated());

		reservar(tokenHuesped, idCotizacion)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(ReservaService.COTIZACION_NO_VIGENTE));
		assertThat(reservaRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("CA-131 Solo los huéspedes reservan; la cotización de otro usuario no existe para él")
	void permisos_CA131() throws Exception {
		Long idCotizacion = idCotizacion(cotizar(tokenHuesped, BASE, BASE.plusDays(2), 1)
				.andReturn().getResponse().getContentAsString());

		reservar(tokenAnfitrion, idCotizacion).andExpect(status().isForbidden());
		mockMvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"idCotizacion\": " + idCotizacion + "}")).andExpect(status().isUnauthorized());
		String tokenOtro = jwtUtil.generateToken(crearUsuario("otro@correo.com", Role.HUESPED));
		reservar(tokenOtro, idCotizacion)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(ReservaService.COTIZACION_NO_EXISTE));
		assertThat(reservaRepository.count()).isZero();
	}

	@Test
	@DisplayName("Datos inválidos al cotizar o reservar responden 400 o 404")
	void datosInvalidos() throws Exception {
		cotizar(tokenHuesped, BASE.plusDays(2), BASE, 1).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CalendarioService.SALIDA_NO_POSTERIOR));
		cotizar(tokenHuesped, BASE, BASE.plusDays(2), 9).andExpect(status().isBadRequest());
		cotizar(tokenHuesped, BASE.plusYears(2), BASE.plusYears(2).plusDays(1), 1)
				.andExpect(status().isConflict());
		mockMvc.perform(post("/cotizaciones").header("Authorization", "Bearer " + tokenHuesped)
				.contentType(MediaType.APPLICATION_JSON).content("{\"idAlojamiento\": 999999, \"fechaEntrada\": \""
						+ BASE + "\", \"fechaSalida\": \"" + BASE.plusDays(1) + "\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/reservas").header("Authorization", "Bearer " + tokenHuesped)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ReservaService.FALTA_COTIZACION));
		reservar(tokenHuesped, 999999L).andExpect(status().isNotFound());
	}

	private ResultActions cotizar(String token, LocalDate entrada, LocalDate salida, int huespedes)
			throws Exception {
		return mockMvc.perform(post("/cotizaciones").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idAlojamiento\": " + idAlojamiento + ", \"fechaEntrada\": \"" + entrada
						+ "\", \"fechaSalida\": \"" + salida + "\", \"huespedes\": " + huespedes + "}"));
	}

	private ResultActions reservar(String token, Long idCotizacion) throws Exception {
		return mockMvc.perform(post("/reservas").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"idCotizacion\": " + idCotizacion + "}"));
	}

	/**
	 * Id de la cotización en la respuesta.
	 *
	 * @param json respuesta de {@code POST /cotizaciones}
	 * @return id de la cotización
	 */
	private static Long idCotizacion(String json) {
		Number id = JsonPath.read(json, "$.idCotizacion");
		return id.longValue();
	}

	/**
	 * Guarda un alojamiento ACTIVA del anfitrión, con un período disponible de
	 * dos meses desde la fecha base.
	 *
	 * @param idAnfitrion dueño
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(Long idAnfitrion) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setIdAnfitrion(idAnfitrion);
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setPorcentajeLimpieza(new BigDecimal("5"));
		alojamiento.setPorcentajeServicio(new BigDecimal("10"));
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
