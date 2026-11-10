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
import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.HistorialEstadoAlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ImagenRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
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
				historialRepo, usuarioRepo, almacenamientoService, auditoriaService, geocodingService,
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
