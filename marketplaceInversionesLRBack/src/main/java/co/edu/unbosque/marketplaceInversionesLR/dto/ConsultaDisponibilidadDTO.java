package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de la consulta de disponibilidad para unas fechas (HU-21, esquema
 * {@code ConsultaDisponibilidad} de {@code docs/openapi/disponibilidad-busqueda.yaml}).
 * La estancia es [fechaEntrada, fechaSalida): el día de salida no es noche.
 */
@Getter
@Setter
@NoArgsConstructor
public class ConsultaDisponibilidadDTO {

	/** Alojamiento consultado. */
	private Long idAlojamiento;

	/** Día de llegada consultado. */
	private LocalDate fechaEntrada;

	/** Día de salida consultado. */
	private LocalDate fechaSalida;

	/** {@code true} si se puede reservar en esas fechas (CA-119). */
	@Schema(description = "true si está libre (CA-119); false si incluye fechas reservadas o no disponibles (CA-120)")
	private Boolean disponible;

	/** Motivo cuando no está disponible; {@code null} si lo está. */
	@Schema(description = "Motivo cuando no está disponible")
	private String motivo;
}
