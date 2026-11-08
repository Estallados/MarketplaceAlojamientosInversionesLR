package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.DisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PeriodoDisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.RangoReservadoDTO;
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
 * Calendario de disponibilidad de los alojamientos (HU-20, RF-025). El
 * anfitrión registra los períodos en que ofrece su alojamiento; las fechas
 * reservadas no se guardan aparte, sino que se calculan a partir de las
 * reservas que ocupan fechas.
 * <p>
 * Una reserva ocupa fechas si está CONFIRMADA o EN_REVISION, o si está
 * PENDIENTE_PAGO y se creó hace menos de {@value #MINUTOS_PENDIENTE} minutos.
 * Un período no se puede modificar ni eliminar si deja por fuera una de esas
 * reservas (CA-118). Cada operación queda en la auditoría (HU-26).
 * </p>
 */
@Service
public class CalendarioService {

	/** Estados de reserva que ocupan fechas. */
	public static final List<EstadoReserva> ESTADOS_OCUPAN = List.of(EstadoReserva.CONFIRMADA,
			EstadoReserva.EN_REVISION);

	/** Minutos que una reserva sin pagar retiene las fechas. */
	public static final int MINUTOS_PENDIENTE = 15;

	/** Mensaje cuando el alojamiento no existe. */
	public static final String NO_EXISTE = "El alojamiento no existe.";

	/** Mensaje cuando el período no existe. */
	public static final String PERIODO_NO_EXISTE = "El período no existe.";

	/** Mensaje cuando el usuario no es el dueño ni un administrador. */
	public static final String SIN_PERMISO = "Solo el anfitrión propietario o un administrador puede modificar la "
			+ "disponibilidad.";

	/** Mensaje cuando el período nuevo se cruza con otro. */
	public static final String CRUCE_AL_CREAR = "El período se cruza con otro período ya registrado; modifícalo en "
			+ "lugar de crear uno nuevo.";

	/** Mensaje cuando el período modificado se cruza con otro. */
	public static final String CRUCE_AL_MODIFICAR = "El período se cruza con otro período ya registrado.";

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Repositorio de períodos de disponibilidad. */
	private final PeriodoDisponibilidadRepository periodoRepo;

	/** Repositorio de reservas. */
	private final ReservaRepository reservaRepo;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el servicio.
	 *
	 * @param alojamientoRepo  repositorio de alojamientos
	 * @param periodoRepo      repositorio de períodos
	 * @param reservaRepo      repositorio de reservas
	 * @param auditoriaService servicio de auditoría
	 */
	public CalendarioService(AlojamientoRepository alojamientoRepo, PeriodoDisponibilidadRepository periodoRepo,
			ReservaRepository reservaRepo, AuditoriaService auditoriaService) {
		this.alojamientoRepo = alojamientoRepo;
		this.periodoRepo = periodoRepo;
		this.reservaRepo = reservaRepo;
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Las reservas PENDIENTE_PAGO creadas antes de este instante ya no ocupan
	 * fechas.
	 *
	 * @return instante límite (ahora menos {@value #MINUTOS_PENDIENTE} minutos)
	 */
	public static LocalDateTime limitePendiente() {
		return LocalDateTime.now().minusMinutes(MINUTOS_PENDIENTE);
	}

	/**
	 * Indica si el alojamiento tiene algún período que termine después de hoy.
	 * Es uno de los datos mínimos para enviar la publicación a revisión
	 * (CA-097).
	 *
	 * @param idAlojamiento alojamiento
	 * @return {@code true} si tiene disponibilidad futura
	 */
	public boolean tieneDisponibilidadFutura(Long idAlojamiento) {
		return periodoRepo.existsByIdAlojamientoAndFechaFinAfter(idAlojamiento, LocalDate.now());
	}

	/**
	 * Calendario del alojamiento: los períodos ofrecidos y los rangos
	 * reservados. Es público si está ACTIVA; en otro estado solo lo ven su
	 * anfitrión o un administrador.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       usuario que consulta; {@code null} si no hay sesión
	 * @param esAdmin       {@code true} si es administrador
	 * @return el calendario; o NO_ENCONTRADO si no existe o el usuario no puede
	 *         verlo
	 */
	@Transactional(readOnly = true)
	public Resultado<DisponibilidadDTO> calendario(Long idAlojamiento, Long idActor, boolean esAdmin) {
		Optional<Alojamiento> alojamiento = alojamientoRepo.findById(idAlojamiento);
		if (alojamiento.isEmpty() || !(alojamiento.get().getEstado() == EstadoAlojamiento.ACTIVA || esAdmin
				|| alojamiento.get().getIdAnfitrion().equals(idActor))) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		DisponibilidadDTO dto = new DisponibilidadDTO();
		dto.setIdAlojamiento(idAlojamiento);
		dto.setEstadoAlojamiento(alojamiento.get().getEstado());
		dto.setPeriodos(periodoRepo.findByIdAlojamientoOrderByFechaInicio(idAlojamiento).stream()
				.map(CalendarioService::convertirDTO).toList());
		dto.setReservados(ocupadas(idAlojamiento, LocalDate.now()).stream().map(CalendarioService::rango).toList());
		return Resultado.exito(dto);
	}

	/**
	 * Registra un período disponible (CA-115). Lo puede hacer el anfitrión
	 * dueño o un administrador, y no puede cruzarse con otro período del mismo
	 * alojamiento.
	 *
	 * @param idAlojamiento alojamiento
	 * @param datos         fechas del período
	 * @param idActor       usuario que lo registra
	 * @param esAdmin       {@code true} si es administrador
	 * @return el período; NO_ENCONTRADO si el alojamiento no existe;
	 *         SIN_PERMISO si no es el dueño; DATOS_INVALIDOS si las fechas no
	 *         son válidas; ESTADO_INVALIDO si se cruza con otro período
	 */
	@Transactional
	public Resultado<PeriodoDisponibilidadDTO> agregarPeriodo(Long idAlojamiento, PeriodoDisponibilidadDTO datos,
			Long idActor, boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		String error = validarPeriodo(datos);
		if (error != null) {
			auditar(idActor, null, ResultadoAuditoria.RECHAZADO, error);
			return Resultado.error(Resultado.DATOS_INVALIDOS, error);
		}
		if (periodoRepo.existeCruce(idAlojamiento, datos.getFechaInicio(), datos.getFechaFin())) {
			auditar(idActor, null, ResultadoAuditoria.RECHAZADO, "Cruce con otro período del alojamiento "
					+ idAlojamiento);
			return Resultado.error(Resultado.ESTADO_INVALIDO, CRUCE_AL_CREAR);
		}
		PeriodoDisponibilidad periodo = new PeriodoDisponibilidad();
		periodo.setIdAlojamiento(idAlojamiento);
		periodo.setFechaInicio(datos.getFechaInicio());
		periodo.setFechaFin(datos.getFechaFin());
		periodo = periodoRepo.save(periodo);
		auditar(idActor, periodo.getIdPeriodo(), ResultadoAuditoria.EXITOSO,
				"Período agregado " + periodo.getFechaInicio() + " a " + periodo.getFechaFin());
		return Resultado.exito(convertirDTO(periodo));
	}

	/**
	 * Modifica un período no reservado (CA-117). Se rechaza si deja por fuera
	 * una reserva que estaba dentro del período (CA-118) o si se cruza con
	 * otro período.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idPeriodo     período a modificar
	 * @param datos         fechas nuevas
	 * @param idActor       usuario que lo modifica
	 * @param esAdmin       {@code true} si es administrador
	 * @return el período actualizado; NO_ENCONTRADO si no existe; SIN_PERMISO
	 *         si no es el dueño; DATOS_INVALIDOS si las fechas no son válidas;
	 *         ESTADO_INVALIDO si afecta una reserva o se cruza con otro período
	 */
	@Transactional
	public Resultado<PeriodoDisponibilidadDTO> actualizarPeriodo(Long idAlojamiento, Long idPeriodo,
			PeriodoDisponibilidadDTO datos, Long idActor, boolean esAdmin) {
		Resultado<PeriodoDisponibilidad> carga = cargarPeriodo(idAlojamiento, idPeriodo, idActor, esAdmin);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		PeriodoDisponibilidad periodo = carga.getDato();
		String error = validarPeriodo(datos);
		if (error != null) {
			auditar(idActor, idPeriodo, ResultadoAuditoria.RECHAZADO, error);
			return Resultado.error(Resultado.DATOS_INVALIDOS, error);
		}
		if (periodoRepo.existeCruceConOtro(idAlojamiento, datos.getFechaInicio(), datos.getFechaFin(), idPeriodo)) {
			auditar(idActor, idPeriodo, ResultadoAuditoria.RECHAZADO, CRUCE_AL_MODIFICAR);
			return Resultado.error(Resultado.ESTADO_INVALIDO, CRUCE_AL_MODIFICAR);
		}
		Optional<Reserva> afectada = reservasDentro(periodo).stream()
				.filter(r -> r.getFechaEntrada().isBefore(datos.getFechaInicio())
						|| r.getFechaSalida().isAfter(datos.getFechaFin()))
				.findFirst();
		if (afectada.isPresent()) {
			Reserva reserva = afectada.get();
			auditar(idActor, idPeriodo, ResultadoAuditoria.RECHAZADO,
					"El cambio deja por fuera la reserva " + reserva.getIdReserva());
			return Resultado.error(Resultado.ESTADO_INVALIDO, "No puedes modificar el período: afecta la reserva del "
					+ reserva.getFechaEntrada() + " al " + reserva.getFechaSalida() + ".");
		}
		periodo.setFechaInicio(datos.getFechaInicio());
		periodo.setFechaFin(datos.getFechaFin());
		PeriodoDisponibilidad guardado = periodoRepo.save(periodo);
		auditar(idActor, idPeriodo, ResultadoAuditoria.EXITOSO,
				"Período modificado a " + guardado.getFechaInicio() + " - " + guardado.getFechaFin());
		return Resultado.exito(convertirDTO(guardado));
	}

	/**
	 * Elimina un período que no tenga reservas dentro.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idPeriodo     período a eliminar
	 * @param idActor       usuario que lo elimina
	 * @param esAdmin       {@code true} si es administrador
	 * @return éxito; NO_ENCONTRADO si no existe; SIN_PERMISO si no es el dueño;
	 *         ESTADO_INVALIDO si tiene reservas
	 */
	@Transactional
	public Resultado<Void> eliminarPeriodo(Long idAlojamiento, Long idPeriodo, Long idActor, boolean esAdmin) {
		Resultado<PeriodoDisponibilidad> carga = cargarPeriodo(idAlojamiento, idPeriodo, idActor, esAdmin);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		if (!reservasDentro(carga.getDato()).isEmpty()) {
			auditar(idActor, idPeriodo, ResultadoAuditoria.RECHAZADO, "Intento de eliminar un período con reservas");
			return Resultado.error(Resultado.ESTADO_INVALIDO, "No puedes eliminar el período: tiene reservas activas.");
		}
		periodoRepo.delete(carga.getDato());
		auditar(idActor, idPeriodo, ResultadoAuditoria.EXITOSO, "Período eliminado");
		return Resultado.exito(null);
	}

	/**
	 * Valida las fechas de un período: las dos presentes, el fin después del
	 * inicio y el fin no en el pasado.
	 *
	 * @param datos período a validar
	 * @return el mensaje de error, o {@code null} si es válido
	 */
	public static String validarPeriodo(PeriodoDisponibilidadDTO datos) {
		if (datos == null || datos.getFechaInicio() == null || datos.getFechaFin() == null) {
			return "Debes indicar la fecha de inicio y la de fin del período.";
		}
		if (!datos.getFechaFin().isAfter(datos.getFechaInicio())) {
			return "Las fechas son inválidas: el fin del período debe ser posterior al inicio.";
		}
		if (datos.getFechaFin().isBefore(LocalDate.now())) {
			return "El período no puede terminar en el pasado.";
		}
		return null;
	}

	/**
	 * Reservas que ocupan fechas dentro de un período.
	 *
	 * @param periodo período a revisar
	 * @return reservas que se cruzan con el período
	 */
	private List<Reserva> reservasDentro(PeriodoDisponibilidad periodo) {
		return ocupadas(periodo.getIdAlojamiento(), periodo.getFechaInicio()).stream()
				.filter(r -> !r.getFechaEntrada().isAfter(periodo.getFechaFin()))
				.toList();
	}

	/**
	 * Reservas que ocupan fechas del alojamiento y terminan después de un día.
	 *
	 * @param idAlojamiento alojamiento
	 * @param desde         día de referencia
	 * @return reservas ordenadas por fecha de entrada
	 */
	private List<Reserva> ocupadas(Long idAlojamiento, LocalDate desde) {
		return reservaRepo.buscarOcupadas(idAlojamiento, desde, ESTADOS_OCUPAN, limitePendiente());
	}

	/**
	 * Carga un período del alojamiento verificando que el usuario pueda
	 * modificarlo.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idPeriodo     período
	 * @param idActor       usuario
	 * @param esAdmin       {@code true} si es administrador
	 * @return el período; NO_ENCONTRADO o SIN_PERMISO
	 */
	private Resultado<PeriodoDisponibilidad> cargarPeriodo(Long idAlojamiento, Long idPeriodo, Long idActor,
			boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		return periodoRepo.findById(idPeriodo).filter(p -> p.getIdAlojamiento().equals(idAlojamiento))
				.map(Resultado::exito)
				.orElseGet(() -> Resultado.error(Resultado.NO_ENCONTRADO, PERIODO_NO_EXISTE));
	}

	/**
	 * Carga el alojamiento verificando que el usuario pueda modificar su
	 * disponibilidad (dueño o administrador).
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       usuario
	 * @param esAdmin       {@code true} si es administrador
	 * @return el alojamiento; NO_ENCONTRADO o SIN_PERMISO
	 */
	private Resultado<Alojamiento> cargarModificable(Long idAlojamiento, Long idActor, boolean esAdmin) {
		Optional<Alojamiento> alojamiento = alojamientoRepo.findById(idAlojamiento);
		if (alojamiento.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		if (!esAdmin && !alojamiento.get().getIdAnfitrion().equals(idActor)) {
			auditar(idActor, null, ResultadoAuditoria.RECHAZADO,
					"Sin permiso sobre la disponibilidad del alojamiento " + idAlojamiento);
			return Resultado.error(Resultado.SIN_PERMISO, SIN_PERMISO);
		}
		return Resultado.exito(alojamiento.get());
	}

	/**
	 * Registra en la auditoría una operación sobre la disponibilidad.
	 *
	 * @param idActor   usuario
	 * @param idPeriodo período afectado; puede ser {@code null}
	 * @param resultado resultado de la operación
	 * @param detalle   detalle para la auditoría
	 */
	private void auditar(Long idActor, Long idPeriodo, ResultadoAuditoria resultado, String detalle) {
		auditoriaService.registrar(idActor, AuditoriaService.DISPONIBILIDAD, AuditoriaService.ENTIDAD_PERIODO,
				idPeriodo, resultado, detalle);
	}

	/**
	 * Convierte un período a DTO.
	 *
	 * @param periodo período
	 * @return período para la API
	 */
	private static PeriodoDisponibilidadDTO convertirDTO(PeriodoDisponibilidad periodo) {
		PeriodoDisponibilidadDTO dto = new PeriodoDisponibilidadDTO();
		dto.setIdPeriodo(periodo.getIdPeriodo());
		dto.setIdAlojamiento(periodo.getIdAlojamiento());
		dto.setFechaInicio(periodo.getFechaInicio());
		dto.setFechaFin(periodo.getFechaFin());
		return dto;
	}

	/**
	 * Convierte una reserva en el rango de fechas que ocupa.
	 *
	 * @param reserva reserva
	 * @return rango ocupado
	 */
	private static RangoReservadoDTO rango(Reserva reserva) {
		RangoReservadoDTO rango = new RangoReservadoDTO();
		rango.setFechaEntrada(reserva.getFechaEntrada());
		rango.setFechaSalida(reserva.getFechaSalida());
		return rango;
	}
}
