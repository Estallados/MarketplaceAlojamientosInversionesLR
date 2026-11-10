package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;

/**
 * Avisos para los usuarios. Por ahora solo registra la notificación; listarla
 * y marcarla como leída llega con la historia de notificaciones.
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

	/** Un administrador restringió la cuenta; el mensaje lleva el motivo (HU-12, CA-058). */
	public static final String CUENTA_RESTRINGIDA = "CUENTA_RESTRINGIDA";

	/** Un administrador habilitó de nuevo la cuenta (HU-12). */
	public static final String CUENTA_LIBERADA = "CUENTA_LIBERADA";

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
}
