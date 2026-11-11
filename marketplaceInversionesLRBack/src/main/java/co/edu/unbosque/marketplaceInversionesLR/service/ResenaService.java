package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaEntradaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResumenResenasDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Reseñas y calificaciones de los alojamientos (HU-03, RF-004) y su moderación
 * por el administrador (HU-14, RF-016).
 * <p>
 * Solo el huésped de una reserva completada (CONFIRMADA y con la salida ya
 * alcanzada) puede reseñarla, una vez, con una calificación de 1 a 5 (CA-016).
 * La reseña queda asociada a huésped, reserva, alojamiento, fecha y contenido;
 * el autor sale del token y el alojamiento de la reserva, nunca del cliente
 * (CA-018). Se notifica al anfitrión y queda en la auditoría.
 * </p>
 * <p>
 * La consulta de un alojamiento muestra solo sus reseñas PUBLICADA (CA-019),
 * igual que la del anfitrión; el huésped ve todas las suyas con su estado,
 * incluidas las que ocultó un administrador.
 * </p>
 * <p>
 * Guardar no se hace dentro de una transacción del servicio: si dos peticiones
 * reseñan la misma reserva a la vez, la restricción {@code uq_resena_reserva}
 * rechaza la segunda y se responde 409 en lugar de un error de transacción.
 * </p>
 */
@Service
public class ResenaService {

	/** Calificación mínima. */
	public static final int CALIFICACION_MINIMA = 1;

	/** Calificación máxima. */
	public static final int CALIFICACION_MAXIMA = 5;

	/** Mensaje cuando falta la reserva o la calificación no es válida. */
	public static final String DATOS_INVALIDOS = "Indica la reserva y una calificación entre 1 y 5.";

	/** Mensaje cuando el comentario es demasiado largo. */
	public static final String COMENTARIO_LARGO = "El comentario no puede superar 2000 caracteres.";

	/** Mensaje cuando la reserva no está completada (CA-016). */
	public static final String NO_COMPLETADA = "Solo puedes reseñar una estancia completada.";

	/** Mensaje cuando la reserva ya tiene reseña. */
	public static final String YA_RESENADA = "Ya registraste una reseña para esta reserva.";

	/** Mensaje cuando la reseña no existe (HU-14). */
	public static final String NO_EXISTE = "La reseña no existe.";

	/** Mensaje cuando falta el motivo para ocultar (HU-14, CA-066). */
	public static final String MOTIVO_OBLIGATORIO = "El motivo para ocultar la reseña es obligatorio.";

	/** Inicio del aviso al autor de una reseña ocultada; siguen la reserva y el motivo. */
	public static final String AVISO_OCULTA = "Un administrador ocultó tu reseña de la reserva ";

	/** Repositorio de reseñas. */
	private final ResenaRepository resenaRepo;

	/** Repositorio de reservas. */
	private final ReservaRepository reservaRepo;

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Nombres de los autores. */
	private final UsuarioRepository usuarioRepo;

	/** Avisa al anfitrión. */
	private final NotificacionService notificacionService;

	/** Deja en la auditoría cada intento de reseña. */
	private final AuditoriaService auditoriaService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/**
	 * Crea el servicio.
	 *
	 * @param resenaRepo          repositorio de reseñas
	 * @param reservaRepo         repositorio de reservas
	 * @param alojamientoRepo     repositorio de alojamientos
	 * @param usuarioRepo         repositorio de usuarios
	 * @param notificacionService servicio de notificaciones
	 * @param auditoriaService    servicio de auditoría
	 * @param modelMapper         copiador de entidades a DTO
	 */
	public ResenaService(ResenaRepository resenaRepo, ReservaRepository reservaRepo,
			AlojamientoRepository alojamientoRepo, UsuarioRepository usuarioRepo,
			NotificacionService notificacionService, AuditoriaService auditoriaService, ModelMapper modelMapper) {
		this.resenaRepo = resenaRepo;
		this.reservaRepo = reservaRepo;
		this.alojamientoRepo = alojamientoRepo;
		this.usuarioRepo = usuarioRepo;
		this.notificacionService = notificacionService;
		this.auditoriaService = auditoriaService;
		this.modelMapper = modelMapper;
	}

