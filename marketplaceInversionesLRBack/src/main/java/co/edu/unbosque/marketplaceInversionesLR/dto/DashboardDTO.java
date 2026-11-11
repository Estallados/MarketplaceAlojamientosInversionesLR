package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Indicadores del dashboard administrativo (HU-04, CA-022), ya calculados con
 * consultas agregadas (sin listas de entidades).
 *
 * @param totalUsuarios      cantidad de usuarios registrados
 * @param alojamientos       total de publicaciones y cantidad por estado (las
 *                           activas en {@code porEstado.ACTIVA})
 * @param reservas           total de reservas y cantidad por estado
 * @param ingresosEstimados  pagos cobrados menos las devoluciones de las
 *                           cancelaciones
 * @param ciudadesTop        ciudades con más reservas, de mayor a menor
 */
public record DashboardDTO(long totalUsuarios, ConteoPorEstadoDTO alojamientos, ConteoPorEstadoDTO reservas,
		BigDecimal ingresosEstimados, List<DemandaCiudadDTO> ciudadesTop) {
}
