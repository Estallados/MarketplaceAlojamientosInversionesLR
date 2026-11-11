package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.HistorialEstadoAlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ImagenRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-32 Revisar, aprobar o rechazar publicaciones, con la
 * base de datos y las notificaciones simuladas.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionServiceModeracionTest {

	/** Administrador que decide. */
	private static final Long ADMIN = 1L;

	/** Dueño de la publicación. */
	private static final Long ANFITRION = 4L;

	/** Publicación de prueba. */
	private static final Long ID = 20L;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private ImagenRepository imagenRepo;

	@Mock
	private PeriodoDisponibilidadRepository periodoRepo;

	@Mock
	private ServicioAdicionalRepository servicioRepo;

	@Mock
	private PoliticaCancelacionRepository politicaRepo;

	@Mock
	private HistorialEstadoAlojamientoRepository historialRepo;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private ResenaRepository resenaRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private AlmacenamientoService almacenamientoService;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private GeocodingService geocodingService;

	@Mock
	private NotificacionService notificacionService;

	private PublicacionService servicio;

	/** Publicación en revisión que devuelve el repositorio simulado. */
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new PublicacionService(alojamientoRepo, imagenRepo, periodoRepo, servicioRepo, politicaRepo,
				historialRepo, usuarioRepo, resenaRepo, reservaRepo, almacenamientoService, auditoriaService, geocodingService,
				notificacionService, new ModelMapper());
		alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID);
		alojamiento.setIdAnfitrion(ANFITRION);
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setEstado(EstadoAlojamiento.PENDIENTE_REVISION);
		lenient().when(alojamientoRepo.findById(ID)).thenReturn(Optional.of(alojamiento));
		lenient().when(alojamientoRepo.save(any(Alojamiento.class)))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	@Test
	@DisplayName("CA-158 CA-196 El administrador aprueba una publicación en revisión: queda ACTIVA, se avisa al "
			+ "anfitrión y la decisión queda auditada")
	void aprueba_CA158_CA196() {
		when(periodoRepo.existsByIdAlojamientoAndFechaFinAfter(eq(ID), any())).thenReturn(true);

		Resultado<AlojamientoDTO> resultado = servicio.aprobar(ID, ADMIN);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.ACTIVA);
		HistorialEstadoAlojamiento historial = historial();
		assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
		assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoAlojamiento.ACTIVA);
		assertThat(historial.getIdUsuario()).isEqualTo(ADMIN);
		assertThat(historial.getMotivo()).isEqualTo(PublicacionService.APROBADA);
		verify(notificacionService).enviar(eq(ANFITRION), eq(NotificacionService.ALOJAMIENTO_APROBADO),
				anyString());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO,
				"PENDIENTE_REVISION -> ACTIVA. Aprobada");
	}

	@Test
	@DisplayName("CA-158 No se aprueba si el anfitrión borró sus fechas mientras estaba en revisión")
	void noApruebaSinFechas_CA158() {
		when(periodoRepo.existsByIdAlojamientoAndFechaFinAfter(eq(ID), any())).thenReturn(false);

		Resultado<AlojamientoDTO> resultado = servicio.aprobar(ID, ADMIN);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(PublicacionService.SIN_FECHAS);
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
		verify(alojamientoRepo, never()).save(any());
		verify(notificacionService, never()).enviar(any(), anyString(), anyString());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO, PublicacionService.SIN_FECHAS);
	}

	@Test
	@DisplayName("CA-195 CA-196 El administrador rechaza con motivo: vuelve a BORRADOR, el motivo queda registrado, "
			+ "se avisa al anfitrión y la decisión queda auditada")
	void rechazaConMotivo_CA195_CA196() {
		Resultado<AlojamientoDTO> resultado = servicio.rechazar(ID, ADMIN, "  Las fotos no corresponden  ");

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		HistorialEstadoAlojamiento historial = historial();
		assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(historial.getMotivo()).isEqualTo("Rechazada: Las fotos no corresponden");
		ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
		verify(notificacionService).enviar(eq(ANFITRION), eq(NotificacionService.ALOJAMIENTO_RECHAZADO),
				mensaje.capture());
		assertThat(mensaje.getValue()).contains("Casa en el lago").contains("Motivo: Las fotos no corresponden")
				.contains("vuelve a enviarlo a revisión");
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO,
				"PENDIENTE_REVISION -> BORRADOR. Rechazada: Las fotos no corresponden");
	}

	@Test
	@DisplayName("CA-195 Sin motivo, o con uno de más de 500 caracteres, no se rechaza")
	void rechazoSinMotivo_CA195() {
		for (String motivo : new String[] { null, "", "   ", "x".repeat(501) }) {
			Resultado<AlojamientoDTO> resultado = servicio.rechazar(ID, ADMIN, motivo);

			assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
			assertThat(resultado.getMensaje()).isEqualTo(PublicacionService.MOTIVO_INVALIDO);
		}
		assertThat(servicio.rechazar(ID, ADMIN, "x".repeat(500)).esExito()).isTrue();
	}

	@Test
	@DisplayName("CA-196 Aprobar o rechazar algo que no está en revisión se rechaza y queda auditado")
	void soloDesdeRevision_CA196() {
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);

		Resultado<AlojamientoDTO> aprobar = servicio.aprobar(ID, ADMIN);
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		Resultado<AlojamientoDTO> rechazar = servicio.rechazar(ID, ADMIN, "Motivo");

		assertThat(aprobar.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(aprobar.getMensaje()).isEqualTo("No se puede pasar de BORRADOR a ACTIVA.");
		assertThat(rechazar.getMensaje()).isEqualTo("No se puede pasar de ACTIVA a BORRADOR.");
		verify(alojamientoRepo, never()).save(any());
		verify(notificacionService, never()).enviar(any(), anyString(), anyString());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO,
				"No se puede pasar de BORRADOR a ACTIVA.");
	}

	@Test
	@DisplayName("Aprobar o rechazar un alojamiento que no existe responde no encontrado")
	void noExiste() {
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.aprobar(99L, ADMIN).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.rechazar(99L, ADMIN, "Motivo").getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("CA-054 El administrador bloquea con motivo: queda BLOQUEADA, se avisa al anfitrión y queda auditado")
	void bloquea_CA054() {
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);

		Resultado<AlojamientoDTO> resultado = servicio.bloquear(ID, ADMIN, "  Publicidad falsa  ");

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.BLOQUEADA);
		HistorialEstadoAlojamiento historial = historial();
		assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoAlojamiento.ACTIVA);
		assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoAlojamiento.BLOQUEADA);
		assertThat(historial.getMotivo()).isEqualTo("Bloqueada: Publicidad falsa (0 reservas futuras en revisión)");
		verify(notificacionService).enviar(eq(ANFITRION), eq(NotificacionService.ALOJAMIENTO_BLOQUEADO), anyString());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO,
				"ACTIVA -> BLOQUEADA. Bloqueada: Publicidad falsa (0 reservas futuras en revisión)");
	}

	@Test
	@DisplayName("CA-112 Al bloquear, las reservas futuras se conservan, quedan EN_REVISION y se avisa a cada huésped")
	void conservaReservas_CA112() {
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		Reserva confirmada = reserva(70L, 31L, EstadoReserva.CONFIRMADA);
		Reserva pendiente = reserva(71L, 32L, EstadoReserva.PENDIENTE_PAGO);
		when(reservaRepo.findByIdAlojamientoAndFechaEntradaAfterAndEstadoIn(eq(ID), any(),
				eq(List.of(EstadoReserva.CONFIRMADA, EstadoReserva.PENDIENTE_PAGO))))
				.thenReturn(List.of(confirmada, pendiente));

		assertThat(servicio.bloquear(ID, ADMIN, "Revisión").esExito()).isTrue();

		assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.EN_REVISION);
		assertThat(pendiente.getEstado()).isEqualTo(EstadoReserva.EN_REVISION);
		verify(reservaRepo).save(confirmada);
		verify(reservaRepo).save(pendiente);
		verify(reservaRepo, never()).delete(any());
		verify(notificacionService).enviar(eq(31L), eq(NotificacionService.RESERVA_EN_REVISION), anyString());
		verify(notificacionService).enviar(eq(32L), eq(NotificacionService.RESERVA_EN_REVISION), anyString());
		assertThat(historial().getMotivo()).isEqualTo("Bloqueada: Revisión (2 reservas futuras en revisión)");
	}

	@Test
	@DisplayName("CA-054 Sin motivo, o con uno de más de 500 caracteres, no se bloquea")
	void bloqueoSinMotivo_CA054() {
		for (String motivo : new String[] { null, "", "   " }) {
			assertThat(servicio.bloquear(ID, ADMIN, motivo).getMensaje()).isEqualTo(PublicacionService.MOTIVO_BLOQUEO);
		}
		Resultado<AlojamientoDTO> largo = servicio.bloquear(ID, ADMIN, "x".repeat(501));

		assertThat(largo.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(largo.getMensaje()).isEqualTo(LimitesTexto.MOTIVO_LARGO);
		verify(alojamientoRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-054 Bloquear una publicación ya bloqueada se rechaza y queda auditado")
	void yaBloqueada_CA054() {
		alojamiento.setEstado(EstadoAlojamiento.BLOQUEADA);

		Resultado<AlojamientoDTO> resultado = servicio.bloquear(ID, ADMIN, "Otra vez");

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo("No se puede pasar de BLOQUEADA a BLOQUEADA.");
		verify(alojamientoRepo, never()).save(any());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO,
				"No se puede pasar de BLOQUEADA a BLOQUEADA.");
	}

	@Test
	@DisplayName("CA-054 Bloquear un alojamiento que no existe responde no encontrado")
	void bloqueoNoExiste_CA054() {
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.bloquear(99L, ADMIN, "Motivo").getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("Desbloquear pasa de BLOQUEADA a PAUSADA, queda en el historial y en la auditoría y no toca las "
			+ "reservas en revisión")
	void desbloquea() {
		alojamiento.setEstado(EstadoAlojamiento.BLOQUEADA);

		Resultado<AlojamientoDTO> resultado = servicio.desbloquear(ID, ADMIN);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.PAUSADA);
		HistorialEstadoAlojamiento historial = historial();
		assertThat(historial.getEstadoAnterior()).isEqualTo(EstadoAlojamiento.BLOQUEADA);
		assertThat(historial.getEstadoNuevo()).isEqualTo(EstadoAlojamiento.PAUSADA);
		assertThat(historial.getIdUsuario()).isEqualTo(ADMIN);
		assertThat(historial.getMotivo()).isEqualTo(PublicacionService.DESBLOQUEADA);
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO,
				"BLOQUEADA -> PAUSADA. " + PublicacionService.DESBLOQUEADA);
		verify(reservaRepo, never()).save(any());
	}

	@Test
	@DisplayName("Solo se desbloquea una publicación BLOQUEADA; lo demás responde 409 y queda auditado")
	void desbloquearSoloBloqueada() {
		for (EstadoAlojamiento estado : List.of(EstadoAlojamiento.ACTIVA, EstadoAlojamiento.PAUSADA,
				EstadoAlojamiento.PENDIENTE_REVISION, EstadoAlojamiento.BORRADOR)) {
			alojamiento.setEstado(estado);

			Resultado<AlojamientoDTO> resultado = servicio.desbloquear(ID, ADMIN);

			assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
			assertThat(resultado.getMensaje()).isEqualTo("No se puede pasar de " + estado + " a PAUSADA.");
		}
		verify(alojamientoRepo, never()).save(any());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO,
				"No se puede pasar de ACTIVA a PAUSADA.");
	}

	@Test
	@DisplayName("Desbloquear un alojamiento que no existe responde no encontrado")
	void desbloqueoNoExiste() {
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.desbloquear(99L, ADMIN).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.desbloquear(null, ADMIN).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("El administrador ve la cola de un estado o todos los alojamientos, paginados")
	void listaPorEstado() {
		Pageable pagina = PageRequest.of(0, 20);
		when(alojamientoRepo.findByEstado(EstadoAlojamiento.PENDIENTE_REVISION, pagina))
				.thenReturn(new PageImpl<>(List.of(alojamiento), pagina, 1));
		when(alojamientoRepo.findAll(pagina)).thenReturn(new PageImpl<>(List.of(), pagina, 0));

		PaginaDTO<AlojamientoDTO> cola = servicio.listarPorEstado(EstadoAlojamiento.PENDIENTE_REVISION, pagina);
		PaginaDTO<AlojamientoDTO> todos = servicio.listarPorEstado(null, pagina);

		assertThat(cola.getContenido()).singleElement()
				.satisfies(a -> assertThat(a.getIdAlojamiento()).isEqualTo(ID));
		assertThat(cola.getTotalElementos()).isEqualTo(1);
		assertThat(cola.getTotalPaginas()).isEqualTo(1);
		assertThat(cola.getPagina()).isZero();
		assertThat(cola.getTamanio()).isEqualTo(20);
		assertThat(todos.getContenido()).isEmpty();
	}

	private static Reserva reserva(Long id, Long idHuesped, EstadoReserva estado) {
		Reserva reserva = new Reserva();
		reserva.setIdReserva(id);
		reserva.setIdHuesped(idHuesped);
		reserva.setIdAlojamiento(ID);
		reserva.setEstado(estado);
		return reserva;
	}

	/**
	 * Registro que se guardó en el historial.
	 *
	 * @return historial guardado
	 */
	private HistorialEstadoAlojamiento historial() {
		ArgumentCaptor<HistorialEstadoAlojamiento> historial = ArgumentCaptor
				.forClass(HistorialEstadoAlojamiento.class);
		verify(historialRepo).save(historial.capture());
		return historial.getValue();
	}
}
