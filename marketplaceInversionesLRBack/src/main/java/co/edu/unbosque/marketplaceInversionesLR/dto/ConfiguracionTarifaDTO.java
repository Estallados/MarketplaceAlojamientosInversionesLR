package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Porcentajes de limpieza y de servicio tal como entran y salen por la API
 * (esquemas {@code ConfiguracionTarifaEntrada} y {@code ConfiguracionTarifa}
 * de {@code docs/openapi/administracion-reportes.yaml}).
 */
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionTarifaDTO {

	/** Identificador de la configuración guardada; null si es la de por defecto. */
	private Long idConfiguracion;

	/** Porcentaje sobre el precio base que se cobra por limpieza. */
	private BigDecimal porcentajeLimpieza;

	/** Porcentaje sobre el precio base que se cobra como tarifa de servicio. */
	private BigDecimal porcentajeServicio;

	/** Administrador o anfitrión que hizo el último cambio; null si es la de por defecto. */
	private Long idUsuario;

	/** Momento del último cambio; null si es la de por defecto. */
	private LocalDateTime fecha;

	/** {@code true} si aún no hay configuración guardada y se usan los valores por defecto. */
	private Boolean porDefecto;
}
