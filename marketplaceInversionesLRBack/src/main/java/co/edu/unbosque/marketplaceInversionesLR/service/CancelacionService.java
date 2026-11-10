package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Cancelación de reservas por parte del huésped (HU-01, RF-002). Aplica la
 * política de cancelación del alojamiento según los días que faltan para la
 * llegada (CA-001): FLEXIBLE devuelve el 100 % con 1 o más días de
 * anticipación; MODERADA el 100 % con 5 o más días y el 50 % con 1 a 4 días;
 * ESTRICTA el 50 % con 7 o más días y nada después. Las reglas salen de la
 * tabla {@code politica_cancelacion}.
 * <p>
 * La cancelación queda registrada con usuario, fecha, motivo, devolución y
 * estado final (CA-004), y la devolución se hace con la misma pasarela de
 * prueba del pago. La reserva queda CANCELADA y sus fechas vuelven a estar
 * disponibles, porque la disponibilidad solo cuenta las reservas activas
 * (CA-007). Si la política no da devolución, se respeta: la reserva se cancela
 * sin devolver nada (CA-009).
 * </p>
 * <p>
 * La reserva se lee con bloqueo, así dos cancelaciones simultáneas se procesan
 * una después de la otra. En Neon, {@code trg_validar_cancelacion} comprueba la
 * devolución y quién cancela, y {@code trg_cancelacion_reserva_cancelada}, al
 * confirmar, que la reserva haya quedado CANCELADA.
 * </p>
 */
@Service
public class CancelacionService {

	/** Política que se usa si el alojamiento no tiene una asignada. */
	public static final String POLITICA_POR_DEFECTO = "FLEXIBLE";

	/** Mensaje cuando la reserva no existe o es de otro usuario. */
	public static final String NO_EXISTE = "La reserva no existe.";

	/** Mensaje cuando falta el motivo o es muy largo. */
	public static final String MOTIVO_INVALIDO = "El motivo de la cancelación es obligatorio y puede tener máximo "
			+ LimitesTexto.MOTIVO + " caracteres.";

	/** Mensaje cuando la llegada ya pasó o es hoy. */
	public static final String SOLO_FUTURAS = "Solo se pueden cancelar reservas futuras.";

	/** Inicio del mensaje cuando la reserva no está en un estado cancelable. */
	public static final String ESTADO_NO_CANCELABLE = "La reserva no se puede cancelar en estado ";

	/** Cien, para calcular porcentajes. */
	private static final int CIEN = 100;

	/** Repositorio de reservas. */
	private final ReservaRepository reservaRepo;

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Repositorio de políticas de cancelación. */
	private final PoliticaCancelacionRepository politicaRepo;

	/** Repositorio de cancelaciones. */
	private final CancelacionRepository cancelacionRepo;

	/** Hace la devolución del pago. */
	private final PagoService pagoService;

	/** Avisa al huésped y al anfitrión. */
	private final NotificacionService notificacionService;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/**
	 * Crea el servicio.
	 *
	 * @param reservaRepo         repositorio de reservas
	 * @param alojamientoRepo     repositorio de alojamientos
	 * @param politicaRepo        repositorio de políticas
	 * @param cancelacionRepo     repositorio de cancelaciones
	 * @param pagoService         servicio de pagos
	 * @param notificacionService servicio de notificaciones
	 * @param auditoriaService    servicio de auditoría
	 * @param modelMapper         copiador de entidades a DTO
	 */
	public CancelacionService(ReservaRepository reservaRepo, AlojamientoRepository alojamientoRepo,
			PoliticaCancelacionRepository politicaRepo, CancelacionRepository cancelacionRepo, PagoService pagoService,
			NotificacionService notificacionService, AuditoriaService auditoriaService, ModelMapper modelMapper) {
		this.reservaRepo = reservaRepo;
		this.alojamientoRepo = alojamientoRepo;
		this.politicaRepo = politicaRepo;
		this.cancelacionRepo = cancelacionRepo;
		this.pagoService = pagoService;
		this.notificacionService = notificacionService;
		this.auditoriaService = auditoriaService;
		this.modelMapper = modelMapper;
	}