	/**
	 * Registra la reseña del huésped sobre una reserva propia completada y
	 * avisa al anfitrión.
	 *
	 * @param datos     reserva, calificación y comentario
	 * @param idHuesped huésped autenticado
	 * @return la reseña PUBLICADA; DATOS_INVALIDOS si falta la reserva, la
	 *         calificación no está entre 1 y 5 o el comentario es muy largo;
	 *         NO_ENCONTRADO si la reserva no existe o es de otro huésped;
	 *         ESTADO_INVALIDO si no está completada o ya tiene reseña
	 */
	public Resultado<ResenaDTO> crear(ResenaEntradaDTO datos, Long idHuesped) {
		if (datos == null || datos.getIdReserva() == null || datos.getCalificacion() == null
				|| datos.getCalificacion() < CALIFICACION_MINIMA || datos.getCalificacion() > CALIFICACION_MAXIMA) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, DATOS_INVALIDOS);
		}
		String comentario = datos.getComentario() == null || datos.getComentario().isBlank() ? null
				: datos.getComentario().trim();
		if (LimitesTexto.excede(comentario, LimitesTexto.LARGO)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, COMENTARIO_LARGO);
		}
		Optional<Reserva> reserva = reservaRepo.findById(datos.getIdReserva())
				.filter(r -> Objects.equals(r.getIdHuesped(), idHuesped));
		if (reserva.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, ReservaService.NO_EXISTE);
		}
		if (!ReservaService.esCompletada(reserva.get())) {
			return rechazar(idHuesped, NO_COMPLETADA);
		}
		if (resenaRepo.existsByIdReserva(datos.getIdReserva())) {
			return rechazar(idHuesped, YA_RESENADA);
		}

		Resena resena = new Resena();
		resena.setIdReserva(reserva.get().getIdReserva());
		resena.setIdAlojamiento(reserva.get().getIdAlojamiento());
		resena.setIdUsuario(idHuesped);
		resena.setCalificacion(datos.getCalificacion());
		resena.setComentario(comentario);
		resena.setEstado(EstadoResena.PUBLICADA);
		resena.setFecha(LocalDateTime.now());
		try {
			resena = resenaRepo.saveAndFlush(resena);
		} catch (DataIntegrityViolationException e) {
			// Otra petición la reseñó al mismo tiempo; cualquier otra violación es un error real
			if (!resenaRepo.existsByIdReserva(datos.getIdReserva())) {
				throw e;
			}
			return rechazar(idHuesped, YA_RESENADA);
		}

		Integer calificacion = resena.getCalificacion();
		alojamientoRepo.findById(resena.getIdAlojamiento())
				.ifPresent(a -> notificacionService.enviar(a.getIdAnfitrion(), NotificacionService.RESENA_RECIBIDA,
						"Recibiste una reseña de " + calificacion + (calificacion == 1 ? " estrella" : " estrellas")
								+ " en \"" + a.getTitulo() + "\"."));
		auditoriaService.registrar(idHuesped, AuditoriaService.RESENA_CREADA, AuditoriaService.ENTIDAD_RESENA,
				resena.getIdResena(), ResultadoAuditoria.EXITOSO,
				"Reserva " + resena.getIdReserva() + ", calificación " + calificacion);
		return Resultado.exito(convertirDTO(resena));
	}

	/**
	 * Reseñas PUBLICADA de un alojamiento con su promedio y cantidad (CA-019).
	 *
	 * @param idAlojamiento alojamiento
	 * @param pagina        página y orden
	 * @return el resumen; NO_ENCONTRADO si el alojamiento no existe
	 */
	@Transactional(readOnly = true)
	public Resultado<ResumenResenasDTO> resumen(Long idAlojamiento, Pageable pagina) {
		if (idAlojamiento == null || !alojamientoRepo.existsById(idAlojamiento)) {
			return Resultado.error(Resultado.NO_ENCONTRADO, CalendarioService.NO_EXISTE);
		}
		return Resultado.exito(resumenDeAlojamiento(idAlojamiento, pagina));
	}

	/**
	 * Reseñas que escribió el huésped, en cualquier estado.
	 *
	 * @param idHuesped huésped autenticado
	 * @param pagina    página y orden
	 * @return página de reseñas; vacía si no tiene
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<ResenaDTO> listarMias(Long idHuesped, Pageable pagina) {
		return PaginaDTO.de(resenaRepo.findByIdUsuario(idHuesped, pagina), this::convertirDTO);
	}

	/**
	 * Reseñas PUBLICADA de los alojamientos del anfitrión con su promedio y
	 * cantidad. Con un alojamiento, solo las de ese, que debe ser suyo.
	 *
	 * @param idAnfitrion   anfitrión autenticado
	 * @param idAlojamiento alojamiento a filtrar; {@code null} para todos
	 * @param pagina        página y orden
	 * @return el resumen; NO_ENCONTRADO si el alojamiento no existe o es de
	 *         otro anfitrión
	 */
	@Transactional(readOnly = true)
	public Resultado<ResumenResenasDTO> resumenDeAnfitrion(Long idAnfitrion, Long idAlojamiento, Pageable pagina) {
		if (idAlojamiento != null) {
			boolean propio = alojamientoRepo.findById(idAlojamiento)
					.filter(a -> Objects.equals(a.getIdAnfitrion(), idAnfitrion)).isPresent();
			return propio ? Resultado.exito(resumenDeAlojamiento(idAlojamiento, pagina))
					: Resultado.error(Resultado.NO_ENCONTRADO, CalendarioService.NO_EXISTE);
		}
		return Resultado.exito(armarResumen(null,
				resenaRepo.promedioDeAnfitrion(idAnfitrion, EstadoResena.PUBLICADA),
				resenaRepo.contarDeAnfitrion(idAnfitrion, EstadoResena.PUBLICADA),
				resenaRepo.buscarDeAnfitrion(idAnfitrion, EstadoResena.PUBLICADA, pagina)));
	}

	/**
	 * Todas las reseñas, o solo las de un estado, para moderarlas (HU-14).
	 *
	 * @param estado estado a filtrar; {@code null} para todas
	 * @param pagina página y orden
	 * @return página de reseñas
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<ResenaDTO> listarTodas(EstadoResena estado, Pageable pagina) {
		Page<Resena> resenas = estado == null ? resenaRepo.findAll(pagina) : resenaRepo.findByEstado(estado, pagina);
		return PaginaDTO.de(resenas, this::convertirDTO);
	}

	/**
	 * Oculta una reseña con un motivo o la vuelve a mostrar (administrador,
	 * HU-14). No la borra: solo cambia su estado, así deja de mostrarse en las
	 * consultas públicas y en el promedio (CA-066). Al ocultarla se avisa al
	 * autor con el motivo. Cada cambio queda en la auditoría para la
	 * trazabilidad; si la reseña ya estaba en ese estado no se repiten el aviso
	 * ni la auditoría.
	 *
	 * @param idResena reseña
	 * @param ocultar  {@code true} para ocultar, {@code false} para mostrar
	 * @param motivo   motivo, obligatorio al ocultar
	 * @param idAdmin  administrador que modera
	 * @return la reseña con su estado nuevo; DATOS_INVALIDOS si al ocultar
	 *         falta el motivo o es muy largo; NO_ENCONTRADO si no existe
	 */
	@Transactional
	public Resultado<ResenaDTO> moderar(Long idResena, boolean ocultar, String motivo, Long idAdmin) {
		if (ocultar && (motivo == null || motivo.isBlank())) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, MOTIVO_OBLIGATORIO);
		}
		if (ocultar && LimitesTexto.excede(motivo, LimitesTexto.MOTIVO)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, LimitesTexto.MOTIVO_LARGO);
		}
		Optional<Resena> encontrada = idResena == null ? Optional.empty() : resenaRepo.findById(idResena);
		if (encontrada.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Resena resena = encontrada.get();
		EstadoResena nuevo = ocultar ? EstadoResena.OCULTA : EstadoResena.PUBLICADA;
		if (resena.getEstado() == nuevo) {
			return Resultado.exito(convertirDTO(resena));
		}

		resenaRepo.cambiarEstado(resena.getIdResena(), nuevo);
		resena.setEstado(nuevo);
		String detalle;
		if (ocultar) {
			String limpio = motivo.trim();
			notificacionService.enviar(resena.getIdUsuario(), NotificacionService.RESENA_OCULTA,
					AVISO_OCULTA + resena.getIdReserva() + ". Motivo: " + limpio);
			detalle = "Ocultada: " + limpio;
		} else {
			detalle = "Visible de nuevo";
		}
		auditoriaService.registrar(idAdmin, AuditoriaService.RESENA_MODERADA, AuditoriaService.ENTIDAD_RESENA,
				resena.getIdResena(), ResultadoAuditoria.EXITOSO, detalle);
		return Resultado.exito(convertirDTO(resena));
	}

	/**
	 * Resumen de las reseñas PUBLICADA de un alojamiento.
	 *
	 * @param idAlojamiento alojamiento
	 * @param pagina        página y orden
	 * @return el resumen
	 */
	private ResumenResenasDTO resumenDeAlojamiento(Long idAlojamiento, Pageable pagina) {
		return armarResumen(idAlojamiento, resenaRepo.promedio(idAlojamiento, EstadoResena.PUBLICADA),
				resenaRepo.countByIdAlojamientoAndEstado(idAlojamiento, EstadoResena.PUBLICADA),
				resenaRepo.findByIdAlojamientoAndEstado(idAlojamiento, EstadoResena.PUBLICADA, pagina));
	}

	/**
	 * Arma el resumen con el promedio redondeado a una cifra decimal.
	 *
	 * @param idAlojamiento alojamiento; {@code null} si son las de un anfitrión
	 * @param promedio      promedio; {@code null} si no hay reseñas
	 * @param cantidad      cantidad de reseñas
	 * @param resenas       página de reseñas
	 * @return el resumen
	 */
	private ResumenResenasDTO armarResumen(Long idAlojamiento, Double promedio, long cantidad, Page<Resena> resenas) {
		ResumenResenasDTO resumen = new ResumenResenasDTO();
		resumen.setIdAlojamiento(idAlojamiento);
		resumen.setPromedio(promedio == null ? null
				: BigDecimal.valueOf(promedio).setScale(1, RoundingMode.HALF_UP).doubleValue());
		resumen.setCantidad(cantidad);
		resumen.setResenas(PaginaDTO.de(resenas, this::convertirDTO));
		return resumen;
	}

	/**
	 * Convierte la reseña a DTO con el nombre del autor y el título del
	 * alojamiento.
	 *
	 * @param resena reseña
	 * @return reseña para la API
	 */
	private ResenaDTO convertirDTO(Resena resena) {
		ResenaDTO dto = modelMapper.map(resena, ResenaDTO.class);
		usuarioRepo.findById(resena.getIdUsuario()).map(Usuario::getNombre).ifPresent(dto::setNombreUsuario);
		alojamientoRepo.findById(resena.getIdAlojamiento()).ifPresent(a -> dto.setTituloAlojamiento(a.getTitulo()));
		return dto;
	}

	/**
	 * Audita un intento de reseña rechazado y devuelve el error.
	 *
	 * @param idHuesped huésped que intentó reseñar
	 * @param mensaje   causa del rechazo
	 * @return error ESTADO_INVALIDO
	 */
	private Resultado<ResenaDTO> rechazar(Long idHuesped, String mensaje) {
		auditoriaService.registrar(idHuesped, AuditoriaService.RESENA_CREADA, AuditoriaService.ENTIDAD_RESENA, null,
				ResultadoAuditoria.RECHAZADO, mensaje);
		return Resultado.error(Resultado.ESTADO_INVALIDO, mensaje);
	}
}
