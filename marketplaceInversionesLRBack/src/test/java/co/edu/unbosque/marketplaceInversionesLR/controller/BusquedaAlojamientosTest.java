package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-23 Buscar alojamientos con filtros sobre la API completa y un
 * PostgreSQL temporal en Docker.
 * <p>
 * Datos: tres publicaciones ACTIVA con calendario (Medellín, Cartagena y
 * Guatapé), una ACTIVA sin calendario (Bogotá), una PAUSADA y una en
 * BORRADOR, que nunca deben aparecer. Cartagena tiene una reserva confirmada;
 * Medellín y Guatapé tienen reseñas publicadas.
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class BusquedaAlojamientosTest {

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
	private ResenaRepository resenaRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Huésped que deja las reseñas y la reserva. */
	private Usuario huesped;

	/** Anfitrión dueño de todas las publicaciones. */
	private Usuario anfitrion;

	/** Casa ACTIVA en Medellín, 6 huéspedes, 350.000, wifi y parqueadero; 2 reseñas (4 y 5). */
	private Long medellin;

	/** Apartamento ACTIVA en Cartagena, 4 huéspedes, 420.000, wifi y piscina; reservado y una reseña oculta. */
	private Long cartagena;

	/** Cabaña ACTIVA en Guatapé, 2 huéspedes, 200.000, wifi, piscina y parqueadero; 1 reseña (5). */
	private Long guatape;

	/** Casa ACTIVA en Bogotá sin calendario, 3 huéspedes, 300.000, sin amenidades. */
	private Long bogota;

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = crearUsuario("anfitrion.busqueda@correo.com", Role.ANFITRION);
		huesped = crearUsuario("huesped.busqueda@correo.com", Role.HUESPED);

		medellin = crearAlojamiento("Casa en El Poblado", "Medellín", "Casa", 6, "350000", EstadoAlojamiento.ACTIVA,
				4, true, "wifi", "parqueadero");
		cartagena = crearAlojamiento("Apartamento frente al mar", "Cartagena", "Apartamento", 4, "420000",
				EstadoAlojamiento.ACTIVA, 3, true, "wifi", "piscina");
		guatape = crearAlojamiento("Cabaña en el lago", "Guatapé", "Cabaña", 2, "200000", EstadoAlojamiento.ACTIVA, 2,
				true, "wifi", "piscina", "parqueadero");
		bogota = crearAlojamiento("Casa sin calendario", "Bogotá", "Casa", 3, "300000", EstadoAlojamiento.ACTIVA, 1,
				false);
		crearAlojamiento("Casa pausada", "Medellín", "Casa", 6, "100000", EstadoAlojamiento.PAUSADA, 0, true, "wifi");
		crearAlojamiento("Casa en borrador", "Medellín", "Casa", 6, "90000", EstadoAlojamiento.BORRADOR, 0, true,
				"wifi");

		// Cartagena ocupada del día 5 al 8 desde la base
		reserva(cartagena, BASE.plusDays(5), BASE.plusDays(8), EstadoReserva.CONFIRMADA);
		// Reseñas: Medellín 4 y 5 (promedio 4,5), Guatapé 5, Cartagena una oculta que no cuenta
		resena(medellin, 4, EstadoResena.PUBLICADA);
		resena(medellin, 5, EstadoResena.PUBLICADA);
		resena(guatape, 5, EstadoResena.PUBLICADA);
		resena(cartagena, 1, EstadoResena.OCULTA);
	}

	/**
	 * Borra en orden de llaves foráneas: reseñas, pagos y reservas antes que
	 * los alojamientos, y estos antes que los usuarios.
	 */
	@AfterEach
	void limpiar() {
		resenaRepository.deleteAll();
		jdbc.update("DELETE FROM cotizacion");
		jdbc.update("DELETE FROM pago");
		reservaRepository.deleteAll();
		jdbc.update("DELETE FROM notificacion");
		periodoRepository.deleteAll();
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM servicio_adicional");
		jdbc.update("DELETE FROM favorito");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-087 Sin filtros solo aparecen publicaciones ACTIVA, las más recientes primero, sin pedir sesión")
	void soloActivas_CA087() throws Exception {
		buscar("").andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(bogota), id(guatape),
						id(cartagena), id(medellin))))
				.andExpect(jsonPath("$.contenido[*].estado").value(contains("ACTIVA", "ACTIVA", "ACTIVA", "ACTIVA")))
				.andExpect(jsonPath("$.totalElementos").value(4))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(12))
				.andExpect(jsonPath("$.totalPaginas").value(1));
	}

	@Test
	@DisplayName("CA-087 Ciudad y tipo filtran sin distinguir mayúsculas")
	void ciudadYTipo_CA087() throws Exception {
		buscar("?ciudad=MEDELLÍN").andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(medellin))));
		buscar("?tipo=casa").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(containsInAnyOrder(id(medellin), id(bogota))));
		buscar("?ciudad=medellín&tipo=Apartamento").andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	@DisplayName("CA-087 Solo aparecen los que tienen capacidad para los huéspedes")
	void capacidad_CA087() throws Exception {
		buscar("?huespedes=4").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(containsInAnyOrder(id(medellin), id(cartagena))));
		buscar("?huespedes=7").andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	@DisplayName("CA-087 El precio por noche debe estar dentro del rango, con los extremos incluidos")
	void rangoDePrecio_CA087() throws Exception {
		buscar("?precioMinimo=300000&precioMaximo=420000").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(containsInAnyOrder(id(medellin), id(cartagena), id(bogota))));
		buscar("?precioMaximo=200000").andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(guatape))));
		buscar("?precioMinimo=400000").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(cartagena))));
	}

	@Test
	@DisplayName("CA-087 Con varias amenidades, el alojamiento debe tenerlas todas")
	void amenidades_CA087() throws Exception {
		buscar("?amenidades=wifi").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(containsInAnyOrder(id(medellin), id(cartagena), id(guatape))));
		buscar("?amenidades=WIFI&amenidades=Piscina").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(containsInAnyOrder(id(cartagena), id(guatape))));
		buscar("?amenidades=piscina&amenidades=parqueadero").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(guatape))));
		buscar("?amenidades=wifi&amenidades=chimenea").andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	@DisplayName("CA-087 Con fechas solo aparecen los disponibles en todo el período")
	void disponibilidadEnElPeriodo_CA087() throws Exception {
		// Libre para todos los que tienen calendario
		buscar("?llegada=" + BASE.plusDays(1) + "&salida=" + BASE.plusDays(3))
				.andExpect(jsonPath("$.contenido[*].idAlojamiento")
						.value(containsInAnyOrder(id(medellin), id(cartagena), id(guatape))));
		// Se cruza con la reserva de Cartagena
		buscar("?llegada=" + BASE.plusDays(6) + "&salida=" + BASE.plusDays(7))
				.andExpect(jsonPath("$.contenido[*].idAlojamiento")
						.value(containsInAnyOrder(id(medellin), id(guatape))));
		// El día de salida de la reserva ya está libre
		buscar("?ciudad=Cartagena&llegada=" + BASE.plusDays(8) + "&salida=" + BASE.plusDays(9))
				.andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(cartagena))));
		// Fuera de los períodos ofrecidos no aparece nadie
		buscar("?llegada=" + BASE.plusMonths(3) + "&salida=" + BASE.plusMonths(3).plusDays(2))
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	@DisplayName("CA-087 Todos los filtros se combinan")
	void filtrosCombinados_CA087() throws Exception {
		buscar("?ciudad=Guatapé&tipo=cabaña&huespedes=2&precioMinimo=100000&precioMaximo=250000"
				+ "&amenidades=piscina&llegada=" + BASE.plusDays(1) + "&salida=" + BASE.plusDays(2))
				.andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(guatape))))
				.andExpect(jsonPath("$.contenido[0].ciudad").value("Guatapé"))
				.andExpect(jsonPath("$.contenido[0].precioNoche").value(200000))
				.andExpect(jsonPath("$.contenido[0].amenidades").value(containsInAnyOrder("wifi", "piscina",
						"parqueadero")))
				.andExpect(jsonPath("$.contenido[0].promedioCalificacion").value(5.0))
				.andExpect(jsonPath("$.contenido[0].cantidadResenas").value(1));
		buscar("?ciudad=Guatapé&huespedes=3").andExpect(jsonPath("$.totalElementos").value(0));
	}

	@Test
	@DisplayName("Órdenes: precio, recomendados y mejor calificados; la reseña oculta no cuenta")
	void ordenes() throws Exception {
		buscar("?orden=precio_menor").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(guatape), id(bogota), id(medellin), id(cartagena))));
		buscar("?orden=precio_mayor").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(cartagena), id(medellin), id(bogota), id(guatape))));
		// Más reseñas primero; empates por promedio y luego por el más reciente
		buscar("?orden=recomendados").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(medellin), id(guatape), id(bogota), id(cartagena))));
		// Mejor promedio primero
		buscar("?orden=MEJOR_CALIFICADOS").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(guatape), id(medellin), id(bogota), id(cartagena))))
				.andExpect(jsonPath("$.contenido[1].promedioCalificacion").value(4.5))
				.andExpect(jsonPath("$.contenido[1].cantidadResenas").value(2))
				.andExpect(jsonPath("$.contenido[3].cantidadResenas").value(0));
	}

	@Test
	@DisplayName("La paginación es estable y el tamaño queda entre 1 y 100")
	void paginacion() throws Exception {
		buscar("?pagina=1&tamanio=3").andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(medellin))))
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.totalElementos").value(4))
				.andExpect(jsonPath("$.totalPaginas").value(2));
		buscar("?orden=recomendados&pagina=1&tamanio=2").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(bogota), id(cartagena))))
				.andExpect(jsonPath("$.totalElementos").value(4));
		buscar("?tamanio=500").andExpect(jsonPath("$.tamanio").value(100));
		buscar("?tamanio=0&pagina=-3").andExpect(jsonPath("$.tamanio").value(1)).andExpect(jsonPath("$.pagina").value(0));
	}

	@Test
	@DisplayName("Filtros u orden inválidos responden 400 con la causa")
	void filtrosInvalidos() throws Exception {
		buscar("?llegada=" + BASE).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CalendarioService.FECHAS_FALTANTES));
		buscar("?llegada=" + BASE.plusDays(3) + "&salida=" + BASE).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CalendarioService.SALIDA_NO_POSTERIOR));
		buscar("?llegada=" + LocalDate.now().minusDays(1) + "&salida=" + BASE).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(CalendarioService.LLEGADA_EN_EL_PASADO));
		buscar("?huespedes=0").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.HUESPEDES_INVALIDOS));
		buscar("?precioMinimo=-1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.PRECIO_NEGATIVO));
		buscar("?precioMinimo=500000&precioMaximo=100000").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.PRECIOS_INVERTIDOS));
		buscar("?orden=barato").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.ORDEN_INVALIDO));
		buscar("?huespedes=dos").andExpect(status().isBadRequest());
		buscar("?llegada=mañana&salida=" + BASE).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("El catálogo muestra las ACTIVA con fechas desde hoy, recomendadas primero")
	void catalogo() throws Exception {
		mockMvc.perform(get("/alojamientos/disponibles")).andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(medellin), id(guatape),
						id(cartagena))))
				.andExpect(jsonPath("$.tamanio").value(20));
		mockMvc.perform(get("/alojamientos/disponibles").param("orden", "precio_menor"))
				.andExpect(jsonPath("$.contenido[*].idAlojamiento").value(contains(id(guatape), id(medellin),
						id(cartagena))));
		mockMvc.perform(get("/alojamientos/disponibles").param("orden", "otro"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PublicacionService.ORDEN_INVALIDO));
	}

	@Test
	@DisplayName("Los tipos se cuentan en cualquier estado, en orden alfabético")
	void tipos() throws Exception {
		mockMvc.perform(get("/alojamientos/tipos")).andExpect(status().isOk())
				.andExpect(jsonPath("$.Apartamento").value(1))
				.andExpect(jsonPath("$['Cabaña']").value(1))
				.andExpect(jsonPath("$.Casa").value(4));
	}

	@Test
	@DisplayName("CA-158 Una publicación aprobada por el administrador aparece en la búsqueda")
	void aprobadaApareceEnLaBusqueda_CA158() throws Exception {
		Long revision = crearAlojamiento("Finca en revisión", "Manizales", "Finca", 8, "500000",
				EstadoAlojamiento.PENDIENTE_REVISION, 0, true, "wifi");
		buscar("?ciudad=Manizales").andExpect(jsonPath("$.totalElementos").value(0));

		Usuario admin = crearUsuario("admin.busqueda@correo.com", Role.ADMIN);
		mockMvc.perform(post("/admin/alojamientos/" + revision + "/aprobar")
				.header("Authorization", "Bearer " + jwtUtil.generateToken(admin)))
				.andExpect(status().isOk());

		buscar("?ciudad=manizales&huespedes=8").andExpect(jsonPath("$.contenido[*].idAlojamiento")
				.value(contains(id(revision))))
				.andExpect(jsonPath("$.contenido[0].estado").value("ACTIVA"));
	}

	/**
	 * Busca sin sesión con los parámetros indicados.
	 *
	 * @param parametros consulta, por ejemplo {@code ?ciudad=Cali}; vacío sin
	 *                   filtros
	 * @return la respuesta
	 */
	private ResultActions buscar(String parametros) throws Exception {
		return mockMvc.perform(get("/alojamientos" + parametros));
	}

	/**
	 * Id como entero, que es como lo devuelve JsonPath cuando es pequeño.
	 *
	 * @param id id del alojamiento
	 * @return el id como {@code int}
	 */
	private static int id(Long id) {
		return id.intValue();
	}

	/**
	 * Guarda un alojamiento del anfitrión de prueba. Con calendario, ofrece dos
	 * meses desde la fecha base.
	 *
	 * @param titulo     título
	 * @param ciudad     ciudad
	 * @param tipo       tipo
	 * @param capacidad  huéspedes máximos
	 * @param precio     precio por noche
	 * @param estado     estado de la publicación
	 * @param diasAtras  hace cuántos días se creó (define "los más recientes")
	 * @param calendario {@code true} para agregarle un período disponible
	 * @param amenidades amenidades
	 * @return id del alojamiento
	 */
	private Long crearAlojamiento(String titulo, String ciudad, String tipo, int capacidad, String precio,
			EstadoAlojamiento estado, int diasAtras, boolean calendario, String... amenidades) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo(titulo);
		alojamiento.setCiudad(ciudad);
		alojamiento.setTipo(tipo);
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(estado);
		alojamiento.setCapacidad(capacidad);
		alojamiento.setPrecioNoche(new BigDecimal(precio));
		alojamiento.getAmenidades().addAll(List.of(amenidades));
		alojamiento.setFechaCreacion(LocalDateTime.now().minusDays(diasAtras));
		Long id = alojamientoRepository.save(alojamiento).getIdAlojamiento();
		if (calendario) {
			PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
			periodo.setIdAlojamiento(id);
			periodo.setFechaInicio(BASE);
			periodo.setFechaFin(BASE.plusMonths(2));
			periodoRepository.save(periodo);
		}
		return id;
	}

	/**
	 * Guarda una reserva del huésped de prueba.
	 *
	 * @param idAlojamiento alojamiento
	 * @param entrada       día de llegada
	 * @param salida        día de salida
	 * @param estado        estado de la reserva
	 * @return id de la reserva
	 */
	private Long reserva(Long idAlojamiento, LocalDate entrada, LocalDate salida, EstadoReserva estado) {
		Reserva reserva = new Reserva();
		reserva.setIdAlojamiento(idAlojamiento);
		reserva.setIdHuesped(huesped.getIdUsuario());
		reserva.setIdCotizacion(1L);
		reserva.setFechaEntrada(entrada);
		reserva.setFechaSalida(salida);
		reserva.setNoches((int) (salida.toEpochDay() - entrada.toEpochDay()));
		reserva.setHuespedes(1);
		reserva.setEstado(estado);
		reserva.setTotal(new BigDecimal("500000"));
		reserva.setFechaCreacion(LocalDateTime.now());
		return reservaRepository.save(reserva).getIdReserva();
	}

	/**
	 * Guarda una reseña de una estancia pasada del huésped de prueba.
	 *
	 * @param idAlojamiento alojamiento reseñado
	 * @param calificacion  de 1 a 5
	 * @param estado        PUBLICADA u OCULTA
	 */
	private void resena(Long idAlojamiento, int calificacion, EstadoResena estado) {
		LocalDate salida = LocalDate.now().minusDays(10L + resenaRepository.count());
		Resena resena = new Resena();
		resena.setIdAlojamiento(idAlojamiento);
		resena.setIdUsuario(huesped.getIdUsuario());
		resena.setIdReserva(reserva(idAlojamiento, salida.minusDays(2), salida, EstadoReserva.CONFIRMADA));
		resena.setCalificacion(calificacion);
		resena.setComentario("Estancia de prueba");
		resena.setEstado(estado);
		resena.setFecha(LocalDateTime.now());
		resenaRepository.save(resena);
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
