package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Total y cantidad por estado (dashboard, HU-04). {@code porEstado} trae todos
 * los estados, con 0 si no hay. {@code confirmadasFinalizadas} solo aplica a
 * reservas; si es nulo no se envía.
 *
 * @param total                  cantidad total
 * @param porEstado              cantidad por cada estado
 * @param confirmadasFinalizadas reservas confirmadas cuya salida ya pasó; solo
 *                               para reservas
 */
public record ConteoPorEstadoDTO(long total, Map<String, Long> porEstado,
		@JsonInclude(JsonInclude.Include.NON_NULL) Long confirmadasFinalizadas) {
}
