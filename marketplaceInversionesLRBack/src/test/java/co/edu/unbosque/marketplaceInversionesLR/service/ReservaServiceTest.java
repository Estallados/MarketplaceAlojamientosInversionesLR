package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import org.springframework.transaction.PlatformTransactionManager;

import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SolicitudReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas unitarias de HU-22 Crear reserva con bloqueo automático de fechas,
 * con los repositorios, el calendario y la auditoría simulados. El pago usa
 * el simulador real de {@link PagoService}.
 */
@ExtendWith(MockitoExtension.class)
class ReservaServiceTest {

	/** Cotización de prueba. */
	private static final Long ID_COTIZACION = 42L;

	/** Alojamiento de prueba. */
	private static final Long ID_ALOJAMIENTO = 10L;

	/** Anfitrión dueño del alojamiento. */
	private static final Long ID_ANFITRION = 1L;

	/** Huésped que reserva. */
	private static final Long ID_HUESPED = 3L;

	/** Reserva que se guarda. */
	private static final Long ID_RESERVA = 15L;

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

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

	/** Transacciones simuladas: la plantilla ejecuta el paso sin base de datos. */
	@Mock
	private PlatformTransactionManager transactionManager;

	private ReservaService servicio;

	/** Cotización vigente del huésped. */
	private Cotizacion cotizacion;

	/** Alojamiento ACTIVA de otro anfitrión. */
	private Alojamiento alojamiento;

	/** Estado de la reserva en cada saveAndFlush, en orden. */
	private final List<EstadoReserva> estadosGuardados = new ArrayList<>();