	/**
	 * Porcentaje a devolver según la política y los días de anticipación
	 * (CA-001).
	 *
	 * @param politica         política del alojamiento
	 * @param diasAnticipacion días que faltan para la llegada
	 * @return porcentaje a devolver (0 a 100)
	 */
	public static int porcentajeReembolso(PoliticaCancelacion politica, long diasAnticipacion) {
		if (politica.getDiasReembolsoTotal() != null && diasAnticipacion >= politica.getDiasReembolsoTotal()) {
			return CIEN;
		}
		if (politica.getDiasReembolsoParcial() != null && diasAnticipacion >= politica.getDiasReembolsoParcial()
				&& politica.getPorcentajeParcial() != null) {
			return politica.getPorcentajeParcial();
		}
		return 0;
	}

	/**
	 * Valor a devolver: el total por el porcentaje, redondeado a 2 decimales
	 * hacia arriba en la mitad. Es la misma cuenta que valida Neon
	 * ({@code ROUND(total * porcentaje / 100, 2)}).
	 *
	 * @param total      total pagado
	 * @param porcentaje porcentaje a devolver
	 * @return valor de la devolución
	 */
	public static BigDecimal devolucion(BigDecimal total, int porcentaje) {
		return total.multiply(BigDecimal.valueOf(porcentaje)).divide(BigDecimal.valueOf(CIEN), 2,
				RoundingMode.HALF_UP);
	}

	/**
	 * Cancela una reserva futura del huésped autenticado: aplica la política,
	 * deja la reserva CANCELADA, registra la cancelación, hace la devolución y
	 * avisa al huésped y al anfitrión.
	 *
	 * @param idReserva reserva a cancelar
	 * @param motivo    motivo obligatorio, de máximo
	 *                  {@value LimitesTexto#MOTIVO} caracteres
	 * @param idActor   huésped autenticado
	 * @return la cancelación; DATOS_INVALIDOS si el motivo falta o es muy largo;
	 *         NO_ENCONTRADO si la reserva no existe o es de otro usuario;
	 *         ESTADO_INVALIDO si no se puede cancelar
	 */
	@Transactional
	public Resultado<CancelacionDTO> cancelar(Long idReserva, String motivo, Long idActor) {
		if (motivo == null || motivo.isBlank() || LimitesTexto.excede(motivo, LimitesTexto.MOTIVO)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, MOTIVO_INVALIDO);
		}
		Optional<Reserva> reservaOpt = idReserva == null ? Optional.empty()
				: reservaRepo.buscarParaCancelar(idReserva).filter(r -> Objects.equals(r.getIdHuesped(), idActor));
		if (reservaOpt.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Reserva reserva = reservaOpt.get();
		String noCancelable = motivoNoCancelable(reserva);
		if (noCancelable != null) {
			auditoriaService.registrar(idActor, AuditoriaService.CANCELACION, AuditoriaService.ENTIDAD_RESERVA,
					idReserva, ResultadoAuditoria.RECHAZADO, noCancelable);
			return Resultado.error(Resultado.ESTADO_INVALIDO, noCancelable);
		}

		Optional<Alojamiento> alojamiento = alojamientoRepo.findById(reserva.getIdAlojamiento());
		PoliticaCancelacion politica = politicaDe(alojamiento);
		long dias = diasAnticipacion(reserva);
		int porcentaje = porcentajeReembolso(politica, dias);
		BigDecimal valor = devolucion(reserva.getTotal(), porcentaje);

		reserva.setEstado(EstadoReserva.CANCELADA);
		reservaRepo.save(reserva);

		Cancelacion cancelacion = new Cancelacion();
		cancelacion.setIdReserva(idReserva);
		cancelacion.setIdUsuario(idActor);
		cancelacion.setFecha(LocalDateTime.now());
		cancelacion.setMotivo(motivo.trim());
		cancelacion.setPolitica(politica.getNombre());
		cancelacion.setPorcentajeReembolso(porcentaje);
		cancelacion.setValorReembolso(valor);
		cancelacion.setEstado(EstadoCancelacion.COMPLETADA);
		cancelacion.setEstadoFinalReserva(EstadoReserva.CANCELADA);
		cancelacion = cancelacionRepo.save(cancelacion);

		pagoService.reembolsar(idReserva, valor);
		String fechas = "del " + reserva.getFechaEntrada() + " al " + reserva.getFechaSalida();
		notificacionService.enviar(alojamiento.map(Alojamiento::getIdAnfitrion).orElse(null),
				NotificacionService.RESERVA_CANCELADA,
				"La reserva " + idReserva + " " + fechas + " fue cancelada por el huésped; las fechas quedan libres.");
		notificacionService.enviar(idActor, NotificacionService.RESERVA_CANCELADA, "Cancelaste la reserva " + idReserva
				+ " " + fechas + ". Devolución (" + politica.getNombre() + ", " + porcentaje + " %): " + valor + ".");
		auditoriaService.registrar(idActor, AuditoriaService.CANCELACION, AuditoriaService.ENTIDAD_RESERVA, idReserva,
				ResultadoAuditoria.EXITOSO, politica.getNombre() + " " + porcentaje + " %, devolución " + valor);
		return Resultado.exito(modelMapper.map(cancelacion, CancelacionDTO.class));
	}

