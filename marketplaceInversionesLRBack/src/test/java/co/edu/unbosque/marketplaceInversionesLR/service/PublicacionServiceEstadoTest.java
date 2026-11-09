package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ConsultaDisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.HistorialEstadoAlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.HistorialEstadoAlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ImagenRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-17: envío a revisión, pausa, reanudación e historial
 * de estados. La base de datos se simula.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionServiceEstadoTest {

	/** Dueño de la publicación. */
	private static final Long ANFITRION = 4L;

	/** Usuario autenticado que no es el dueño. */
	private static final Long OTRO = 9L;

	/** Publicación de prueba. */
	private static final Long ID = 20L;

	/** Mensaje del rechazo por permisos. */
	private static final String SIN_PERMISO = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.";

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private ImagenRepository imagenRepo;

	@Mock
	private PeriodoDisponibilidadRepository periodoRepo;

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
	private ReservaRepository reservaRepo;

	private PublicacionService servicio;

	/** Publicación que devuelve el repositorio simulado. */
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new PublicacionService(alojamientoRepo, imagenRepo, periodoRepo, politicaRepo, historialRepo,
				usuarioRepo, almacenamientoService, auditoriaService, geocodingService, new ModelMapper());
		alojamiento = completo();
		lenient().when(alojamientoRepo.findById(ID)).thenReturn(Optional.of(alojamiento));
		lenient().when(alojamientoRepo.save(any(Alojamiento.class)))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	@Test
	@DisplayName("CA-157 Con los datos completos, enviar a revisión cambia el estado a PENDIENTE_REVISION")
	void enviaARevision_CA157() {
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(new Imagen()));
		when(periodoRepo.existsByIdAlojamientoAndFechaFinAfter(eq(ID), any())).thenReturn(true);

		Resultado<AlojamientoDTO> resultado = servicio.enviarRevision(ID, ANFITRION);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
		assertHistorial(EstadoAlojamiento.BORRADOR, EstadoAlojamiento.PENDIENTE_REVISION);
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO,
				"BORRADOR -> PENDIENTE_REVISION");
	}

	@Test
	@DisplayName("CA-157 Sin los datos completos no se envía a revisión e indica lo que falta")
	void noEnviaSinDatosCompletos_CA157() {
		alojamiento.setDescripcion(null);

		Resultado<AlojamientoDTO> resultado = servicio.enviarRevision(ID, ANFITRION);

		String mensaje = "Completa los datos antes de enviar a revisión. Falta: descripción, al menos una imagen, "
				+ "fechas de disponibilidad desde hoy.";
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(mensaje);
		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		verify(alojamientoRepo, never()).save(any());
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO, mensaje);
	}

	@Test
	@DisplayName("CA-108 El anfitrión pausa su publicación ACTIVA y queda PAUSADA")
	void pausaLaPublicacion_CA108() {
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);

		Resultado<AlojamientoDTO> resultado = servicio.pausar(ID, ANFITRION);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.PAUSADA);
		assertHistorial(EstadoAlojamiento.ACTIVA, EstadoAlojamiento.PAUSADA);
	}

	@Test
	@DisplayName("El anfitrión reanuda su publicación PAUSADA y queda ACTIVA")
	void reanudaLaPublicacion() {
		alojamiento.setEstado(EstadoAlojamiento.PAUSADA);

		Resultado<AlojamientoDTO> resultado = servicio.reanudar(ID, ANFITRION);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoAlojamiento.ACTIVA);
		assertHistorial(EstadoAlojamiento.PAUSADA, EstadoAlojamiento.ACTIVA);
	}

	@Test
	@DisplayName("CA-104 Los únicos estados de una publicación son BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA y BLOQUEADA")
	void soloCincoEstados_CA104() {
		assertThat(EstadoAlojamiento.values()).containsExactly(EstadoAlojamiento.BORRADOR,
				EstadoAlojamiento.PENDIENTE_REVISION, EstadoAlojamiento.ACTIVA, EstadoAlojamiento.PAUSADA,
				EstadoAlojamiento.BLOQUEADA);
	}

	@Test
	@DisplayName("CA-104 Solo se permiten las transiciones definidas")
	void soloTransicionesDefinidas_CA104() {
		assertTransicionRechazada(EstadoAlojamiento.BORRADOR, servicio::pausar, "No se puede pasar de BORRADOR a PAUSADA.");
		assertTransicionRechazada(EstadoAlojamiento.BORRADOR, servicio::reanudar, "No se puede pasar de BORRADOR a ACTIVA.");
		assertTransicionRechazada(EstadoAlojamiento.BLOQUEADA, servicio::reanudar, "No se puede pasar de BLOQUEADA a ACTIVA.");
		assertTransicionRechazada(EstadoAlojamiento.PAUSADA, servicio::pausar, "No se puede pasar de PAUSADA a PAUSADA.");
		assertTransicionRechazada(EstadoAlojamiento.ACTIVA, servicio::enviarRevision,
				"No se puede pasar de ACTIVA a PENDIENTE_REVISION.");

		verify(alojamientoRepo, never()).save(any());
		verify(historialRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-159 Una publicación PAUSADA o BLOQUEADA no se muestra disponible a un huésped")
	void pausadaOBloqueadaNoDisponible_CA159() {
		CalendarioService calendario = new CalendarioService(alojamientoRepo, periodoRepo, reservaRepo, auditoriaService);
		LocalDate llegada = LocalDate.now().plusDays(5);

		for (EstadoAlojamiento estado : List.of(EstadoAlojamiento.PAUSADA, EstadoAlojamiento.BLOQUEADA)) {
			alojamiento.setEstado(estado);

			Resultado<ConsultaDisponibilidadDTO> huesped = calendario.consultar(ID, llegada, llegada.plusDays(2), OTRO,
					false);
			Resultado<ConsultaDisponibilidadDTO> dueno = calendario.consultar(ID, llegada, llegada.plusDays(2), ANFITRION,
					false);

			assertThat(huesped.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
			assertThat(dueno.getDato().getDisponible()).isFalse();
			assertThat(dueno.getDato().getMotivo())
					.isEqualTo("El alojamiento no está disponible para nuevas reservas (estado " + estado + ").");
		}
	}

	@Test
	@DisplayName("Solo el anfitrión dueño puede cambiar el estado y el intento ajeno queda en la auditoría")
	void soloElDuenoCambiaElEstado() {
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);

		assertSinPermiso(servicio.enviarRevision(ID, OTRO));
		assertSinPermiso(servicio.pausar(ID, OTRO));
		assertSinPermiso(servicio.reanudar(ID, OTRO));
		assertSinPermiso(servicio.pausar(ID, null));

		assertThat(alojamiento.getEstado()).isEqualTo(EstadoAlojamiento.ACTIVA);
		verify(alojamientoRepo, never()).save(any());
		verify(auditoriaService, org.mockito.Mockito.times(3)).registrar(OTRO,
				AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO, AuditoriaService.ENTIDAD_ALOJAMIENTO, ID,
				ResultadoAuditoria.RECHAZADO, "Sin permiso sobre el alojamiento");
	}

	@Test
	@DisplayName("Una publicación inexistente responde NO_ENCONTRADO")
	void alojamientoInexistente() {
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.enviarRevision(99L, ANFITRION).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.pausar(99L, ANFITRION).getMensaje()).isEqualTo("El alojamiento no existe.");
		assertThat(servicio.reanudar(99L, ANFITRION).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.historial(99L, ANFITRION, true).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("El historial lo ven el dueño y el administrador; para los demás no existe")
	void historial() {
		HistorialEstadoAlojamiento cambio = new HistorialEstadoAlojamiento();
		cambio.setIdAlojamiento(ID);
		cambio.setEstadoAnterior(EstadoAlojamiento.BORRADOR);
		cambio.setEstadoNuevo(EstadoAlojamiento.PENDIENTE_REVISION);
		cambio.setIdUsuario(ANFITRION);
		cambio.setFecha(LocalDateTime.of(2026, 11, 8, 10, 0));
		when(historialRepo.findByIdAlojamientoOrderByFechaAscIdHistorialAsc(ID)).thenReturn(List.of(cambio));

		Resultado<List<HistorialEstadoAlojamientoDTO>> resultado = servicio.historial(ID, ANFITRION, false);

		assertThat(resultado.getDato()).singleElement().satisfies(h -> {
			assertThat(h.getIdAlojamiento()).isEqualTo(ID);
			assertThat(h.getEstadoAnterior()).isEqualTo(EstadoAlojamiento.BORRADOR);
			assertThat(h.getEstadoNuevo()).isEqualTo(EstadoAlojamiento.PENDIENTE_REVISION);
			assertThat(h.getIdUsuario()).isEqualTo(ANFITRION);
			assertThat(h.getFecha()).isEqualTo(LocalDateTime.of(2026, 11, 8, 10, 0));
		});
		assertThat(servicio.historial(ID, OTRO, true).esExito()).isTrue();
		assertThat(servicio.historial(ID, OTRO, false).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.historial(ID, null, false).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	/** Operación de cambio de estado del anfitrión. */
	@FunctionalInterface
	private interface Transicion {

		/**
		 * Ejecuta el cambio de estado.
		 *
		 * @param idAlojamiento alojamiento
		 * @param idActor       anfitrión
		 * @return resultado de la operación
		 */
		Resultado<AlojamientoDTO> aplicar(Long idAlojamiento, Long idActor);
	}

	/**
	 * Revisa que el cambio de estado se rechace y deje el estado igual.
	 *
	 * @param estado     estado actual
	 * @param transicion cambio que se intenta
	 * @param mensaje    mensaje esperado
	 */
	private void assertTransicionRechazada(EstadoAlojamiento estado, Transicion transicion, String mensaje) {
		alojamiento.setEstado(estado);

		Resultado<AlojamientoDTO> resultado = transicion.aplicar(ID, ANFITRION);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(mensaje);
		assertThat(alojamiento.getEstado()).isEqualTo(estado);
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO, mensaje);
	}

	/**
	 * Revisa que la operación se haya rechazado por falta de permisos.
	 *
	 * @param resultado resultado de la operación
	 */
	private static void assertSinPermiso(Resultado<?> resultado) {
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(resultado.getMensaje()).isEqualTo(SIN_PERMISO);
	}

	/**
	 * Revisa el registro guardado en el historial.
	 *
	 * @param anterior estado anterior esperado
	 * @param nuevo    estado nuevo esperado
	 */
	private void assertHistorial(EstadoAlojamiento anterior, EstadoAlojamiento nuevo) {
		ArgumentCaptor<HistorialEstadoAlojamiento> historial = ArgumentCaptor
				.forClass(HistorialEstadoAlojamiento.class);
		verify(historialRepo).save(historial.capture());
		assertThat(historial.getValue().getIdAlojamiento()).isEqualTo(ID);
		assertThat(historial.getValue().getEstadoAnterior()).isEqualTo(anterior);
		assertThat(historial.getValue().getEstadoNuevo()).isEqualTo(nuevo);
		assertThat(historial.getValue().getIdUsuario()).isEqualTo(ANFITRION);
	}

	/**
	 * Publicación en BORRADOR con todos los datos mínimos.
	 *
	 * @return publicación
	 */
	private static Alojamiento completo() {
		Alojamiento completo = new Alojamiento();
		completo.setIdAlojamiento(ID);
		completo.setIdAnfitrion(ANFITRION);
		completo.setEstado(EstadoAlojamiento.BORRADOR);
		completo.setTitulo("Casa");
		completo.setDescripcion("Descripción");
		completo.setUbicacion("Calle 1");
		completo.setCiudad("Bogotá");
		completo.setTipo("Casa");
		completo.setReglas("No fumar");
		completo.setCapacidad(2);
		completo.setHabitaciones(1);
		completo.setBanos(1);
		completo.setHoraEntrada(LocalTime.of(15, 0));
		completo.setHoraSalida(LocalTime.of(11, 0));
		completo.setPrecioNoche(BigDecimal.TEN);
		completo.setIdPolitica(1L);
		return completo;
	}
}
