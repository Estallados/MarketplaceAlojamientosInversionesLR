package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento de la auditoría, tal como lo consulta el administrador (HU-13).
 */
@Getter
@Setter
@NoArgsConstructor
public class EventoAuditoriaDTO {

	/** Identificador del evento. */
	private Long idEvento;

	/** Acción realizada, por ejemplo LOGIN. */
	private String accion;

	/** Momento del evento. */
	private LocalDateTime fecha;

	/** Información adicional del evento. */
	private String detalle;

	/** Usuario que hizo la acción; {@code null} si no se conoce. */
	private Long idUsuario;

	/** Tipo de recurso afectado, por ejemplo USUARIO. */
	private String entidad;

	/** Id del recurso afectado. */
	private Long idEntidad;

	/** Resultado de la operación. */
	private ResultadoAuditoria resultado;
}
