package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import co.edu.unbosque.marketplaceInversionesLR.dto.ConsultaDisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PeriodoDisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.PeriodoDisponibilidad;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-20 Registrar períodos de disponibilidad y HU-21
 * Consultar disponibilidad para fechas seleccionadas, con los
 * repositorios y la auditoría simulados.
 */
@ExtendWith(MockitoExtension.class)
class CalendarioServiceTest {

	/** Alojamiento de prueba. */
	private static final Long ID_ALOJAMIENTO = 10L;

	/** Anfitrión dueño del alojamiento. */
	private static final Long ID_ANFITRION = 1L;

	/** Otro anfitrión, sin permisos sobre el alojamiento. */
	private static final Long OTRO_ANFITRION = 2L;

	/** Período existente. */
	private static final Long ID_PERIODO = 5L;

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private PeriodoDisponibilidadRepository periodoRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private AuditoriaService auditoriaService;

	private CalendarioService servicio;

	@BeforeEach
	void preparar() {
		servicio = new CalendarioService(alojamientoRepo, periodoRepo, reservaRepo, auditoriaService);
	}

	@Test
	@DisplayName("CA-115 El anfitrión registra un período válido y queda guardado y auditado")
	void registraPeriodo_CA115() {
		alojamiento(EstadoAlojamiento.BORRADOR);
		when(periodoRepo.existeCruce(ID_ALOJAMIENTO, BASE, BASE.plusDays(10))).thenReturn(false);
		when(periodoRepo.save(any())).thenAnswer(invocacion -> {
			PeriodoDisponibilidad p = invocacion.getArgument(0);
			p.setIdPeriodo(ID_PERIODO);
			return p;
		});

		Resultado<PeriodoDisponibilidadDTO> resultado = servicio.agregarPeriodo(ID_ALOJAMIENTO,
				datos(BASE, BASE.plusDays(10)), ID_ANFITRION, false);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getIdPeriodo()).isEqualTo(ID_PERIODO);
		assertThat(resultado.getDato().getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(resultado.getDato().getFechaInicio()).isEqualTo(BASE);
		assertThat(resultado.getDato().getFechaFin()).isEqualTo(BASE.plusDays(10));
		ArgumentCaptor<PeriodoDisponibilidad> guardado = ArgumentCaptor.forClass(PeriodoDisponibilidad.class);
		verify(periodoRepo).save(guardado.capture());
		assertThat(guardado.getValue().getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		verify(auditoriaService).registrar(eq(ID_ANFITRION), eq(AuditoriaService.DISPONIBILIDAD),
				eq(AuditoriaService.ENTIDAD_PERIODO), eq(ID_PERIODO), eq(ResultadoAuditoria.EXITOSO), anyString());
	}

	@Test
	@DisplayName("CA-115 Un administrador también puede registrar períodos de cualquier alojamiento")
	void administradorRegistraPeriodo_CA115() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(periodoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		assertThat(servicio.agregarPeriodo(ID_ALOJAMIENTO, datos(BASE, BASE.plusDays(3)), 99L, true).esExito())
				.isTrue();
	}

	@Test
	@DisplayName("No registra un período que se cruza con otro del mismo alojamiento")
	void rechazaCruceAlCrear() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(periodoRepo.existeCruce(ID_ALOJAMIENTO, BASE, BASE.plusDays(5))).thenReturn(true);

		Resultado<PeriodoDisponibilidadDTO> resultado = servicio.agregarPeriodo(ID_ALOJAMIENTO,
				datos(BASE, BASE.plusDays(5)), ID_ANFITRION, false);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(CalendarioService.CRUCE_AL_CREAR);
		verify(periodoRepo, never()).save(any());
	}

	@Test
	@DisplayName("No registra un período con fechas faltantes, invertidas o que ya terminó")
	void rechazaFechasInvalidas() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		LocalDate hoy = LocalDate.now();

