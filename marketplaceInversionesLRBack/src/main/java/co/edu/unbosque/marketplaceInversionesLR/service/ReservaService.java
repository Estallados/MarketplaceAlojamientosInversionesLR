package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.unbosque.marketplaceInversionesLR.dto.CancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PagoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.SolicitudReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.ObjectMapper;

/**
 * Creación de reservas con pago de prueba y bloqueo automático de fechas
 * (HU-22).
 * <p>
 * Todo pasa en una transacción. Se bloquean la cotización y el alojamiento,
 * así dos reservas simultáneas sobre las mismas fechas se procesan una después
 * de la otra (CA-072). Con el bloqueo puesto se valida la cotización (vigente,
 * del huésped y sin usar), que el alojamiento esté ACTIVA (CA-071) y no sea
 * del propio huésped, y se vuelve a revisar la disponibilidad (CA-074).
 * </p>
 * <p>
 * Luego se sigue el flujo que exige Neon: la reserva nace en PENDIENTE_PAGO con
 * el monto exacto de la cotización (CA-070, CA-086), se registra el pago de
 * prueba y, si se aprueba, la reserva pasa a CONFIRMADA (CA-075) y sus fechas
 * dejan de estar disponibles (CA-076). Si el pago se rechaza o falla, la
 * reserva queda CANCELADA, la cotización sigue libre para intentar con otro
 * método y la API responde 402 o 502.
 * </p>
 * <p>
 * En Neon, la restricción {@code ex_reserva_sin_cruces} es la última defensa:
 * se aplica al pasar a CONFIRMADA y, si dos reservas se cruzan, rechaza la
 * segunda y la API responde 409.
 * </p>
 * <p>
 * La auditoría se registra después de confirmar la transacción. Así no pide
 * una segunda conexión mientras retiene los bloqueos, y un evento EXITOSO
 * solo queda si la reserva de verdad se guardó.
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

	/** Inicio del mensaje del 402. */
	public static final String PAGO_RECHAZADO = "El pago fue rechazado: ";

	/** Inicio del mensaje del 502. */
	public static final String PAGO_NO_CONFIRMADO = "No se pudo confirmar el pago: ";

	/** Final de los mensajes del 402 y el 502. */
	public static final String SIN_PAGAR = " La reserva no quedó pagada.";

	/** Restricción de Neon que impide dos reservas confirmadas con fechas cruzadas. */
	public static final String RESTRICCION_CRUCE_RESERVAS = "ex_reserva_sin_cruces";

	/** Mensaje cuando la reserva no existe o el usuario no puede verla. */
	public static final String NO_EXISTE = "La reserva no existe.";

	/**
	 * Reserva y pago que deja la transacción.
	 *
	 * @param reserva reserva CONFIRMADA o CANCELADA
	 * @param pago    pago de prueba con su resultado
	 */
	private record Creada(Reserva reserva, Pago pago) {
	}

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

	/** Hace el pago de prueba. */
	private final PagoService pagoService;

	/** Avisa al huésped y al anfitrión el resultado. */
	private final NotificacionService notificacionService;

	/** Deja en la auditoría cada intento de reserva (HU-26). */
	private final AuditoriaService auditoriaService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/** Guarda el desglose en JSON. */
	private final ObjectMapper objectMapper;

	/** Nombres del huésped y del anfitrión (HU-25). */
	private final UsuarioRepository usuarioRepo;

	/** Nombre de la política del alojamiento (HU-25). */
	private final PoliticaCancelacionRepository politicaRepo;

	/** Si la reserva ya tiene reseña (HU-25). */
	private final ResenaRepository resenaRepo;

	/** Cancelación de la reserva, si la hubo (HU-25). */
	private final CancelacionRepository cancelacionRepo;

	/** Imagen principal del alojamiento (HU-25). */
	private final PublicacionService publicacionService;

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
	 * @param pagoService         servicio de pagos de prueba
	 * @param notificacionService servicio de notificaciones
	 * @param auditoriaService    servicio de auditoría
	 * @param modelMapper         copiador de entidades a DTO
	 * @param objectMapper        conversor a JSON
	 * @param transactionManager  administrador de transacciones de Spring
	 * @param usuarioRepo         nombres del huésped y del anfitrión
	 * @param politicaRepo        políticas de cancelación
	 * @param resenaRepo          reseñas
	 * @param cancelacionRepo     cancelaciones
	 * @param publicacionService  imágenes del alojamiento
	 */
	public ReservaService(AlojamientoRepository alojamientoRepo, CotizacionRepository cotizacionRepo,
			ReservaRepository reservaRepo, CalendarioService calendarioService, CotizacionService cotizacionService,
			PagoService pagoService, NotificacionService notificacionService, AuditoriaService auditoriaService,
			ModelMapper modelMapper, ObjectMapper objectMapper, PlatformTransactionManager transactionManager,
			UsuarioRepository usuarioRepo, PoliticaCancelacionRepository politicaRepo, ResenaRepository resenaRepo,
			CancelacionRepository cancelacionRepo, PublicacionService publicacionService) {
		this.transaccion = new TransactionTemplate(transactionManager);
		this.usuarioRepo = usuarioRepo;
		this.politicaRepo = politicaRepo;
		this.resenaRepo = resenaRepo;
		this.cancelacionRepo = cancelacionRepo;
		this.publicacionService = publicacionService;
		this.alojamientoRepo = alojamientoRepo;
		this.cotizacionRepo = cotizacionRepo;
		this.reservaRepo = reservaRepo;
		this.calendarioService = calendarioService;
		this.cotizacionService = cotizacionService;
		this.pagoService = pagoService;
		this.notificacionService = notificacionService;
		this.auditoriaService = auditoriaService;
		this.modelMapper = modelMapper;
		this.objectMapper = objectMapper;
	}

	/**
	 * Crea una reserva desde una cotización vigente y la paga.
	 *
	 * @param solicitud cotización a reservar y método de pago
	 * @param idHuesped huésped autenticado
	 * @return la reserva CONFIRMADA con su pago; DATOS_INVALIDOS si falta la
	 *         cotización, el método de pago no es válido o las fechas ya no son
	 *         válidas; NO_ENCONTRADO si la cotización o el alojamiento no
	 *         existe, o la cotización es de otro usuario; SIN_PERMISO si es su
	 *         propio alojamiento; ESTADO_INVALIDO si la cotización venció o ya
	 *         se usó, o el alojamiento no está ACTIVA; NO_DISPONIBLE si otra
	 *         reserva tomó las fechas; PAGO_RECHAZADO o PAGO_ERROR si el pago no
	 *         se aprobó, con la reserva CANCELADA como dato
	 */
	public Resultado<ReservaDTO> crear(SolicitudReservaDTO solicitud, Long idHuesped) {
		// Igual que el proyecto base: primero falta la cotización, luego el método de pago
		if (solicitud != null && solicitud.getIdCotizacion() != null
				&& !PagoService.metodoValido(solicitud.getMetodoPago())) {
			return rechazar(idHuesped, Resultado.DATOS_INVALIDOS, PagoService.METODO_INVALIDO);
		}
		Resultado<Creada> creada;
		try {
			creada = transaccion.execute(estado -> reservar(solicitud, idHuesped));
		} catch (DataIntegrityViolationException e) {
			if (!String.valueOf(e.getMostSpecificCause().getMessage()).contains(RESTRICCION_CRUCE_RESERVAS)) {
				throw e;
			}
			creada = Resultado.error(Resultado.NO_DISPONIBLE, PERIODO_TOMADO);
		}
		if (creada == null) {
			return rechazar(idHuesped, Resultado.NO_DISPONIBLE, PERIODO_TOMADO);
		}
		if (!creada.esExito()) {
			return rechazar(idHuesped, creada.getCodigo(), creada.getMensaje());
		}

		Reserva reserva = creada.getDato().reserva();
		Pago pago = creada.getDato().pago();
		ReservaDTO dto = convertirDTO(reserva, pago);
		if (pago.getEstado() == EstadoPago.APROBADO) {
			auditoriaService.registrar(idHuesped, AuditoriaService.PAGO, AuditoriaService.ENTIDAD_RESERVA,
					reserva.getIdReserva(), ResultadoAuditoria.EXITOSO, "Referencia " + pago.getReferencia());
			auditoriaService.registrar(idHuesped, AuditoriaService.RESERVA_CREADA, AuditoriaService.ENTIDAD_RESERVA,
					reserva.getIdReserva(), ResultadoAuditoria.EXITOSO, "Del " + reserva.getFechaEntrada() + " al "
							+ reserva.getFechaSalida() + ", total " + reserva.getTotal());
			return Resultado.exito(dto);
		}

		boolean rechazado = pago.getEstado() == EstadoPago.RECHAZADO;
		String mensaje = (rechazado ? PAGO_RECHAZADO : PAGO_NO_CONFIRMADO) + pago.getDetalle() + SIN_PAGAR;
		auditoriaService.registrar(idHuesped, AuditoriaService.PAGO, AuditoriaService.ENTIDAD_RESERVA,
				reserva.getIdReserva(), rechazado ? ResultadoAuditoria.RECHAZADO : ResultadoAuditoria.ERROR,
				pago.getDetalle());
		auditoriaService.registrar(idHuesped, AuditoriaService.RESERVA_CREADA, AuditoriaService.ENTIDAD_RESERVA,
				reserva.getIdReserva(), ResultadoAuditoria.RECHAZADO, mensaje);
		return Resultado.error(rechazado ? Resultado.PAGO_RECHAZADO : Resultado.PAGO_ERROR, mensaje, dto);
	}

	/**
	 * Audita un intento de reserva que no llegó a crearse y devuelve el error.
	 *
	 * @param idHuesped huésped autenticado
	 * @param codigo    código del error
	 * @param mensaje   causa
	 * @return el error
	 */
	private Resultado<ReservaDTO> rechazar(Long idHuesped, int codigo, String mensaje) {
		auditoriaService.registrar(idHuesped, AuditoriaService.RESERVA_CREADA, AuditoriaService.ENTIDAD_RESERVA,
				null, ResultadoAuditoria.RECHAZADO, mensaje);
		return Resultado.error(codigo, mensaje);
	}

	/**
	 * Reservas de un huésped en cualquier estado, con su último pago (detalle
	 * de usuario del administrador, HU-12).
	 *
	 * @param idHuesped huésped
	 * @param pagina    página y orden
	 * @return página de reservas; vacía si no tiene
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<ReservaDTO> listarDeHuesped(Long idHuesped, Pageable pagina) {
		return PaginaDTO.de(reservaRepo.findByIdHuesped(idHuesped, pagina), this::convertirDTO);
	}

	/**
	 * Historial del huésped autenticado: todas sus reservas con su estado
	 * actual (HU-25, CA-078). Un usuario sin reservas recibe una página vacía.
	 *
	 * @param idHuesped usuario autenticado
	 * @param pagina    página y orden
	 * @return página de reservas
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<ReservaDTO> listarMias(Long idHuesped, Pageable pagina) {
		return listarDeHuesped(idHuesped, pagina);
	}

	/**
	 * Reservas de los alojamientos del anfitrión autenticado, nunca las de
	 * alojamientos de otros (HU-25, CA-079).
	 *
	 * @param idAnfitrion anfitrión autenticado
	 * @param pagina      página y orden
	 * @return página de reservas
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<ReservaDTO> listarDeAnfitrion(Long idAnfitrion, Pageable pagina) {
		return PaginaDTO.de(reservaRepo.buscarDeAnfitrion(idAnfitrion, pagina), this::convertirDTO);
	}

	/**
	 * Detalle de una reserva con el pago, el alojamiento y la cancelación
	 * (HU-25). La ven su huésped, el anfitrión del alojamiento y los
	 * administradores; para los demás es como si no existiera.
	 *
	 * @param idReserva reserva
	 * @param idActor   usuario autenticado
	 * @param esAdmin   {@code true} si es administrador
	 * @return la reserva; o NO_ENCONTRADO si no existe o el usuario no puede
	 *         verla
	 */
	@Transactional(readOnly = true)
	public Resultado<ReservaDTO> obtener(Long idReserva, Long idActor, boolean esAdmin) {
		Optional<Reserva> reserva = idReserva == null ? Optional.empty() : reservaRepo.findById(idReserva);
		if (reserva.isEmpty() || !puedeVer(reserva.get(), idActor, esAdmin)) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		return Resultado.exito(convertirDTO(reserva.get()));
	}

	/**
	 * Indica si el usuario puede ver la reserva: es administrador, es su
	 * huésped o es el anfitrión del alojamiento.
	 *
	 * @param reserva reserva
	 * @param idActor usuario autenticado
	 * @param esAdmin {@code true} si es administrador
	 * @return {@code true} si la puede ver
	 */
	private boolean puedeVer(Reserva reserva, Long idActor, boolean esAdmin) {
		if (esAdmin || Objects.equals(reserva.getIdHuesped(), idActor)) {
			return true;
		}
		return idActor != null && alojamientoRepo.findById(reserva.getIdAlojamiento())
				.map(a -> idActor.equals(a.getIdAnfitrion())).orElse(false);
	}

	/**
	 * Indica si la reserva ya se completó: está CONFIRMADA y la fecha de salida
	 * ya llegó. Habilita la reseña (CA-016).
	 *
	 * @param reserva reserva
	 * @return {@code true} si está completada
	 */
	public static boolean esCompletada(Reserva reserva) {
		return reserva.getEstado() == EstadoReserva.CONFIRMADA && reserva.getFechaSalida() != null
				&& !reserva.getFechaSalida().isAfter(LocalDate.now());
	}

	/**
	 * Convierte la reserva a DTO con su último pago.
	 *
	 * @param reserva reserva
	 * @return reserva para la API
	 */
	private ReservaDTO convertirDTO(Reserva reserva) {
		return convertirDTO(reserva, pagoService.ultimo(reserva.getIdReserva()).orElse(null));
	}

	/**
	 * Convierte la reserva a DTO con el pago, los datos del alojamiento (aunque
	 * esté pausado o bloqueado), su imagen principal y su política, los
	 * nombres del huésped y del anfitrión, si ya tiene reseña y su
	 * cancelación, si la hubo. Así el frontend arma la lista y el detalle con
	 * una sola llamada.
	 *
	 * @param reserva reserva
	 * @param pago    pago de la reserva; {@code null} si no tiene
	 * @return reserva para la API
	 */
	private ReservaDTO convertirDTO(Reserva reserva, Pago pago) {
		ReservaDTO dto = modelMapper.map(reserva, ReservaDTO.class);
		dto.setPago(pago == null ? null : modelMapper.map(pago, PagoDTO.class));
		dto.setCompletada(esCompletada(reserva));
		alojamientoRepo.findById(reserva.getIdAlojamiento()).ifPresent(a -> {
			dto.setTituloAlojamiento(a.getTitulo());
			dto.setCiudad(a.getCiudad());
			dto.setUbicacion(a.getUbicacion());
			dto.setTipoAlojamiento(a.getTipo());
			dto.setCapacidadAlojamiento(a.getCapacidad());
			dto.setIdAnfitrion(a.getIdAnfitrion());
			dto.setNombreAnfitrion(nombreUsuario(a.getIdAnfitrion()));
			publicacionService.imagenesDTO(a.getIdAlojamiento()).stream().findFirst()
					.ifPresent(i -> dto.setImagenAlojamiento(i.getUrl()));
			String politica = a.getIdPolitica() == null ? null
					: politicaRepo.findById(a.getIdPolitica()).map(PoliticaCancelacion::getNombre).orElse(null);
			dto.setNombrePolitica(politica == null ? CancelacionService.POLITICA_POR_DEFECTO : politica);
		});
		dto.setNombreHuesped(nombreUsuario(reserva.getIdHuesped()));
		dto.setTieneResena(reserva.getIdReserva() != null && resenaRepo.existsByIdReserva(reserva.getIdReserva()));
		if (reserva.getIdReserva() != null) {
			cancelacionRepo.findByIdReserva(reserva.getIdReserva())
					.ifPresent(c -> dto.setCancelacion(modelMapper.map(c, CancelacionDTO.class)));
		}
		return dto;
	}

	/**
	 * Nombre de un usuario.
	 *
	 * @param idUsuario usuario; puede ser {@code null}
	 * @return el nombre, o {@code null} si no existe
	 */
	private String nombreUsuario(Long idUsuario) {
		return idUsuario == null ? null : usuarioRepo.findById(idUsuario).map(Usuario::getNombre).orElse(null);
	}

	/**
	 * Paso transaccional: valida con los bloqueos puestos, crea la reserva en
	 * PENDIENTE_PAGO, registra el pago y la deja CONFIRMADA o CANCELADA según el
	 * resultado. Al confirmarse la transacción se liberan los bloqueos.
	 *
	 * @param solicitud cotización a reservar y método de pago
	 * @param idHuesped huésped autenticado
	 * @return la reserva y su pago, o el error de validación
	 */
	private Resultado<Creada> reservar(SolicitudReservaDTO solicitud, Long idHuesped) {
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
		// Neon solo acepta reservas nuevas en PENDIENTE_PAGO (trg_validar_reserva)
		reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
		reserva.setFechaCreacion(LocalDateTime.now());
		reserva = reservaRepo.saveAndFlush(reserva);

		Pago pago = pagoService.procesar(reserva, solicitud.getMetodoPago());
		if (pago.getEstado() != EstadoPago.APROBADO) {
			// La cotización queda libre para intentar de nuevo con otro método de pago
			reserva.setEstado(EstadoReserva.CANCELADA);
			reserva = reservaRepo.saveAndFlush(reserva);
			notificacionService.enviar(idHuesped, NotificacionService.PAGO_NO_CONFIRMADO,
					"El pago de tu reserva no se confirmó y la reserva no quedó registrada como pagada.");
			return Resultado.exito(new Creada(reserva, pago));
		}

		// Con el pago APROBADO Neon deja confirmarla (trg_reserva_transicion). saveAndFlush: si
		// ex_reserva_sin_cruces rechaza el cruce, falla aquí y se revierte todo, también el pago.
		reserva.setEstado(EstadoReserva.CONFIRMADA);
		reserva = reservaRepo.saveAndFlush(reserva);

		cotizacion.setIdReserva(reserva.getIdReserva());
		cotizacionRepo.save(cotizacion);
		avisarConfirmada(reserva);
		return Resultado.exito(new Creada(reserva, pago));
	}

	/**
	 * Avisa al huésped y al anfitrión que la reserva quedó confirmada. Va en la
	 * misma transacción: si un aviso no se puede guardar, tampoco se confirma.
	 *
	 * @param reserva reserva CONFIRMADA
	 */
	private void avisarConfirmada(Reserva reserva) {
		String fechas = "del " + reserva.getFechaEntrada() + " al " + reserva.getFechaSalida();
		notificacionService.enviar(reserva.getIdHuesped(), NotificacionService.RESERVA_CONFIRMADA,
				"Tu reserva " + reserva.getIdReserva() + " " + fechas + " está confirmada.");
		alojamientoRepo.findById(reserva.getIdAlojamiento()).ifPresent(a -> notificacionService.enviar(
				a.getIdAnfitrion(), NotificacionService.RESERVA_CONFIRMADA,
				"Tienes una reserva confirmada " + fechas + " en " + a.getTitulo() + "."));
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