	@BeforeEach
	void preparar() {
		ModelMapper modelMapper = new ModelMapper();
		CotizacionService cotizacionService = new CotizacionService(null, null, null, null, modelMapper);
		servicio = new ReservaService(alojamientoRepo, cotizacionRepo, reservaRepo, calendarioService,
				cotizacionService, new PagoService(pagoRepo), notificacionService, auditoriaService, modelMapper,
				JsonMapper.builder().build(), transactionManager);

		cotizacion = new Cotizacion();
		cotizacion.setIdCotizacion(ID_COTIZACION);
		cotizacion.setIdUsuario(ID_HUESPED);
		cotizacion.setIdAlojamiento(ID_ALOJAMIENTO);
		cotizacion.setFechaEntrada(BASE);
		cotizacion.setFechaSalida(BASE.plusDays(3));
		cotizacion.setNoches(3);
		cotizacion.setHuespedes(2);
		cotizacion.setTotal(new BigDecimal("690000.00"));
		cotizacion.setFechaCreacion(LocalDateTime.now());
		cotizacion.setFechaExpiracion(LocalDateTime.now().plusMinutes(30));

		alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID_ALOJAMIENTO);
		alojamiento.setIdAnfitrion(ID_ANFITRION);
		alojamiento.setTitulo("Casa en Villa de Leyva");
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
	}

	@Test
	@DisplayName("CA-070 CA-075 CA-086 CA-160 CA-161 La reserva nace en PENDIENTE_PAGO, se paga y queda CONFIRMADA "
			+ "con el monto exacto de la cotización")
	void reservaConfirmada_CA070_CA075_CA086() {
		listoParaPagar();

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.esExito()).isTrue();
		ReservaDTO reserva = resultado.getDato();
		assertThat(reserva.getIdReserva()).isEqualTo(ID_RESERVA);
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(reserva.getTotal()).isEqualByComparingTo(cotizacion.getTotal());
		assertThat(reserva.getIdHuesped()).isEqualTo(ID_HUESPED);
		assertThat(reserva.getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(reserva.getIdCotizacion()).isEqualTo(ID_COTIZACION);
		assertThat(reserva.getFechaEntrada()).isEqualTo(BASE);
		assertThat(reserva.getFechaSalida()).isEqualTo(BASE.plusDays(3));
		assertThat(reserva.getNoches()).isEqualTo(3);
		assertThat(reserva.getHuespedes()).isEqualTo(2);
		assertThat(reserva.getTituloAlojamiento()).isEqualTo("Casa en Villa de Leyva");
		assertThat(reserva.getDesglose()).contains("\"total\":690000.00").contains("\"idCotizacion\":42");

		// Flujo que exige Neon: PENDIENTE_PAGO, pago APROBADO y luego CONFIRMADA
		assertThat(estadosGuardados).containsExactly(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA);
		ArgumentCaptor<Pago> pago = ArgumentCaptor.forClass(Pago.class);
		verify(pagoRepo).save(pago.capture());
		assertThat(pago.getValue().getIdReserva()).isEqualTo(ID_RESERVA);
		assertThat(pago.getValue().getValor()).isEqualByComparingTo(cotizacion.getTotal());
		assertThat(pago.getValue().getProveedor()).isEqualTo(PagoService.PROVEEDOR_SIMULADO);
		assertThat(pago.getValue().getMetodo()).isEqualTo(PagoService.METODO_POR_DEFECTO);
		assertThat(reserva.getPago().getEstado()).isEqualTo(EstadoPago.APROBADO);
		assertThat(reserva.getPago().getReferencia()).startsWith("sim_pi_");

		// La cotización queda usada: no sirve para otra reserva
		ArgumentCaptor<Cotizacion> usada = ArgumentCaptor.forClass(Cotizacion.class);
		verify(cotizacionRepo).save(usada.capture());
		assertThat(usada.getValue().getIdReserva()).isEqualTo(ID_RESERVA);
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.RESERVA_CREADA),
				eq(AuditoriaService.ENTIDAD_RESERVA), eq(ID_RESERVA), eq(ResultadoAuditoria.EXITOSO), anyString());
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.PAGO),
				eq(AuditoriaService.ENTIDAD_RESERVA), eq(ID_RESERVA), eq(ResultadoAuditoria.EXITOSO), anyString());

		// Avisos al huésped y al anfitrión, como en el proyecto base
		verify(notificacionService).enviar(eq(ID_HUESPED), eq(NotificacionService.RESERVA_CONFIRMADA),
				contains("Tu reserva 15"));
		verify(notificacionService).enviar(eq(ID_ANFITRION), eq(NotificacionService.RESERVA_CONFIRMADA),
				contains("Casa en Villa de Leyva"));
	}

	@Test
	@DisplayName("CA-162 Si el pago se rechaza, la reserva queda CANCELADA, la cotización sigue libre y responde 402")
	void pagoRechazado_CA162() {
		listoParaPagar();
		SolicitudReservaDTO solicitud = solicitud(ID_COTIZACION);
		solicitud.setMetodoPago("pm_card_chargeDeclinedInsufficientFunds");

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud, ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.PAGO_RECHAZADO);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.PAGO_RECHAZADO
				+ "La tarjeta no tiene fondos suficientes." + ReservaService.SIN_PAGAR);
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(resultado.getDato().getPago().getEstado()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(estadosGuardados).containsExactly(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CANCELADA);
		// La cotización no queda usada: se puede intentar con otro método de pago
		verify(cotizacionRepo, never()).save(any());
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.PAGO),
				eq(AuditoriaService.ENTIDAD_RESERVA), eq(ID_RESERVA), eq(ResultadoAuditoria.RECHAZADO), anyString());
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.RESERVA_CREADA),
				eq(AuditoriaService.ENTIDAD_RESERVA), eq(ID_RESERVA), eq(ResultadoAuditoria.RECHAZADO),
				eq(resultado.getMensaje()));
		verify(notificacionService).enviar(eq(ID_HUESPED), eq(NotificacionService.PAGO_NO_CONFIRMADO), anyString());
		verify(notificacionService, never()).enviar(any(), eq(NotificacionService.RESERVA_CONFIRMADA), anyString());
	}

	@Test
	@DisplayName("CA-162 Si la pasarela falla, la reserva queda CANCELADA y responde 502")
	void pagoConError_CA162() {
		listoParaPagar();
		SolicitudReservaDTO solicitud = solicitud(ID_COTIZACION);
		solicitud.setMetodoPago("pm_card_error");

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud, ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.PAGO_ERROR);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.PAGO_NO_CONFIRMADO + PagoService.FALLA_PASARELA
				+ ReservaService.SIN_PAGAR);
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
		assertThat(resultado.getDato().getPago().getEstado()).isEqualTo(EstadoPago.ERROR);
		verify(cotizacionRepo, never()).save(any());
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.PAGO),
				eq(AuditoriaService.ENTIDAD_RESERVA), eq(ID_RESERVA), eq(ResultadoAuditoria.ERROR), anyString());
	}

	@Test
	@DisplayName("Un método de pago sin la forma pm_... responde datos inválidos sin tocar la base de datos")
	void metodoInvalido() {
		SolicitudReservaDTO solicitud = solicitud(ID_COTIZACION);
		solicitud.setMetodoPago("4242 4242 4242 4242");

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud, ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo(PagoService.METODO_INVALIDO);
		verify(cotizacionRepo, never()).buscarParaReservar(any());
		verify(pagoRepo, never()).save(any());
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.RESERVA_CREADA),
				eq(AuditoriaService.ENTIDAD_RESERVA), isNull(), eq(ResultadoAuditoria.RECHAZADO),
				eq(PagoService.METODO_INVALIDO));
	}

	@Test
	@DisplayName("Sin cotización no se reserva y el rechazo queda auditado")
	void faltaCotizacion() {
		Resultado<ReservaDTO> resultado = servicio.crear(new SolicitudReservaDTO(), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.FALTA_COTIZACION);
		assertThat(servicio.crear(null, ID_HUESPED).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		verify(auditoriaService, times(2)).registrar(eq(ID_HUESPED),
				eq(AuditoriaService.RESERVA_CREADA), eq(AuditoriaService.ENTIDAD_RESERVA), isNull(),
				eq(ResultadoAuditoria.RECHAZADO), eq(ReservaService.FALTA_COTIZACION));
	}

	@Test
	@DisplayName("La cotización de otro usuario es como si no existiera")
	void cotizacionDeOtro() {
		cotizacion.setIdUsuario(99L);
		when(cotizacionRepo.buscarParaReservar(ID_COTIZACION)).thenReturn(Optional.of(cotizacion));

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.COTIZACION_NO_EXISTE);
		sinReserva();
	}

	@Test
	@DisplayName("Una cotización o un alojamiento que no existe responde no encontrado")
	void noExiste() {
		when(cotizacionRepo.buscarParaReservar(7L)).thenReturn(Optional.empty());
		assertThat(servicio.crear(solicitud(7L), ID_HUESPED).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);

		when(cotizacionRepo.buscarParaReservar(ID_COTIZACION)).thenReturn(Optional.of(cotizacion));
		when(alojamientoRepo.buscarParaReservar(ID_ALOJAMIENTO)).thenReturn(Optional.empty());
		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo(CalendarioService.NO_EXISTE);
		sinReserva();
	}

	@Test
	@DisplayName("Una cotización ya usada no sirve para otra reserva")
	void cotizacionUsada() {
		cotizacion.setIdReserva(8L);
		bloqueos();

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.COTIZACION_NO_VIGENTE);
		sinReserva();
	}

	@Test
	@DisplayName("Una cotización vencida no sirve para reservar")
	void cotizacionVencida() {
		cotizacion.setFechaExpiracion(LocalDateTime.now().minusSeconds(1));
		bloqueos();

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.COTIZACION_NO_VIGENTE);
		sinReserva();
	}

	@Test
	@DisplayName("CA-071 Si el alojamiento dejó de estar ACTIVA después de cotizar, no se reserva")
	void noActiva_CA071() {
		alojamiento.setEstado(EstadoAlojamiento.BLOQUEADA);
		bloqueos();

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).isEqualTo(CalendarioService.noActiva(alojamiento)).contains("BLOQUEADA");
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.RESERVA_CREADA),
				eq(AuditoriaService.ENTIDAD_RESERVA), isNull(), eq(ResultadoAuditoria.RECHAZADO), anyString());
		sinReserva();
	}

	@Test
	@DisplayName("Nadie puede reservar su propio alojamiento")
	void propioAlojamiento() {
		alojamiento.setIdAnfitrion(ID_HUESPED);
		bloqueos();

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.PROPIO_ALOJAMIENTO);
		sinReserva();
	}

	@Test
	@DisplayName("Si la llegada cotizada ya pasó, no se reserva")
	void llegadaPasada() {
		cotizacion.setFechaEntrada(LocalDate.now().minusDays(1));
		bloqueos();

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo(CalendarioService.LLEGADA_EN_EL_PASADO);
		sinReserva();
	}

	@Test
	@DisplayName("CA-072 CA-074 Si otra reserva tomó las fechas, la revalidación lo detecta y no se reserva")
	void periodoTomado_CA072_CA074() {
		bloqueos();
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(false);

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_DISPONIBLE);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.PERIODO_TOMADO);
		sinReserva();
	}

	@Test
	@DisplayName("CA-072 Si Neon rechaza el cruce al confirmar, responde que otra reserva tomó el período y lo audita")
	void restriccionDeNeon_CA072() {
		bloqueos();
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(pagoRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
		// El INSERT en PENDIENTE_PAGO pasa; la restricción se aplica al pasar a CONFIRMADA
		when(reservaRepo.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0))
				.thenThrow(new DataIntegrityViolationException("error",
						new IllegalStateException("violates exclusion constraint \"ex_reserva_sin_cruces\"")));

		Resultado<ReservaDTO> resultado = servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_DISPONIBLE);
		assertThat(resultado.getMensaje()).isEqualTo(ReservaService.PERIODO_TOMADO);
		verify(auditoriaService).registrar(eq(ID_HUESPED), eq(AuditoriaService.RESERVA_CREADA),
				eq(AuditoriaService.ENTIDAD_RESERVA), isNull(), eq(ResultadoAuditoria.RECHAZADO),
				eq(ReservaService.PERIODO_TOMADO));
		verify(cotizacionRepo, never()).save(any());
	}

	@Test
	@DisplayName("Otra violación de datos no se oculta: sigue al manejador global y no se audita como éxito")
	void otraViolacionSePropaga() {
		bloqueos();
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		DataIntegrityViolationException otra = new DataIntegrityViolationException("fk_reserva_cotizacion");
		when(reservaRepo.saveAndFlush(any())).thenThrow(otra);

		assertThatThrownBy(() -> servicio.crear(solicitud(ID_COTIZACION), ID_HUESPED)).isSameAs(otra);
		verify(pagoRepo, never()).save(any());
		verify(auditoriaService, never()).registrar(any(), anyString(), anyString(), any(), any(), anyString());
	}

	/**
	 * Todo listo para pagar: bloqueos, fechas libres, la reserva guardada con
	 * su id (anotando su estado en cada guardado), el pago guardado y el
	 * alojamiento para el título.
	 */
	private void listoParaPagar() {
		bloqueos();
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(reservaRepo.saveAndFlush(any())).thenAnswer(invocacion -> {
			Reserva r = invocacion.getArgument(0);
			r.setIdReserva(ID_RESERVA);
			estadosGuardados.add(r.getEstado());
			return r;
		});
		when(pagoRepo.save(any())).thenAnswer(invocacion -> {
			Pago p = invocacion.getArgument(0);
			p.setIdPago(30L);
			return p;
		});
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
	}

	/** La cotización y el alojamiento se leen con bloqueo. */
	private void bloqueos() {
		when(cotizacionRepo.buscarParaReservar(ID_COTIZACION)).thenReturn(Optional.of(cotizacion));
		when(alojamientoRepo.buscarParaReservar(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
	}

	/** No se creó reserva ni se marcó la cotización como usada. */
	private void sinReserva() {
		verify(reservaRepo, never()).saveAndFlush(any());
		verify(cotizacionRepo, never()).save(any());
	}

	private static SolicitudReservaDTO solicitud(Long idCotizacion) {
		SolicitudReservaDTO solicitud = new SolicitudReservaDTO();
		solicitud.setIdCotizacion(idCotizacion);
		return solicitud;
	}
}
