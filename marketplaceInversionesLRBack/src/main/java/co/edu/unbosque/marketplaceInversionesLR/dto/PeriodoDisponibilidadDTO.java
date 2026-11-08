package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Período de disponibilidad tal como entra y sale por la API (esquemas
 * {@code PeriodoEntrada} y {@code Periodo} de
 * {@code docs/openapi/disponibilidad-busqueda.yaml}). Al registrar o modificar
 * solo se leen las fechas; el id y el alojamiento salen de la ruta.
 */
@Getter
@Setter
@NoArgsConstructor
public class PeriodoDisponibilidadDTO {

	/** Identificador del período. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long idPeriodo;

	/** Alojamiento al que pertenece. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long idAlojamiento;

	/** Primer día disponible (AAAA-MM-DD). */
	@Schema(example = "2026-12-01")
	private LocalDate fechaInicio;

	/** Último día disponible (AAAA-MM-DD), posterior al inicio. */
	@Schema(example = "2026-12-20")
	private LocalDate fechaFin;
}
