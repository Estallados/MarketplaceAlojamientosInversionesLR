package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import co.edu.unbosque.marketplaceInversionesLR.dto.ImagenDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas unitarias de HU-25 Consultar estado e historial de reservas, con los
 * repositorios simulados: el huésped ve las suyas (CA-078), el anfitrión las
 * de sus alojamientos (CA-079) y el detalle solo lo ven el huésped, el
 * anfitrión y los administradores.
 */
@ExtendWith(MockitoExtension.class)
class HistorialReservasServiceTest {

	/** Alojamiento de prueba. */
	private static final Long ID_ALOJAMIENTO = 10L;

	/** Anfitrión dueño del alojamiento. */
	private static final Long ID_ANFITRION = 1L;

	/** Huésped que reservó. */
	private static final Long ID_HUESPED = 3L;

	/** Usuario sin relación con la reserva. */
	private static final Long ID_OTRO = 99L;

	/** Reserva de prueba. */
	private static final Long ID_RESERVA = 15L;

	/** Política del alojamiento. */
	private static final Long ID_POLITICA = 2L;

	/** Página por defecto. */
	private static final Pageable PAGINA = PageRequest.of(0, 20);

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private CotizacionRepository cotizacionRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private PagoRepository pagoRepo;

	@Mock
	private CalendarioService calendarioService;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private NotificacionService notificacionService;

	@Mock
	private PlatformTransactionManager transactionManager;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private PoliticaCancelacionRepository politicaRepo;

	@Mock
	private ResenaRepository resenaRepo;

	@Mock
	private CancelacionRepository cancelacionRepo;

	@Mock
	private PublicacionService publicacionService;

	private ReservaService servicio;

