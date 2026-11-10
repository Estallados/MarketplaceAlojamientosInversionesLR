package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Notificación tal como sale por la API (HU-02).
 */
@Getter
@Setter
@NoArgsConstructor
public class NotificacionDTO {

	/** Identificador de la notificación. */
	private Long idNotificacion;

	/** Evento que la originó (CA-010). */
	@Schema(description = "Evento que la originó (CA-010)", allowableValues = { "RESERVA_CONFIRMADA",
			"RESERVA_CANCELADA", "RESERVA_EN_REVISION", "PAGO_NO_CONFIRMADO", "ALOJAMIENTO_APROBADO",
			"ALOJAMIENTO_RECHAZADO", "ALOJAMIENTO_BLOQUEADO", "RESENA_RECIBIDA", "RESENA_OCULTA",
			"CUENTA_RESTRINGIDA", "CUENTA_LIBERADA" }, example = "RESERVA_CONFIRMADA")
	private String tipo;

	/** Texto que ve el usuario. */
	@Schema(example = "Tu reserva 15 del 2026-12-10 al 2026-12-13 está confirmada.")
	private String mensaje;

	/** Momento en que se generó. */
	private LocalDateTime fecha;

	/** Si el usuario ya la leyó. */
	private Boolean leida;

	/** Usuario que la recibe. */
	private Long idUsuario;
}
