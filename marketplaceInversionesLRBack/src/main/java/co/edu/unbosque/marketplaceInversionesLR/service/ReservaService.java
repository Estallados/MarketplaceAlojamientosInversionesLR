package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SolicitudReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.ObjectMapper;

/**
 * Creación de reservas con bloqueo automático de fechas (HU-22).
 * <p>
 * La reserva se crea en una transacción. Se bloquean la cotización y el
 * alojamiento, así dos reservas simultáneas sobre las mismas fechas se
 * procesan una después de la otra (CA-072). Con el bloqueo puesto se valida la
 * cotización (vigente, del huésped y sin usar), que el alojamiento esté ACTIVA
 * (CA-071) y no sea del propio huésped, y se vuelve a revisar la
 * disponibilidad justo antes de confirmar (CA-074). La reserva queda
 * CONFIRMADA de inmediato (CA-075) con el monto exacto de la cotización
 * (CA-070, CA-086), y sus fechas dejan de estar disponibles (CA-076).
 * </p>
 * <p>
 * En Neon, la restricción {@code ex_reserva_sin_cruces} es la última defensa:
 * si aun así dos reservas confirmadas se cruzan, la base de datos rechaza la
 * segunda y la API responde 409.
 * </p>
 * <p>
 * La auditoría se registra después de confirmar la transacción. Así no pide
 * una segunda conexión mientras retiene los bloqueos, y un evento EXITOSO
 * solo queda si la reserva de verdad se guardó.
 * </p>
 * <p>
 * El pago (HU-27) y la idempotencia con {@code Idempotency-Key} (CA-140,
 * CA-141) llegan con sus historias.
 * </p>
 */
@Service
public class ReservaService {

	/** Mensaje cuando falta la cotización. */
	public static final String FALTA_COTIZACION = "Debes indicar la cotización a reservar.";

	/** Mensaje cuando la cotización no existe o es de otro usuario. */
	public static final String COTIZACION_NO_EXISTE = "La cotización no existe.";

	/** Mensaje cuando la cotización venció o ya se usó. */
	public static final String COTIZACION_NO_VIGENTE = "La cotización venció o ya se usó; solicita una cotización "
			+ "nueva.";

	/** Mensaje cuando el huésped intenta reservar su propio alojamiento. */
	public static final String PROPIO_ALOJAMIENTO = "No puedes reservar tu propio alojamiento.";

	/** Mensaje cuando, al revalidar, las fechas ya no están libres (CA-072, CA-074). */
	public static final String PERIODO_TOMADO = "El período ya no está disponible: otra reserva lo tomó.";

	/** Restricción de Neon que impide dos reservas confirmadas con fechas cruzadas. */
	public static final String RESTRICCION_CRUCE_RESERVAS = "ex_reserva_sin_cruces";

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Repositorio de cotizaciones. */
	private final CotizacionRepository cotizacionRepo;

	/** Repositorio de reservas. */
	private final ReservaRepository reservaRepo;

	/** Revalida la disponibilidad. */
	private final CalendarioService calendarioService;

	/** Convierte la cotización para congelar el desglose. */
	private final CotizacionService cotizacionService;

	/** Deja en la auditoría cada intento de reserva (HU-26). */
	private final AuditoriaService auditoriaService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/** Guarda el desglose en JSON. */
	private final ObjectMapper objectMapper;

	/**
	 * Transacción de la reserva. Se maneja aparte para auditar después de
	 * confirmar, cuando ya se liberaron los bloqueos.
	 */
	private final TransactionTemplate transaccion;

	/**
	 * Crea el servicio.
	 *
	 * @param alojamientoRepo    repositorio de alojamientos
	 * @param cotizacionRepo     repositorio de cotizaciones
	 * @param reservaRepo        repositorio de reservas
	 * @param calendarioService  servicio de calendario
	 * @param cotizacionService  servicio de cotizaciones
	 * @param auditoriaService   servicio de auditoría
	 * @param modelMapper        copiador de entidades a DTO
	 * @param objectMapper       conversor a JSON
	 * @param transactionManager administrador de transacciones de Spring
	 */
	public ReservaService(AlojamientoRepository alojamientoRepo, CotizacionRepository cotizacionRepo,
			ReservaRepository reservaRepo, CalendarioService calendarioService, CotizacionService cotizacionService,
			AuditoriaService auditoriaService, ModelMapper modelMapper, ObjectMapper objectMapper,
			PlatformTransactionManager transactionManager) {
		this.transaccion = new TransactionTemplate(transactionManager);
		this.alojamientoRepo = alojamientoRepo;
		this.cotizacionRepo = cotizacionRepo;
		this.reservaRepo = reservaRepo;
		this.calendarioService = calendarioService;
		this.cotizacionService = cotizacionService;
		this.auditoriaService = auditoriaService;
		this.modelMapper = modelMapper;
		this.objectMapper = objectMapper;
	}

	/**
	 * Crea una reserva desde una cotización vigente.
	 *
	 * @param solicitud cotización a reservar
	 * @param idHuesped huésped autenticado
	 * @return la reserva CONFIRMADA; DATOS_INVALIDOS si falta la cotización o
	 *         sus fechas ya no son válidas; NO_ENCONTRADO si la cotización o el
	 *         alojamiento no existe, o la cotización es de otro usuario;
	 *         SIN_PERMISO si es su propio alojamiento; ESTADO_INVALIDO si la
	 *         cotización venció o ya se usó, o el alojamiento no está ACTIVA;
	 *         NO_DISPONIBLE si otra reserva tomó las fechas
	 */
	public Resultado<ReservaDTO> crear(SolicitudReservaDTO solicitud, Long idHuesped) {
		Resultado<Reserva> creada;
		try {
			creada = transaccion.execute(estado -> reservar(solicitud, idHuesped));
		} catch (DataIntegrityViolationException e) {
			if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains(RESTRICCION_CRUCE_RESERVAS)) {
				throw e;
			}
			creada = Resultado.error(Resultado.NO_DISPONIBLE, PERIODO_TOMADO);
		}
		if (creada == null || !creada.esExito()) {
			String mensaje = creada == null ? PERIODO_TOMADO : creada.getMensaje();
			auditoriaService.registrar(idHuesped, AuditoriaService.RESERVA_CREADA, AuditoriaService.ENTIDAD_RESERVA,
					null, ResultadoAuditoria.RECHAZADO, mensaje);
			return Resultado.error(creada == null ? Resultado.NO_DISPONIBLE : creada.getCodigo(), mensaje);
		}