	/** Reserva CONFIRMADA futura del huésped. */
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		ModelMapper modelMapper = new ModelMapper();
		servicio = new ReservaService(alojamientoRepo, cotizacionRepo, reservaRepo, calendarioService, null,
				new PagoService(pagoRepo), notificacionService, auditoriaService, modelMapper,
				JsonMapper.builder().build(), transactionManager, usuarioRepo, politicaRepo, resenaRepo,
				cancelacionRepo, publicacionService);
		reserva = reserva(EstadoReserva.CONFIRMADA, LocalDate.now().plusDays(10));
	}

	@Test
	@DisplayName("CA-078 El huésped ve sus reservas con el estado, el alojamiento, la imagen, la política, el pago y "
			+ "los nombres")
	void historialDelHuesped_CA078() {
		when(reservaRepo.findByIdHuesped(ID_HUESPED, PAGINA)).thenReturn(new PageImpl<>(List.of(reserva), PAGINA, 1));
		conAlojamiento(ID_POLITICA);
		when(politicaRepo.findById(ID_POLITICA)).thenReturn(Optional.of(politica("MODERADA")));
		when(pagoRepo.findTopByIdReservaOrderByFechaDescIdPagoDesc(ID_RESERVA)).thenReturn(Optional.of(pago()));
		when(publicacionService.imagenesDTO(ID_ALOJAMIENTO))
				.thenReturn(List.of(imagen("https://img/principal.jpg"), imagen("https://img/otra.jpg")));
		when(usuarioRepo.findById(ID_HUESPED)).thenReturn(Optional.of(usuario("Ana Huésped")));
		when(usuarioRepo.findById(ID_ANFITRION)).thenReturn(Optional.of(usuario("Beto Anfitrión")));

		PaginaDTO<ReservaDTO> pagina = servicio.listarMias(ID_HUESPED, PAGINA);

		assertThat(pagina.getTotalElementos()).isEqualTo(1);
		ReservaDTO dto = pagina.getContenido().get(0);
		assertThat(dto.getIdReserva()).isEqualTo(ID_RESERVA);
		assertThat(dto.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(dto.getTotal()).isEqualByComparingTo("600000.00");
		assertThat(dto.getTituloAlojamiento()).isEqualTo("Casa en Villa de Leyva");
		assertThat(dto.getCiudad()).isEqualTo("Villa de Leyva");
		assertThat(dto.getUbicacion()).isEqualTo("Calle 1 # 2-3");
		assertThat(dto.getTipoAlojamiento()).isEqualTo("Casa");
		assertThat(dto.getCapacidadAlojamiento()).isEqualTo(4);
		assertThat(dto.getImagenAlojamiento()).isEqualTo("https://img/principal.jpg");
		assertThat(dto.getNombrePolitica()).isEqualTo("MODERADA");
		assertThat(dto.getIdAnfitrion()).isEqualTo(ID_ANFITRION);
		assertThat(dto.getNombreAnfitrion()).isEqualTo("Beto Anfitrión");
		assertThat(dto.getNombreHuesped()).isEqualTo("Ana Huésped");
		assertThat(dto.getPago().getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(dto.getCompletada()).isFalse();
		assertThat(dto.getTieneResena()).isFalse();
		assertThat(dto.getCancelacion()).isNull();
	}

	@Test
	@DisplayName("CA-078 Un huésped sin reservas recibe una página vacía")
	void historialVacio_CA078() {
		when(reservaRepo.findByIdHuesped(ID_OTRO, PAGINA)).thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

		PaginaDTO<ReservaDTO> pagina = servicio.listarMias(ID_OTRO, PAGINA);

		assertThat(pagina.getContenido()).isEmpty();
		assertThat(pagina.getTotalElementos()).isZero();
	}

	@Test
	@DisplayName("CA-079 El anfitrión consulta solo las reservas de sus alojamientos")
	void reservasDelAnfitrion_CA079() {
		when(reservaRepo.buscarDeAnfitrion(ID_ANFITRION, PAGINA)).thenReturn(new PageImpl<>(List.of(reserva), PAGINA, 1));
		conAlojamiento(null);

		PaginaDTO<ReservaDTO> pagina = servicio.listarDeAnfitrion(ID_ANFITRION, PAGINA);

		verify(reservaRepo).buscarDeAnfitrion(ID_ANFITRION, PAGINA);
		assertThat(pagina.getContenido()).extracting(ReservaDTO::getIdAnfitrion).containsExactly(ID_ANFITRION);
		// Sin política asignada se informa la política por defecto
		assertThat(pagina.getContenido().get(0).getNombrePolitica())
				.isEqualTo(CancelacionService.POLITICA_POR_DEFECTO);
		assertThat(pagina.getContenido().get(0).getPago()).isNull();
	}

	@Test
	@DisplayName("El detalle lo ven el huésped, el anfitrión del alojamiento y un administrador")
	void detalleVisibleParaLosInvolucrados() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		conAlojamiento(null);

		assertThat(servicio.obtener(ID_RESERVA, ID_HUESPED, false).esExito()).isTrue();
		assertThat(servicio.obtener(ID_RESERVA, ID_ANFITRION, false).esExito()).isTrue();
		Resultado<ReservaDTO> admin = servicio.obtener(ID_RESERVA, ID_OTRO, true);
		assertThat(admin.esExito()).isTrue();
		assertThat(admin.getDato().getIdReserva()).isEqualTo(ID_RESERVA);
	}

	@Test
	@DisplayName("El detalle responde 404 a otro usuario, a una reserva que no existe y sin id")
	void detalleOcultoParaLosDemas() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		conAlojamiento(null);
		when(reservaRepo.findById(404L)).thenReturn(Optional.empty());

		for (Resultado<ReservaDTO> resultado : List.of(servicio.obtener(ID_RESERVA, ID_OTRO, false),
				servicio.obtener(404L, ID_HUESPED, false), servicio.obtener(null, ID_HUESPED, true))) {
			assertThat(resultado.esExito()).isFalse();
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
			assertThat(resultado.getMensaje()).isEqualTo(ReservaService.NO_EXISTE);
		}
	}

	@Test
	@DisplayName("El detalle de una reserva cancelada trae la cancelación con la devolución")
	void detalleConCancelacion() {
		reserva.setEstado(EstadoReserva.CANCELADA);
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		conAlojamiento(null);
		Cancelacion cancelacion = new Cancelacion();
		cancelacion.setIdCancelacion(7L);
		cancelacion.setIdReserva(ID_RESERVA);
		cancelacion.setMotivo("Cambio de planes");
		cancelacion.setPolitica("FLEXIBLE");
		cancelacion.setPorcentajeReembolso(100);
		cancelacion.setValorReembolso(new BigDecimal("600000.00"));
		cancelacion.setEstado(EstadoCancelacion.COMPLETADA);
		cancelacion.setEstadoFinalReserva(EstadoReserva.CANCELADA);
		when(cancelacionRepo.findByIdReserva(ID_RESERVA)).thenReturn(Optional.of(cancelacion));

		ReservaDTO dto = servicio.obtener(ID_RESERVA, ID_HUESPED, false).getDato();

		assertThat(dto.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(dto.getCancelacion().getIdCancelacion()).isEqualTo(7L);
		assertThat(dto.getCancelacion().getMotivo()).isEqualTo("Cambio de planes");
		assertThat(dto.getCancelacion().getValorReembolso()).isEqualByComparingTo("600000.00");
		assertThat(dto.getCompletada()).isFalse();
	}

	@Test
	@DisplayName("CA-016 Una reserva CONFIRMADA cuya salida ya llegó está completada e informa si ya tiene reseña")
	void completadaYResena_CA016() {
		Reserva pasada = reserva(EstadoReserva.CONFIRMADA, LocalDate.now().minusDays(5));
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(pasada));
		conAlojamiento(null);
		when(resenaRepo.existsByIdReserva(ID_RESERVA)).thenReturn(true);

		ReservaDTO dto = servicio.obtener(ID_RESERVA, ID_HUESPED, false).getDato();

		assertThat(dto.getCompletada()).isTrue();
		assertThat(dto.getTieneResena()).isTrue();
	}

	@Test
	@DisplayName("Solo está completada una reserva CONFIRMADA con la salida hoy o antes")
	void esCompletada() {
		assertThat(ReservaService.esCompletada(reserva(EstadoReserva.CONFIRMADA, LocalDate.now().minusDays(3))))
				.isTrue();
		Reserva saleHoy = reserva(EstadoReserva.CONFIRMADA, LocalDate.now().minusDays(3));
		saleHoy.setFechaSalida(LocalDate.now());
		assertThat(ReservaService.esCompletada(saleHoy)).isTrue();
		assertThat(ReservaService.esCompletada(reserva(EstadoReserva.CONFIRMADA, LocalDate.now().plusDays(1))))
				.isFalse();
		assertThat(ReservaService.esCompletada(reserva(EstadoReserva.CANCELADA, LocalDate.now().minusDays(10))))
				.isFalse();
		assertThat(ReservaService.esCompletada(reserva(EstadoReserva.EN_REVISION, LocalDate.now().minusDays(10))))
				.isFalse();
	}

	/**
	 * Simula el alojamiento del anfitrión.
	 *
	 * @param idPolitica política asignada; {@code null} si no tiene
	 */
	private void conAlojamiento(Long idPolitica) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID_ALOJAMIENTO);
		alojamiento.setIdAnfitrion(ID_ANFITRION);
		alojamiento.setTitulo("Casa en Villa de Leyva");
		alojamiento.setCiudad("Villa de Leyva");
		alojamiento.setUbicacion("Calle 1 # 2-3");
		alojamiento.setTipo("Casa");
		alojamiento.setCapacidad(4);
		alojamiento.setIdPolitica(idPolitica);
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
	}

	/**
	 * Reserva de tres noches del huésped.
	 *
	 * @param estado  estado
	 * @param llegada día de llegada
	 * @return la reserva
	 */
	private static Reserva reserva(EstadoReserva estado, LocalDate llegada) {
		Reserva r = new Reserva();
		r.setIdReserva(ID_RESERVA);
		r.setIdAlojamiento(ID_ALOJAMIENTO);
		r.setIdHuesped(ID_HUESPED);
		r.setFechaEntrada(llegada);
		r.setFechaSalida(llegada.plusDays(3));
		r.setNoches(3);
		r.setHuespedes(2);
		r.setEstado(estado);
		r.setTotal(new BigDecimal("600000.00"));
		r.setFechaCreacion(LocalDateTime.now());
		return r;
	}

	/**
	 * Pago aprobado de la reserva.
	 *
	 * @return el pago
	 */
	private static Pago pago() {
		Pago pago = new Pago();
		pago.setIdPago(1L);
		pago.setIdReserva(ID_RESERVA);
		pago.setValor(new BigDecimal("600000.00"));
		pago.setEstado(EstadoPago.APROBADO);
		pago.setFecha(LocalDateTime.now());
		return pago;
	}

	/**
	 * Política de cancelación.
	 *
	 * @param nombre nombre
	 * @return la política
	 */
	private static PoliticaCancelacion politica(String nombre) {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setIdPolitica(ID_POLITICA);
		politica.setNombre(nombre);
		return politica;
	}

	/**
	 * Imagen del alojamiento.
	 *
	 * @param url dirección
	 * @return la imagen
	 */
	private static ImagenDTO imagen(String url) {
		ImagenDTO imagen = new ImagenDTO();
		imagen.setUrl(url);
		return imagen;
	}

	/**
	 * Usuario con nombre.
	 *
	 * @param nombre nombre
	 * @return el usuario
	 */
	private static Usuario usuario(String nombre) {
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
		return usuario;
	}
}
