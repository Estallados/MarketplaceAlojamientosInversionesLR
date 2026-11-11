package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaEntradaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResumenResenasDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-03 Calificar, reseñar y consultar reseñas, con los
 * repositorios, las notificaciones y la auditoría simulados.
 */
@ExtendWith(MockitoExtension.class)
class ResenaServiceTest {

	/** Huésped que reseña. */
	private static final Long ID_HUESPED = 3L;

	/** Anfitrión del alojamiento. */
	private static final Long ID_ANFITRION = 1L;

	/** Alojamiento de la reserva. */
	private static final Long ID_ALOJAMIENTO = 10L;

	/** Reserva completada. */
	private static final Long ID_RESERVA = 15L;

	/** Página por defecto. */
	private static final Pageable PAGINA = PageRequest.of(0, 20);

	@Mock
	private ResenaRepository resenaRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private NotificacionService notificacionService;

	@Mock
	private AuditoriaService auditoriaService;

	private ResenaService servicio;

	/** Reserva CONFIRMADA cuya salida fue hace dos días. */
	private Reserva reserva;

	@BeforeEach
	void preparar() {
		servicio = new ResenaService(resenaRepo, reservaRepo, alojamientoRepo, usuarioRepo, notificacionService,
				auditoriaService, new ModelMapper());
		reserva = reserva(EstadoReserva.CONFIRMADA, LocalDate.now().minusDays(2));
	}