	/**
	 * Devolución que recibiría el huésped si cancelara hoy, con la misma regla
	 * de {@link #cancelar(Long, String, Long)} (CA-001). No cambia nada ni
	 * queda en la auditoría.
	 *
	 * @param idReserva reserva a consultar
	 * @param idActor   huésped autenticado
	 * @return la estimación; NO_ENCONTRADO si la reserva no existe o es de otro
	 *         usuario; ESTADO_INVALIDO si no se puede cancelar
	 */
	@Transactional(readOnly = true)
	public Resultado<SimulacionCancelacionDTO> simular(Long idReserva, Long idActor) {
		Optional<Reserva> reservaOpt = idReserva == null ? Optional.empty()
				: reservaRepo.findById(idReserva).filter(r -> Objects.equals(r.getIdHuesped(), idActor));
		if (reservaOpt.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Reserva reserva = reservaOpt.get();
		String noCancelable = motivoNoCancelable(reserva);
		if (noCancelable != null) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, noCancelable);
		}

		PoliticaCancelacion politica = politicaDe(alojamientoRepo.findById(reserva.getIdAlojamiento()));
		long dias = diasAnticipacion(reserva);
		int porcentaje = porcentajeReembolso(politica, dias);

		SimulacionCancelacionDTO simulacion = new SimulacionCancelacionDTO();
		simulacion.setIdReserva(idReserva);
		simulacion.setPolitica(politica.getNombre());
		simulacion.setDiasAnticipacion(dias);
		simulacion.setPorcentajeReembolso(porcentaje);
		simulacion.setValorReembolso(devolucion(reserva.getTotal(), porcentaje));
		simulacion.setTotalPagado(reserva.getTotal());
		return Resultado.exito(simulacion);
	}

	/**
	 * Causa por la que la reserva no se puede cancelar: solo se cancelan las
	 * CONFIRMADA o EN_REVISION cuya llegada aún no llegó.
	 *
	 * @param reserva reserva a revisar
	 * @return la causa, o {@code null} si se puede cancelar
	 */
	private static String motivoNoCancelable(Reserva reserva) {
		if (reserva.getEstado() != EstadoReserva.CONFIRMADA && reserva.getEstado() != EstadoReserva.EN_REVISION) {
			return ESTADO_NO_CANCELABLE + reserva.getEstado() + ".";
		}
		if (!reserva.getFechaEntrada().isAfter(LocalDate.now())) {
			return SOLO_FUTURAS;
		}
		return null;
	}

	/**
	 * Política del alojamiento, o la política por defecto si no tiene una
	 * asignada.
	 *
	 * @param alojamiento alojamiento de la reserva
	 * @return política a aplicar
	 */
	private PoliticaCancelacion politicaDe(Optional<Alojamiento> alojamiento) {
		return alojamiento.map(Alojamiento::getIdPolitica).flatMap(politicaRepo::findById)
				.or(() -> politicaRepo.findByNombre(POLITICA_POR_DEFECTO)).orElseThrow();
	}

	/**
	 * Días que faltan desde hoy hasta la llegada.
	 *
	 * @param reserva reserva
	 * @return cantidad de días (cero o negativa si la llegada es hoy o ya pasó)
	 */
	private static long diasAnticipacion(Reserva reserva) {
		return ChronoUnit.DAYS.between(LocalDate.now(), reserva.getFechaEntrada());
	}
}