		Reserva reserva = creada.getDato();
		auditoriaService.registrar(idHuesped, AuditoriaService.RESERVA_CREADA, AuditoriaService.ENTIDAD_RESERVA,
				reserva.getIdReserva(), ResultadoAuditoria.EXITOSO, "Del " + reserva.getFechaEntrada() + " al "
						+ reserva.getFechaSalida() + ", total " + reserva.getTotal());
		ReservaDTO dto = modelMapper.map(reserva, ReservaDTO.class);
		alojamientoRepo.findById(reserva.getIdAlojamiento()).ifPresent(a -> dto.setTituloAlojamiento(a.getTitulo()));
		return Resultado.exito(dto);
	}

	/**
	 * Paso transaccional: valida con los bloqueos puestos, crea la reserva y
	 * marca la cotización como usada. Al confirmarse la transacción se liberan
	 * los bloqueos.
	 *
	 * @param solicitud cotización a reservar
	 * @param idHuesped huésped autenticado
	 * @return la reserva guardada, o el error de validación
	 */
	private Resultado<Reserva> reservar(SolicitudReservaDTO solicitud, Long idHuesped) {
		Resultado<Cotizacion> validada = validar(solicitud, idHuesped);
		if (!validada.esExito()) {
			return Resultado.error(validada.getCodigo(), validada.getMensaje());
		}
		Cotizacion cotizacion = validada.getDato();

		Reserva reserva = new Reserva();
		reserva.setIdHuesped(idHuesped);
		reserva.setIdAlojamiento(cotizacion.getIdAlojamiento());
		reserva.setIdCotizacion(cotizacion.getIdCotizacion());
		reserva.setFechaEntrada(cotizacion.getFechaEntrada());
		reserva.setFechaSalida(cotizacion.getFechaSalida());
		reserva.setNoches(cotizacion.getNoches());
		reserva.setHuespedes(cotizacion.getHuespedes());
		reserva.setTotal(cotizacion.getTotal());
		reserva.setDesglose(objectMapper.writeValueAsString(cotizacionService.convertirDTO(cotizacion)));
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva.setFechaCreacion(LocalDateTime.now());
		// saveAndFlush: si la restricción de Neon rechaza el cruce, falla aquí y no al confirmar
		reserva = reservaRepo.saveAndFlush(reserva);

		cotizacion.setIdReserva(reserva.getIdReserva());
		cotizacionRepo.save(cotizacion);
		return Resultado.exito(reserva);
	}

	/**
	 * Bloquea la cotización y el alojamiento y revisa que se pueda reservar. El
	 * orden de los bloqueos es siempre el mismo (cotización y luego
	 * alojamiento), así dos peticiones no se bloquean entre sí.
	 *
	 * @param solicitud cotización a reservar
	 * @param idHuesped huésped autenticado
	 * @return la cotización bloqueada y lista para reservar, o el error
	 */
	private Resultado<Cotizacion> validar(SolicitudReservaDTO solicitud, Long idHuesped) {
		if (solicitud == null || solicitud.getIdCotizacion() == null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, FALTA_COTIZACION);
		}
		Optional<Cotizacion> cotizacionOpt = cotizacionRepo.buscarParaReservar(solicitud.getIdCotizacion())
				.filter(c -> Objects.equals(c.getIdUsuario(), idHuesped));
		if (cotizacionOpt.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, COTIZACION_NO_EXISTE);
		}
		Cotizacion cotizacion = cotizacionOpt.get();
		Optional<Alojamiento> alojamientoOpt = alojamientoRepo.buscarParaReservar(cotizacion.getIdAlojamiento());
		if (alojamientoOpt.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, CalendarioService.NO_EXISTE);
		}
		Alojamiento alojamiento = alojamientoOpt.get();

		if (cotizacion.getIdReserva() != null || !cotizacion.getFechaExpiracion().isAfter(LocalDateTime.now())) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, COTIZACION_NO_VIGENTE);
		}
		if (alojamiento.getEstado() != EstadoAlojamiento.ACTIVA) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, CalendarioService.noActiva(alojamiento));
		}
		if (Objects.equals(alojamiento.getIdAnfitrion(), idHuesped)) {
			return Resultado.error(Resultado.SIN_PERMISO, PROPIO_ALOJAMIENTO);
		}
		String errorFechas = CalendarioService.validarFechas(cotizacion.getFechaEntrada(),
				cotizacion.getFechaSalida());
		if (errorFechas != null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, errorFechas);
		}
		// Revalidación con el bloqueo puesto: otra reserva pudo tomar las fechas después de cotizar
		if (!calendarioService.estaLibre(alojamiento.getIdAlojamiento(), cotizacion.getFechaEntrada(),
				cotizacion.getFechaSalida())) {
			return Resultado.error(Resultado.NO_DISPONIBLE, PERIODO_TOMADO);
		}
		return Resultado.exito(cotizacion);
	}
}
