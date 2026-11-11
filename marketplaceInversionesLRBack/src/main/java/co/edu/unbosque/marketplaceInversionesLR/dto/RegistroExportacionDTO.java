package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una exportación de reporte del historial (HU-05): qué se exportó, en qué
 * formato, cuántos registros tenía, quién la pidió y cuándo.
 */
@Getter
@Setter
@NoArgsConstructor
public class RegistroExportacionDTO {

	/** Identificador del registro. */
	private Long idRegistro;

	/** Reporte exportado. */
	private String tipoReporte;

	/** Formato del archivo: pdf o excel. */
	private String formato;

	/** Cantidad de filas del reporte. */
	private Integer cantidadRegistros;

	/** Administrador que lo pidió. */
	private Long idUsuario;

	/** Nombre del administrador que lo pidió. */
	private String nombreUsuario;

	/** Momento de la exportación. */
	private LocalDateTime fecha;
}
