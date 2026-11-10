package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import co.edu.unbosque.marketplaceInversionesLR.dto.CancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SimulacionCancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-01 Cancelar reserva aplicando la política de
 * cancelación. La base de datos, el pago y los avisos se simulan.
 */
@ExtendWith(MockitoExtension.class)
class CancelacionServiceTest {

	/** Reserva de prueba. */
	private static final Long ID_RESERVA = 15L;

	/** Huésped dueño de la reserva. */
	private static final Long HUESPED = 3L;

	/** Anfitrión del alojamiento. */
	private static final Long ANFITRION = 1L;

	/** Alojamiento de la reserva. */
	private static final Long ALOJAMIENTO = 10L;

	/** Política MODERADA del alojamiento. */
	private static final Long ID_MODERADA = 2L;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private PoliticaCancelacionRepository politicaRepo;

	@Mock
	private CancelacionRepository cancelacionRepo;

	@Mock
	private PagoService pagoService;

	@Mock
	private NotificacionService notificacionService;

	@Mock
	private AuditoriaService auditoriaService;

	private CancelacionService servicio;

	/** Reserva CONFIRMADA de 690.000 con llegada en 10 días. */
	private Reserva reserva;

	/** Alojamiento con la política MODERADA. */
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new CancelacionService(reservaRepo, alojamientoRepo, politicaRepo, cancelacionRepo, pagoService,
				notificacionService, auditoriaService, new ModelMapper());
		reserva = new Reserva();
		reserva.setIdReserva(ID_RESERVA);
		reserva.setIdHuesped(HUESPED);
		reserva.setIdAlojamiento(ALOJAMIENTO);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setFechaEntrada(LocalDate.now().plusDays(10));
		reserva.setFechaSalida(LocalDate.now().plusDays(13));
		reserva.setTotal(new BigDecimal("690000.00"));
		alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ALOJAMIENTO);
		alojamiento.setIdAnfitrion(ANFITRION);
		alojamiento.setIdPolitica(ID_MODERADA);
	}

	@ParameterizedTest(name = "{0} con {1} días de anticipación devuelve el {2} %")
	@DisplayName("CA-001 El porcentaje sale de la política y de los días de anticipación")
	@CsvSource({ "FLEXIBLE, 30, 100", "FLEXIBLE, 1, 100", "FLEXIBLE, 0, 0", "MODERADA, 5, 100", "MODERADA, 4, 50",
			"MODERADA, 1, 50", "MODERADA, 0, 0", "ESTRICTA, 30, 50", "ESTRICTA, 7, 50", "ESTRICTA, 6, 0" })
	void porcentajeSegunPolitica_CA001(String nombre, long dias, int esperado) {
		assertThat(CancelacionService.porcentajeReembolso(politica(nombre), dias)).isEqualTo(esperado);
	}

	@Test
	@DisplayName("CA-001 La devolución es el total por el porcentaje, redondeada a 2 decimales como en Neon")
	void devolucionRedondeada_CA001() {
		assertThat(CancelacionService.devolucion(new BigDecimal("690000.00"), 50)).isEqualByComparingTo("345000.00");
		assertThat(CancelacionService.devolucion(new BigDecimal("333.33"), 50)).isEqualByComparingTo("166.67");
		assertThat(CancelacionService.devolucion(new BigDecimal("690000.00"), 0)).isEqualByComparingTo("0.00");
		assertThat(CancelacionService.devolucion(new BigDecimal("690000.00"), 100).scale()).isEqualTo(2);
	}

	@Test
	@DisplayName("CA-001 CA-004 CA-007 Cancelar aplica la política, deja la reserva CANCELADA, registra todo y devuelve")
	void cancela_CA001_CA004_CA007() {
		listoParaCancelar();

		Resultado<CancelacionDTO> resultado = servicio.cancelar(ID_RESERVA, "  Cambio de planes  ", HUESPED);

		assertThat(resultado.esExito()).isTrue();
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		ArgumentCaptor<Cancelacion> guardada = ArgumentCaptor.forClass(Cancelacion.class);
		verify(cancelacionRepo).save(guardada.capture());
		Cancelacion cancelacion = guardada.getValue();
		assertThat(cancelacion.getIdReserva()).isEqualTo(ID_RESERVA);
		assertThat(cancelacion.getIdUsuario()).isEqualTo(HUESPED);
		assertThat(cancelacion.getMotivo()).isEqualTo("Cambio de planes");
		assertThat(cancelacion.getFecha()).isNotNull();
		assertThat(cancelacion.getPolitica()).isEqualTo("MODERADA");
		assertThat(cancelacion.getPorcentajeReembolso()).isEqualTo(100);
		assertThat(cancelacion.getValorReembolso()).isEqualByComparingTo("690000.00");
		assertThat(cancelacion.getEstado()).isEqualTo(EstadoCancelacion.COMPLETADA);
		assertThat(cancelacion.getEstadoFinalReserva()).isEqualTo(EstadoReserva.CANCELADA);
		CancelacionDTO dto = resultado.getDato();
		assertThat(dto.getIdCancelacion()).isEqualTo(4L);
		assertThat(dto.getValorReembolso()).isEqualByComparingTo("690000.00");
		assertThat(dto.getEstadoFinalReserva()).isEqualTo(EstadoReserva.CANCELADA);

		verify(reservaRepo).save(reserva);
		verify(pagoService).reembolsar(ID_RESERVA, new BigDecimal("690000.00"));
		verify(notificacionService).enviar(eq(ANFITRION), eq(NotificacionService.RESERVA_CANCELADA),
				contains("fue cancelada por el huésped"));
		verify(notificacionService).enviar(eq(HUESPED), eq(NotificacionService.RESERVA_CANCELADA),
				contains("MODERADA, 100 %"));
		verify(auditoriaService).registrar(eq(HUESPED), eq(AuditoriaService.CANCELACION),
				eq(AuditoriaService.ENTIDAD_RESERVA), eq(ID_RESERVA), eq(ResultadoAuditoria.EXITOSO), anyString());
	}

	@Test
	@DisplayName("CA-009 Si la política no da devolución, se cancela sin devolver nada")
	void sinDevolucion_CA009() {
		reserva.setFechaEntrada(LocalDate.now().plusDays(3));
		alojamiento.setIdPolitica(3L);
		when(reservaRepo.buscarParaCancelar(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
		when(politicaRepo.findById(3L)).thenReturn(Optional.of(politica("ESTRICTA")));
		when(cancelacionRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Resultado<CancelacionDTO> resultado = servicio.cancelar(ID_RESERVA, "Viaje cancelado", HUESPED);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getPolitica()).isEqualTo("ESTRICTA");
		assertThat(resultado.getDato().getPorcentajeReembolso()).isZero();
		assertThat(resultado.getDato().getValorReembolso()).isEqualByComparingTo("0.00");
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		verify(pagoService).reembolsar(ID_RESERVA, new BigDecimal("0.00"));
	}

	@Test
	@DisplayName("CA-009 Una reserva EN_REVISION también se cancela con su política")
	void enRevision_CA009() {
		reserva.setEstado(EstadoReserva.EN_REVISION);
		listoParaCancelar();

		assertThat(servicio.cancelar(ID_RESERVA, "Bloquearon el alojamiento", HUESPED).esExito()).isTrue();
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
	}

	@Test
	@DisplayName("Sin política asignada se usa la FLEXIBLE")
	void politicaPorDefecto() {
		alojamiento.setIdPolitica(null);
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
		when(politicaRepo.findByNombre(CancelacionService.POLITICA_POR_DEFECTO))
				.thenReturn(Optional.of(politica("FLEXIBLE")));

		assertThat(servicio.simular(ID_RESERVA, HUESPED).getDato().getPolitica()).isEqualTo("FLEXIBLE");
	}

	@Test
	@DisplayName("El motivo es obligatorio y de máximo 500 caracteres")
	void motivoInvalido() {
		for (String motivo : new String[] { null, "", "   ", "x".repeat(501) }) {
			Resultado<CancelacionDTO> resultado = servicio.cancelar(ID_RESERVA, motivo, HUESPED);
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
			assertThat(resultado.getMensaje()).isEqualTo(CancelacionService.MOTIVO_INVALIDO);
		}
		verify(reservaRepo, never()).buscarParaCancelar(any());
	}

	@Test
	@DisplayName("La reserva de otro usuario o que no existe responde no encontrado")
	void noEsSuya() {
		when(reservaRepo.buscarParaCancelar(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(reservaRepo.buscarParaCancelar(99L)).thenReturn(Optional.empty());

		assertThat(servicio.cancelar(ID_RESERVA, "Motivo", 77L).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.cancelar(99L, "Motivo", HUESPED).getMensaje()).isEqualTo(CancelacionService.NO_EXISTE);
		assertThat(servicio.cancelar(null, "Motivo", HUESPED).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		verify(cancelacionRepo, never()).save(any());
	}

	@Test
	@DisplayName("Una reserva ya cancelada, pendiente de pago o con la llegada hoy no se cancela, y queda auditado")
	void noCancelable() {
		when(reservaRepo.buscarParaCancelar(ID_RESERVA)).thenReturn(Optional.of(reserva));

		reserva.setEstado(EstadoReserva.CANCELADA);
		Resultado<CancelacionDTO> cancelada = servicio.cancelar(ID_RESERVA, "Motivo", HUESPED);
		reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
		Resultado<CancelacionDTO> pendiente = servicio.cancelar(ID_RESERVA, "Motivo", HUESPED);
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setFechaEntrada(LocalDate.now());
		Resultado<CancelacionDTO> hoy = servicio.cancelar(ID_RESERVA, "Motivo", HUESPED);

		assertThat(cancelada.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(cancelada.getMensaje()).isEqualTo(CancelacionService.ESTADO_NO_CANCELABLE + "CANCELADA.");
		assertThat(pendiente.getMensaje()).contains("PENDIENTE_PAGO");
		assertThat(hoy.getMensaje()).isEqualTo(CancelacionService.SOLO_FUTURAS);
		verify(cancelacionRepo, never()).save(any());
		verify(pagoService, never()).reembolsar(any(), any());
		verify(auditoriaService).registrar(HUESPED, AuditoriaService.CANCELACION, AuditoriaService.ENTIDAD_RESERVA,
				ID_RESERVA, ResultadoAuditoria.RECHAZADO, CancelacionService.SOLO_FUTURAS);
	}

	@Test
	@DisplayName("CA-001 Simular muestra la política, los días, el porcentaje y el valor sin cancelar nada")
	void simula_CA001() {
		reserva.setFechaEntrada(LocalDate.now().plusDays(3));
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
		when(politicaRepo.findById(ID_MODERADA)).thenReturn(Optional.of(politica("MODERADA")));

		Resultado<SimulacionCancelacionDTO> resultado = servicio.simular(ID_RESERVA, HUESPED);

		SimulacionCancelacionDTO simulacion = resultado.getDato();
		assertThat(simulacion.getIdReserva()).isEqualTo(ID_RESERVA);
		assertThat(simulacion.getPolitica()).isEqualTo("MODERADA");
		assertThat(simulacion.getDiasAnticipacion()).isEqualTo(3L);
		assertThat(simulacion.getPorcentajeReembolso()).isEqualTo(50);
		assertThat(simulacion.getValorReembolso()).isEqualByComparingTo("345000.00");
		assertThat(simulacion.getTotalPagado()).isEqualByComparingTo("690000.00");
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		verify(reservaRepo, never()).save(any());
		verify(pagoService, never()).reembolsar(any(), any());
	}

	@Test
	@DisplayName("Simular una reserva ajena responde no encontrado y una cancelada, estado inválido")
	void simularInvalido() {
		when(reservaRepo.findById(ID_RESERVA)).thenReturn(Optional.of(reserva));

		assertThat(servicio.simular(ID_RESERVA, 77L).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		reserva.setEstado(EstadoReserva.CANCELADA);
		assertThat(servicio.simular(ID_RESERVA, HUESPED).getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(servicio.simular(null, HUESPED).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	/** La reserva, el alojamiento y la política MODERADA están listos; la cancelación se guarda con id 4. */
	private void listoParaCancelar() {
		when(reservaRepo.buscarParaCancelar(ID_RESERVA)).thenReturn(Optional.of(reserva));
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
		when(politicaRepo.findById(ID_MODERADA)).thenReturn(Optional.of(politica("MODERADA")));
		when(cancelacionRepo.save(any())).thenAnswer(invocacion -> {
			Cancelacion c = invocacion.getArgument(0);
			c.setIdCancelacion(4L);
			return c;
		});
	}

	/**
	 * Política con las reglas de Neon.
	 *
	 * @param nombre FLEXIBLE, MODERADA o ESTRICTA
	 * @return política
	 */
	private static PoliticaCancelacion politica(String nombre) {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre(nombre);
		switch (nombre) {
		case "FLEXIBLE" -> politica.setDiasReembolsoTotal(1);
		case "MODERADA" -> {
			politica.setDiasReembolsoTotal(5);
			politica.setDiasReembolsoParcial(1);
			politica.setPorcentajeParcial(50);
		}
		default -> {
			politica.setDiasReembolsoParcial(7);
			politica.setPorcentajeParcial(50);
		}
		}
		return politica;
	}
}