		assertThat(servicio.agregarPeriodo(ID_ALOJAMIENTO, null, ID_ANFITRION, false).getMensaje())
				.isEqualTo("Debes indicar la fecha de inicio y la de fin del período.");
		assertThat(servicio.agregarPeriodo(ID_ALOJAMIENTO, datos(BASE, null), ID_ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.agregarPeriodo(ID_ALOJAMIENTO, datos(BASE, BASE), ID_ANFITRION, false).getMensaje())
				.isEqualTo("Las fechas son inválidas: el fin del período debe ser posterior al inicio.");
		assertThat(servicio.agregarPeriodo(ID_ALOJAMIENTO, datos(hoy.minusDays(9), hoy.minusDays(2)), ID_ANFITRION,
				false).getMensaje()).isEqualTo("El período no puede terminar en el pasado.");
		verify(periodoRepo, never()).save(any());
	}

	@Test
	@DisplayName("Solo el dueño o un administrador modifica la disponibilidad; el alojamiento debe existir")
	void permisosYAlojamientoInexistente() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		Resultado<PeriodoDisponibilidadDTO> ajeno = servicio.agregarPeriodo(ID_ALOJAMIENTO,
				datos(BASE, BASE.plusDays(2)), OTRO_ANFITRION, false);
		assertThat(ajeno.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(ajeno.getMensaje()).isEqualTo(CalendarioService.SIN_PERMISO);
		verify(auditoriaService).registrar(eq(OTRO_ANFITRION), eq(AuditoriaService.DISPONIBILIDAD),
				eq(AuditoriaService.ENTIDAD_PERIODO), isNull(), eq(ResultadoAuditoria.RECHAZADO), anyString());

		assertThat(servicio.agregarPeriodo(99L, datos(BASE, BASE.plusDays(2)), ID_ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.eliminarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, OTRO_ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.SIN_PERMISO);
	}

	@Test
	@DisplayName("CA-117 El anfitrión modifica un período no reservado y se guardan las fechas nuevas")
	void modificaPeriodoNoReservado_CA117() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		periodo(BASE, BASE.plusDays(10));
		when(reservaRepo.buscarOcupadas(eq(ID_ALOJAMIENTO), eq(BASE), eq(CalendarioService.ESTADOS_OCUPAN), any()))
				.thenReturn(List.of());
		when(periodoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Resultado<PeriodoDisponibilidadDTO> resultado = servicio.actualizarPeriodo(ID_ALOJAMIENTO, ID_PERIODO,
				datos(BASE.plusDays(2), BASE.plusDays(20)), ID_ANFITRION, false);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getFechaInicio()).isEqualTo(BASE.plusDays(2));
		assertThat(resultado.getDato().getFechaFin()).isEqualTo(BASE.plusDays(20));
		verify(auditoriaService).registrar(eq(ID_ANFITRION), eq(AuditoriaService.DISPONIBILIDAD),
				eq(AuditoriaService.ENTIDAD_PERIODO), eq(ID_PERIODO), eq(ResultadoAuditoria.EXITOSO), anyString());
	}

	@Test
	@DisplayName("CA-118 No se modifica un período si deja por fuera una reserva; el cambio que la conserva sí")
	void rechazaCambioQueAfectaReserva_CA118() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		periodo(BASE, BASE.plusDays(10));
		when(reservaRepo.buscarOcupadas(eq(ID_ALOJAMIENTO), eq(BASE), eq(CalendarioService.ESTADOS_OCUPAN), any()))
				.thenReturn(List.of(reserva(BASE.plusDays(3), BASE.plusDays(6))));
		when(periodoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		// Acortar el final deja la reserva por fuera
		Resultado<PeriodoDisponibilidadDTO> acortado = servicio.actualizarPeriodo(ID_ALOJAMIENTO, ID_PERIODO,
				datos(BASE, BASE.plusDays(4)), ID_ANFITRION, false);
		assertThat(acortado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(acortado.getMensaje()).isEqualTo("No puedes modificar el período: afecta la reserva del "
				+ BASE.plusDays(3) + " al " + BASE.plusDays(6) + ".");
		// Mover el inicio después de la llegada también la deja por fuera
		assertThat(servicio.actualizarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, datos(BASE.plusDays(4), BASE.plusDays(10)),
				ID_ANFITRION, false).getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		verify(periodoRepo, never()).save(any());
		verify(auditoriaService, times(2)).registrar(eq(ID_ANFITRION),
				eq(AuditoriaService.DISPONIBILIDAD), eq(AuditoriaService.ENTIDAD_PERIODO), eq(ID_PERIODO),
				eq(ResultadoAuditoria.RECHAZADO), anyString());

		// Un cambio que deja la reserva dentro sí se acepta
		assertThat(servicio.actualizarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, datos(BASE.plusDays(1), BASE.plusDays(8)),
				ID_ANFITRION, false).esExito()).isTrue();
	}

