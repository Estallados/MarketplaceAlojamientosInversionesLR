package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.NotificacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Notificaciones dentro de la plataforma (HU-02, RF-003). Las demás historias
 * las crean con {@link #enviar} cuando ocurre un evento relevante (CA-010):
 * reservas confirmadas o canceladas, pagos no confirmados, decisiones sobre
 * publicaciones y cuentas restringidas o liberadas. Cada usuario las consulta,
 * las cuenta y las marca como leídas, solo las suyas (CA-013). No hay correo,
 * SMS ni push.
 */
@Service
public class NotificacionService {

	/** El administrador aprobó una publicación (HU-32, CA-158). */
	public static final String ALOJAMIENTO_APROBADO = "ALOJAMIENTO_APROBADO";

	/** El administrador rechazó una publicación (HU-32, CA-195). */
	public static final String ALOJAMIENTO_RECHAZADO = "ALOJAMIENTO_RECHAZADO";

	/** Se confirmó una reserva; la reciben el huésped y el anfitrión (HU-22). */
	public static final String RESERVA_CONFIRMADA = "RESERVA_CONFIRMADA";

	/** El pago de una reserva se rechazó o falló (CA-162). */
	public static final String PAGO_NO_CONFIRMADO = "PAGO_NO_CONFIRMADO";

	/** Una reserva pasó a revisión porque bloquearon el alojamiento (HU-11). */
	public static final String RESERVA_EN_REVISION = "RESERVA_EN_REVISION";

	/** El administrador bloqueó una publicación (HU-11). */
	public static final String ALOJAMIENTO_BLOQUEADO = "ALOJAMIENTO_BLOQUEADO";
	/** Un administrador restringió la cuenta; el mensaje lleva el motivo (HU-12, CA-058). */
	public static final String CUENTA_RESTRINGIDA = "CUENTA_RESTRINGIDA";

	/** Un administrador habilitó de nuevo la cuenta (HU-12). */
	public static final String CUENTA_LIBERADA = "CUENTA_LIBERADA";

	/** El huésped canceló una reserva; la reciben el huésped y el anfitrión (HU-01). */
	public static final String RESERVA_CANCELADA = "RESERVA_CANCELADA";

	/** Mensaje cuando la notificación no existe o es de otro usuario. */
	public static final String NO_EXISTE = "La notificación no existe.";

	/** Repositorio de notificaciones. */
	private final NotificacionRepository notificacionRepo;

	/**
	 * Crea el servicio.
	 *
	 * @param notificacionRepo repositorio de notificaciones
	 */
	public NotificacionService(NotificacionRepository notificacionRepo) {
		this.notificacionRepo = notificacionRepo;
	}

	/**
	 * Registra una notificación sin leer para un usuario.
	 *
	 * @param idUsuario destinatario; si es {@code null} no se hace nada
	 * @param tipo      evento que la origina (una de las constantes de esta clase)
	 * @param mensaje   texto para el usuario; se corta si supera
	 *                  {@value LimitesTexto#MEDIO} caracteres
	 */
	@Transactional
	public void enviar(Long idUsuario, String tipo, String mensaje) {
		if (idUsuario == null) {
			return;
		}
		Notificacion notificacion = new Notificacion();
		notificacion.setIdUsuario(idUsuario);
		notificacion.setTipo(tipo);
		notificacion.setMensaje(LimitesTexto.recortar(mensaje, LimitesTexto.MEDIO));
		notificacion.setFecha(LocalDateTime.now());
		notificacion.setLeida(false);
		notificacionRepo.save(notificacion);
	}

	/**
	 * Notificaciones del usuario autenticado, en el orden de la página (HU-02,
	 * CA-013). Nunca incluye las de otros usuarios.
	 *
	 * @param idUsuario usuario autenticado
	 * @param pagina    página y orden
	 * @return página de notificaciones
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<NotificacionDTO> listar(Long idUsuario, Pageable pagina) {
		return PaginaDTO.de(notificacionRepo.findByIdUsuario(idUsuario, pagina), NotificacionService::convertirDTO);
	}

	/**
	 * Cantidad de notificaciones sin leer del usuario (el contador de la
	 * campana).
	 *
	 * @param idUsuario usuario autenticado
	 * @return cantidad sin leer
	 */
	@Transactional(readOnly = true)
	public long contarNoLeidas(Long idUsuario) {
		return notificacionRepo.countByIdUsuarioAndLeidaFalse(idUsuario);
	}

	/**
	 * Marca como leídas todas las notificaciones del usuario.
	 *
	 * @param idUsuario usuario autenticado
	 * @return cuántas notificaciones cambiaron
	 */
	@Transactional
	public int marcarTodasLeidas(Long idUsuario) {
		return notificacionRepo.marcarTodasLeidas(idUsuario);
	}

	/**
	 * Marca como leída una notificación propia (CA-013). Si ya estaba leída,
	 * la devuelve igual.
	 *
	 * @param idNotificacion notificación a marcar
	 * @param idUsuario      usuario autenticado
	 * @return la notificación; o NO_ENCONTRADO si no existe o es de otro
	 *         usuario
	 */
	@Transactional
	public Resultado<NotificacionDTO> marcarLeida(Long idNotificacion, Long idUsuario) {
		Optional<Notificacion> notificacion = idNotificacion == null ? Optional.empty()
				: notificacionRepo.findByIdNotificacionAndIdUsuario(idNotificacion, idUsuario);
		if (notificacion.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Notificacion propia = notificacion.get();
		if (!Boolean.TRUE.equals(propia.getLeida())) {
			propia.setLeida(true);
			propia = notificacionRepo.save(propia);
		}
		return Resultado.exito(convertirDTO(propia));
	}

	/**
	 * Convierte la entidad a DTO.
	 *
	 * @param notificacion notificación
	 * @return notificación para la API
	 */
	static NotificacionDTO convertirDTO(Notificacion notificacion) {
		NotificacionDTO dto = new NotificacionDTO();
		dto.setIdNotificacion(notificacion.getIdNotificacion());
		dto.setTipo(notificacion.getTipo());
		dto.setMensaje(notificacion.getMensaje());
		dto.setFecha(notificacion.getFecha());
		dto.setLeida(notificacion.getLeida());
		dto.setIdUsuario(notificacion.getIdUsuario());
		return dto;
	}
}