	@Test
	@DisplayName("CA-016 CA-018 El huésped reseña su reserva completada: queda PUBLICADA y asociada a huésped, reserva, "
			+ "alojamiento, fecha y contenido")
	void reseniaPublicada_CA016_CA018() {
		listaParaReseniar();
		when(resenaRepo.saveAndFlush(any(Resena.class))).thenAnswer(i -> {
			Resena r = i.getArgument(0);
			r.setIdResena(9L);
			return r;
		});
		when(usuarioRepo.findById(ID_HUESPED)).thenReturn(Optional.of(usuario("Ana Huésped")));

		ResenaEntradaDTO datos = entrada(ID_RESERVA, 5, "  Muy limpio y bien ubicado.  ");
		Resultado<ResenaDTO> resultado = servicio.crear(datos, ID_HUESPED);

		assertThat(resultado.esExito()).isTrue();
		ArgumentCaptor<Resena> guardada = ArgumentCaptor.forClass(Resena.class);
		verify(resenaRepo).saveAndFlush(guardada.capture());
		Resena resena = guardada.getValue();
		assertThat(resena.getIdUsuario()).isEqualTo(ID_HUESPED);
		assertThat(resena.getIdReserva()).isEqualTo(ID_RESERVA);
		assertThat(resena.getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(resena.getCalificacion()).isEqualTo(5);
		assertThat(resena.getComentario()).isEqualTo("Muy limpio y bien ubicado.");
		assertThat(resena.getEstado()).isEqualTo(EstadoResena.PUBLICADA);
		assertThat(resena.getFecha()).isNotNull();

		ResenaDTO dto = resultado.getDato();
		assertThat(dto.getIdResena()).isEqualTo(9L);
		assertThat(dto.getNombreUsuario()).isEqualTo("Ana Huésped");
		assertThat(dto.getTituloAlojamiento()).isEqualTo("Casa en Villa de Leyva");
		verify(notificacionService).enviar(ID_ANFITRION, NotificacionService.RESENA_RECIBIDA,
				"Recibiste una reseña de 5 estrellas en \"Casa en Villa de Leyva\".");
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.RESENA_CREADA),
				eq(AuditoriaService.ENTIDAD_RESENA), eq(9L), eq(ResultadoAuditoria.EXITOSO), contains("Reserva 15"));
	}

	@Test
	@DisplayName("CA-018 Un comentario vacío se guarda como null y una estrella se dice en singular")
	void comentarioVacio_CA018() {
		listaParaReseniar();
		when(resenaRepo.saveAndFlush(any(Resena.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(servicio.crear(entrada(ID_RESERVA, 1, "   "), ID_HUESPED).esExito()).isTrue();

		ArgumentCaptor<Resena> guardada = ArgumentCaptor.forClass(Resena.class);
		verify(resenaRepo).saveAndFlush(guardada.capture());
		assertThat(guardada.getValue().getComentario()).isNull();
		verify(notificacionService).enviar(eq(ID_ANFITRION), eq(NotificacionService.RESENA_RECIBIDA),
				contains("1 estrella en"));
	}

	@Test
	@DisplayName("CA-016 No se reseña una reserva que no está completada: futura, cancelada, en revisión o pendiente")
	void soloCompletadas_CA016() {
		for (Reserva noCompletada : List.of(reserva(EstadoReserva.CONFIRMADA, LocalDate.now().plusDays(1)),
				reserva(EstadoReserva.CANCELADA, LocalDate.now().minusDays(5)),
				reserva(EstadoReserva.EN_REVISION, LocalDate.now().minusDays(5)),
				reserva(EstadoReserva.PENDIENTE_PAGO, LocalDate.now().minusDays(5)))) {
			when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(noCompletada));

			Resultado<ResenaDTO> resultado = servicio.crear(entrada(ID_RESERVA, 4, null), ID_HUESPED);

			assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
			assertThat(resultado.getMensaje()).isEqualTo(ResenaService.NO_COMPLETADA);
		}
		verify(resenaRepo, never()).saveAndFlush(any());
		verifyNoInteractions(notificacionService);
		verify(auditoriaService, times(4)).registrar(eq(ID_HUESPED),
				eq(AuditoriaService.RESENA_CREADA), eq(AuditoriaService.ENTIDAD_RESENA), isNull(),
				eq(ResultadoAuditoria.RECHAZADO), eq(ResenaService.NO_COMPLETADA));
	}

	@Test
	@DisplayName("CA-016 Una reserva que salió hoy ya está completada")
	void saleHoy_CA016() {
		reserva = reserva(EstadoReserva.CONFIRMADA, LocalDate.now());
		listaParaReseniar();
		when(resenaRepo.saveAndFlush(any(Resena.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(servicio.crear(entrada(ID_RESERVA, 4, null), ID_HUESPED).esExito()).isTrue();
	}

	@Test
	@DisplayName("Una reserva ya reseñada responde 409 y no se guarda otra")
	void yaResenada() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(resenaRepo.existsByIdReserva(ID_RESERVA)).thenReturn(true);

		Resultado<ResenaDTO> resultado = servicio.crear(entrada(ID_RESERVA, 5, null), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(ResenaService.YA_RESENADA);
		verify(resenaRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("Si otra petición reseñó la misma reserva al mismo tiempo, la restricción única responde 409")
	void duplicadoSimultaneo() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(resenaRepo.existsByIdReserva(ID_RESERVA)).thenReturn(false, true);
		when(resenaRepo.saveAndFlush(any(Resena.class))).thenThrow(new DataIntegrityViolationException("uq"));

		Resultado<ResenaDTO> resultado = servicio.crear(entrada(ID_RESERVA, 5, null), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(ResenaService.YA_RESENADA);
		verifyNoInteractions(notificacionService);
	}

	@Test
	@DisplayName("Otra violación de la base de datos no se esconde como duplicado")
	void otraViolacionSePropaga() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(resenaRepo.existsByIdReserva(ID_RESERVA)).thenReturn(false);
		DataIntegrityViolationException error = new DataIntegrityViolationException("trigger");
		when(resenaRepo.saveAndFlush(any(Resena.class))).thenThrow(error);

		assertThatThrownBy(() -> servicio.crear(entrada(ID_RESERVA, 5, null), ID_HUESPED)).isSameAs(error);
	}

	@Test
	@DisplayName("La reserva de otro huésped o inexistente responde 404 sin revelar que existe")
	void reservaAjena() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(reservaRepo.findById(404L)).thenReturn(Optional.empty());

		for (Resultado<ResenaDTO> resultado : List.of(servicio.crear(entrada(ID_RESERVA, 5, null), 99L),
				servicio.crear(entrada(404L, 5, null), ID_HUESPED))) {
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
			assertThat(resultado.getMensaje()).isEqualTo(ReservaService.NO_EXISTE);
		}
		verify(resenaRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("Faltan datos, la calificación está fuera de 1 a 5 o el comentario es muy largo: 400")
	void validaciones() {
		for (ResenaEntradaDTO invalida : List.of(entrada(null, 5, null), entrada(ID_RESERVA, null, null),
				entrada(ID_RESERVA, 0, null), entrada(ID_RESERVA, 6, null))) {
			Resultado<ResenaDTO> resultado = servicio.crear(invalida, ID_HUESPED);
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
			assertThat(resultado.getMensaje()).isEqualTo(ResenaService.DATOS_INVALIDOS);
		}
		assertThat(servicio.crear(null, ID_HUESPED).getMensaje()).isEqualTo(ResenaService.DATOS_INVALIDOS);
		Resultado<ResenaDTO> largo = servicio.crear(entrada(ID_RESERVA, 5, "x".repeat(2001)), ID_HUESPED);
		assertThat(largo.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(largo.getMensaje()).isEqualTo(ResenaService.COMENTARIO_LARGO);
		verifyNoInteractions(reservaRepo, resenaRepo, auditoriaService);
	}

	@Test
	@DisplayName("CA-019 La consulta de un alojamiento trae solo sus reseñas PUBLICADA con promedio y cantidad")
	void resumenDelAlojamiento_CA019() {
		when(alojamientoRepo.existsById(ID_ALOJAMIENTO)).thenReturn(true);
		when(resenaRepo.promedio(ID_ALOJAMIENTO, EstadoResena.PUBLICADA)).thenReturn(14.0 / 3);
		when(resenaRepo.countByIdAlojamientoAndEstado(ID_ALOJAMIENTO, EstadoResena.PUBLICADA)).thenReturn(3L);
		when(resenaRepo.findByIdAlojamientoAndEstado(ID_ALOJAMIENTO, EstadoResena.PUBLICADA, PAGINA))
				.thenReturn(new PageImpl<>(List.of(resena(5), resena(5), resena(4)), PAGINA, 3));

		Resultado<ResumenResenasDTO> resultado = servicio.resumen(ID_ALOJAMIENTO, PAGINA);

		ResumenResenasDTO resumen = resultado.getDato();
		assertThat(resumen.getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(resumen.getPromedio()).isEqualTo(4.7);
		assertThat(resumen.getCantidad()).isEqualTo(3L);
		assertThat(resumen.getResenas().getTotalElementos()).isEqualTo(3);
		assertThat(resumen.getResenas().getContenido()).extracting(ResenaDTO::getIdAlojamiento)
				.containsOnly(ID_ALOJAMIENTO);
	}

	@Test
	@DisplayName("CA-019 Sin reseñas el promedio es null; un alojamiento que no existe responde 404")
	void resumenVacioYNoExiste_CA019() {
		when(alojamientoRepo.existsById(ID_ALOJAMIENTO)).thenReturn(true);
		// Sin filas, avg de la base devuelve null
		when(resenaRepo.promedio(ID_ALOJAMIENTO, EstadoResena.PUBLICADA)).thenReturn(null);
		when(resenaRepo.findByIdAlojamientoAndEstado(ID_ALOJAMIENTO, EstadoResena.PUBLICADA, PAGINA))
				.thenReturn(new PageImpl<>(List.of(), PAGINA, 0));

		ResumenResenasDTO vacio = servicio.resumen(ID_ALOJAMIENTO, PAGINA).getDato();
		assertThat(vacio.getPromedio()).isNull();
		assertThat(vacio.getCantidad()).isZero();

		Resultado<ResumenResenasDTO> noExiste = servicio.resumen(404L, PAGINA);
		assertThat(noExiste.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(noExiste.getMensaje()).isEqualTo(CalendarioService.NO_EXISTE);
		assertThat(servicio.resumen(null, PAGINA).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("El huésped ve todas sus reseñas, también las ocultas")
	void misResenas() {
		Resena oculta = resena(2);
		oculta.setEstado(EstadoResena.OCULTA);
		when(resenaRepo.findByIdUsuario(ID_HUESPED, PAGINA)).thenReturn(new PageImpl<>(List.of(oculta), PAGINA, 1));

		assertThat(servicio.listarMias(ID_HUESPED, PAGINA).getContenido()).extracting(ResenaDTO::getEstado)
				.containsExactly(EstadoResena.OCULTA);
	}

	@Test
	@DisplayName("El anfitrión ve las de todos sus alojamientos o las de uno propio; uno ajeno responde 404")
	void resenasDelAnfitrion() {
		when(resenaRepo.promedioDeAnfitrion(ID_ANFITRION, EstadoResena.PUBLICADA)).thenReturn(4.25);
		when(resenaRepo.contarDeAnfitrion(ID_ANFITRION, EstadoResena.PUBLICADA)).thenReturn(4L);
		when(resenaRepo.buscarDeAnfitrion(ID_ANFITRION, EstadoResena.PUBLICADA, PAGINA))
				.thenReturn(new PageImpl<>(List.of(resena(4)), PAGINA, 4));

		ResumenResenasDTO todas = servicio.resumenDeAnfitrion(ID_ANFITRION, null, PAGINA).getDato();
		assertThat(todas.getIdAlojamiento()).isNull();
		assertThat(todas.getPromedio()).isEqualTo(4.3);
		assertThat(todas.getCantidad()).isEqualTo(4L);

		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento()));
		when(resenaRepo.findByIdAlojamientoAndEstado(ID_ALOJAMIENTO, EstadoResena.PUBLICADA, PAGINA))
				.thenReturn(new PageImpl<>(List.of(), PAGINA, 0));
		assertThat(servicio.resumenDeAnfitrion(ID_ANFITRION, ID_ALOJAMIENTO, PAGINA).getDato().getIdAlojamiento())
				.isEqualTo(ID_ALOJAMIENTO);

		Resultado<ResumenResenasDTO> ajeno = servicio.resumenDeAnfitrion(77L, ID_ALOJAMIENTO, PAGINA);
		assertThat(ajeno.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		when(alojamientoRepo.findById(404L)).thenReturn(Optional.empty());
		assertThat(servicio.resumenDeAnfitrion(ID_ANFITRION, 404L, PAGINA).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
	}

	/** Simula la reserva completada del huésped, sin reseña y con su alojamiento. */
	private void listaParaReseniar() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(resenaRepo.existsByIdReserva(ID_RESERVA)).thenReturn(false);
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento()));
	}

	/**
	 * Datos de entrada de una reseña.
	 *
	 * @param idReserva    reserva
	 * @param calificacion calificación
	 * @param comentario   comentario
	 * @return los datos
	 */
	private static ResenaEntradaDTO entrada(Long idReserva, Integer calificacion, String comentario) {
		ResenaEntradaDTO datos = new ResenaEntradaDTO();
		datos.setIdReserva(idReserva);
		datos.setCalificacion(calificacion);
		datos.setComentario(comentario);
		return datos;
	}

	/**
	 * Reserva de tres noches del huésped.
	 *
	 * @param estado estado
	 * @param salida día de salida
	 * @return la reserva
	 */
	private static Reserva reserva(EstadoReserva estado, LocalDate salida) {
		Reserva r = new Reserva();
		r.setIdReserva(ID_RESERVA);
		r.setIdAlojamiento(ID_ALOJAMIENTO);
		r.setIdHuesped(ID_HUESPED);
		r.setFechaEntrada(salida.minusDays(3));
		r.setFechaSalida(salida);
		r.setEstado(estado);
		r.setTotal(new BigDecimal("600000.00"));
		return r;
	}

	/**
	 * Reseña PUBLICADA del alojamiento.
	 *
	 * @param calificacion calificación
	 * @return la reseña
	 */
	private static Resena resena(int calificacion) {
		Resena r = new Resena();
		r.setIdResena(1L);
		r.setIdReserva(ID_RESERVA);
		r.setIdAlojamiento(ID_ALOJAMIENTO);
		r.setIdUsuario(ID_HUESPED);
		r.setCalificacion(calificacion);
		r.setEstado(EstadoResena.PUBLICADA);
		r.setFecha(LocalDateTime.now());
		return r;
	}

	/**
	 * Alojamiento del anfitrión.
	 *
	 * @return el alojamiento
	 */
	private static Alojamiento alojamiento() {
		Alojamiento a = new Alojamiento();
		a.setIdAlojamiento(ID_ALOJAMIENTO);
		a.setIdAnfitrion(ID_ANFITRION);
		a.setTitulo("Casa en Villa de Leyva");
		return a;
	}

	/**
	 * Usuario con nombre.
	 *
	 * @param nombre nombre
	 * @return el usuario
	 */
	private static Usuario usuario(String nombre) {
		Usuario u = new Usuario();
		u.setNombre(nombre);
		return u;
	}
}