	@Test
	@DisplayName("No modifica un período inexistente, de otro alojamiento, con fechas inválidas o que se cruza")
	void rechazosAlModificar() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		PeriodoDisponibilidad deOtro = new PeriodoDisponibilidad();
		deOtro.setIdAlojamiento(77L);
		when(periodoRepo.findById(8L)).thenReturn(Optional.of(deOtro));
		when(periodoRepo.findById(9L)).thenReturn(Optional.empty());
		periodo(BASE, BASE.plusDays(10));
		when(periodoRepo.existeCruceConOtro(ID_ALOJAMIENTO, BASE, BASE.plusDays(30), ID_PERIODO)).thenReturn(true);

		assertThat(servicio.actualizarPeriodo(ID_ALOJAMIENTO, 8L, datos(BASE, BASE.plusDays(1)), ID_ANFITRION, false)
				.getMensaje()).isEqualTo(CalendarioService.PERIODO_NO_EXISTE);
		assertThat(servicio.actualizarPeriodo(ID_ALOJAMIENTO, 9L, datos(BASE, BASE.plusDays(1)), ID_ANFITRION, false)
				.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.actualizarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, datos(BASE.plusDays(3), BASE), ID_ANFITRION,
				false).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.actualizarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, datos(BASE, BASE.plusDays(30)),
				ID_ANFITRION, false).getMensaje()).isEqualTo(CalendarioService.CRUCE_AL_MODIFICAR);
		verify(periodoRepo, never()).save(any());
	}

	@Test
	@DisplayName("Elimina un período sin reservas y rechaza eliminar uno con reservas")
	void eliminarPeriodo() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		PeriodoDisponibilidad periodo = periodo(BASE, BASE.plusDays(10));
		when(reservaRepo.buscarOcupadas(eq(ID_ALOJAMIENTO), eq(BASE), eq(CalendarioService.ESTADOS_OCUPAN), any()))
				.thenReturn(List.of(reserva(BASE.plusDays(10), BASE.plusDays(12))), List.of());

		Resultado<Void> conReserva = servicio.eliminarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, ID_ANFITRION, false);
		assertThat(conReserva.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		verify(periodoRepo, never()).delete(any());

		assertThat(servicio.eliminarPeriodo(ID_ALOJAMIENTO, ID_PERIODO, ID_ANFITRION, false).esExito()).isTrue();
		verify(periodoRepo).delete(periodo);
	}

	@Test
	@DisplayName("El calendario muestra períodos y rangos reservados; si no está ACTIVA solo lo ve el dueño o un ADMIN")
	void calendario() {
		alojamiento(EstadoAlojamiento.BORRADOR);
		PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
		periodo.setIdPeriodo(ID_PERIODO);
		periodo.setIdAlojamiento(ID_ALOJAMIENTO);
		periodo.setFechaInicio(BASE);
		periodo.setFechaFin(BASE.plusDays(10));
		when(periodoRepo.findByIdAlojamientoOrderByFechaInicio(ID_ALOJAMIENTO)).thenReturn(List.of(periodo));
		when(reservaRepo.buscarOcupadas(eq(ID_ALOJAMIENTO), any(), eq(CalendarioService.ESTADOS_OCUPAN), any()))
				.thenReturn(List.of(reserva(BASE.plusDays(2), BASE.plusDays(4))));

		Resultado<DisponibilidadDTO> delDueno = servicio.calendario(ID_ALOJAMIENTO, ID_ANFITRION, false);
		assertThat(delDueno.esExito()).isTrue();
		assertThat(delDueno.getDato().getEstadoAlojamiento()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(delDueno.getDato().getPeriodos()).singleElement()
				.satisfies(p -> assertThat(p.getFechaFin()).isEqualTo(BASE.plusDays(10)));
		assertThat(delDueno.getDato().getReservados()).singleElement()
				.satisfies(r -> assertThat(r.getFechaEntrada()).isEqualTo(BASE.plusDays(2)));

		assertThat(servicio.calendario(ID_ALOJAMIENTO, 99L, true).esExito()).isTrue();
		assertThat(servicio.calendario(ID_ALOJAMIENTO, null, false).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.calendario(ID_ALOJAMIENTO, OTRO_ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("Indica si el alojamiento tiene períodos que terminen después de hoy")
	void disponibilidadFutura() {
		when(periodoRepo.existsByIdAlojamientoAndFechaFinAfter(ID_ALOJAMIENTO, LocalDate.now())).thenReturn(true);

		assertThat(servicio.tieneDisponibilidadFutura(ID_ALOJAMIENTO)).isTrue();
		assertThat(CalendarioService.limitePendiente()).isBefore(LocalDateTime.now());
	}

	@Test
	@DisplayName("CA-082 Se rechaza la consulta si falta una fecha, si la salida no es posterior a la llegada o si la llegada ya pasó")
	void consultaConFechasInvalidas_CA082() {
		LocalDate hoy = LocalDate.now();

		assertThat(servicio.consultar(ID_ALOJAMIENTO, null, BASE, null, false).getMensaje())
				.isEqualTo(CalendarioService.FECHAS_FALTANTES);
		assertThat(servicio.consultar(ID_ALOJAMIENTO, BASE, null, null, false).getCodigo())
				.isEqualTo(Resultado.DATOS_INVALIDOS);
		// Salida igual a la llegada y salida anterior a la llegada
		assertThat(servicio.consultar(ID_ALOJAMIENTO, BASE, BASE, null, false).getMensaje())
				.isEqualTo(CalendarioService.SALIDA_NO_POSTERIOR);
		assertThat(servicio.consultar(ID_ALOJAMIENTO, BASE, BASE.minusDays(1), null, false).getMensaje())
				.isEqualTo(CalendarioService.SALIDA_NO_POSTERIOR);
		assertThat(servicio.consultar(ID_ALOJAMIENTO, hoy.minusDays(1), hoy.plusDays(2), null, false).getMensaje())
				.isEqualTo(CalendarioService.LLEGADA_EN_EL_PASADO);
		// Hoy sí se puede llegar
		assertThat(CalendarioService.validarFechas(hoy, hoy.plusDays(1))).isNull();
		verify(alojamientoRepo, never()).findById(any());
	}

	@Test
	@DisplayName("CA-119 Una publicación ACTIVA con período que contiene las fechas y sin reservas está disponible")
	void disponibleDentroDePeriodoSinReservas_CA119() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(periodoRepo.contiene(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(reservaRepo.existeCruce(eq(ID_ALOJAMIENTO), eq(BASE), eq(BASE.plusDays(3)),
				eq(CalendarioService.ESTADOS_OCUPAN), any())).thenReturn(false);

		Resultado<ConsultaDisponibilidadDTO> resultado = servicio.consultar(ID_ALOJAMIENTO, BASE, BASE.plusDays(3),
				null, false);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getDisponible()).isTrue();
		assertThat(resultado.getDato().getMotivo()).isNull();
		assertThat(resultado.getDato().getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(resultado.getDato().getFechaEntrada()).isEqualTo(BASE);
		assertThat(resultado.getDato().getFechaSalida()).isEqualTo(BASE.plusDays(3));
		assertThat(servicio.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).isTrue();
	}

	@Test
	@DisplayName("CA-120 No está disponible si las fechas se cruzan con una reserva o están fuera de los períodos")
	void noDisponibleConReservaOFueraDePeriodo_CA120() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(periodoRepo.contiene(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(reservaRepo.existeCruce(eq(ID_ALOJAMIENTO), eq(BASE), eq(BASE.plusDays(3)),
				eq(CalendarioService.ESTADOS_OCUPAN), any())).thenReturn(true);
		when(periodoRepo.contiene(ID_ALOJAMIENTO, BASE.plusDays(40), BASE.plusDays(42))).thenReturn(false);

		ConsultaDisponibilidadDTO reservada = servicio.consultar(ID_ALOJAMIENTO, BASE, BASE.plusDays(3), null, false)
				.getDato();
		assertThat(reservada.getDisponible()).isFalse();
		assertThat(reservada.getMotivo()).isEqualTo(CalendarioService.CRUCE_CON_RESERVA);
		assertThat(servicio.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).isFalse();

		ConsultaDisponibilidadDTO fuera = servicio.consultar(ID_ALOJAMIENTO, BASE.plusDays(40), BASE.plusDays(42),
				null, false).getDato();
		assertThat(fuera.getDisponible()).isFalse();
		assertThat(fuera.getMotivo()).isEqualTo(CalendarioService.FUERA_DE_PERIODO);
	}

	@Test
	@DisplayName("CA-120 Una publicación que no está ACTIVA no está disponible para su dueño y no existe para los demás")
	void publicacionNoActiva_CA120() {
		alojamiento(EstadoAlojamiento.PAUSADA);
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		ConsultaDisponibilidadDTO delDueno = servicio.consultar(ID_ALOJAMIENTO, BASE, BASE.plusDays(3), ID_ANFITRION,
				false).getDato();
		assertThat(delDueno.getDisponible()).isFalse();
		assertThat(delDueno.getMotivo()).contains("PAUSADA");
		assertThat(servicio.consultar(ID_ALOJAMIENTO, BASE, BASE.plusDays(3), 99L, true).getDato().getDisponible())
				.isFalse();

		assertThat(servicio.consultar(ID_ALOJAMIENTO, BASE, BASE.plusDays(3), OTRO_ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.consultar(99L, BASE, BASE.plusDays(3), null, false).getMensaje())
				.isEqualTo(CalendarioService.NO_EXISTE);
		verify(periodoRepo, never()).contiene(any(), any(), any());
	}

	/**
	 * Simula el alojamiento de prueba con el estado indicado.
	 *
	 * @param estado estado de la publicación
	 */
	private void alojamiento(EstadoAlojamiento estado) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID_ALOJAMIENTO);
		alojamiento.setIdAnfitrion(ID_ANFITRION);
		alojamiento.setEstado(estado);
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
	}

	/**
	 * Simula el período de prueba del alojamiento.
	 *
	 * @param inicio primer día
	 * @param fin    último día
	 * @return el período simulado
	 */
	private PeriodoDisponibilidad periodo(LocalDate inicio, LocalDate fin) {
		PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
		periodo.setIdPeriodo(ID_PERIODO);
		periodo.setIdAlojamiento(ID_ALOJAMIENTO);
		periodo.setFechaInicio(inicio);
		periodo.setFechaFin(fin);
		when(periodoRepo.findById(ID_PERIODO)).thenReturn(Optional.of(periodo));
		return periodo;
	}

	/**
	 * Crea una reserva confirmada del alojamiento.
	 *
	 * @param entrada día de llegada
	 * @param salida  día de salida
	 * @return reserva
	 */
	private static Reserva reserva(LocalDate entrada, LocalDate salida) {
		Reserva reserva = new Reserva();
		reserva.setIdReserva(3L);
		reserva.setIdAlojamiento(ID_ALOJAMIENTO);
		reserva.setFechaEntrada(entrada);
		reserva.setFechaSalida(salida);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		return reserva;
	}

	/**
	 * Fechas de un período para la API.
	 *
	 * @param inicio primer día
	 * @param fin    último día
	 * @return datos del período
	 */
	private static PeriodoDisponibilidadDTO datos(LocalDate inicio, LocalDate fin) {
		PeriodoDisponibilidadDTO datos = new PeriodoDisponibilidadDTO();
		datos.setFechaInicio(inicio);
		datos.setFechaFin(fin);
		return datos;
	}
}
