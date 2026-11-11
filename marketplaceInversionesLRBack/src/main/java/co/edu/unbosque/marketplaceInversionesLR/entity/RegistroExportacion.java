package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro de cada exportación de reporte que hace un administrador (HU-05,
 * CA-027): qué reporte, en qué formato, cuántos registros tenía, quién lo pidió
 * y cuándo. Mapeado a la tabla {@code registro_exportacion} de Neon.
 */
@Entity
@Table(name = "registro_exportacion")
@Getter
@Setter
@NoArgsConstructor
public class RegistroExportacion {

	/** Identificador del registro. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idRegistro;

	/** Reporte exportado: usuarios, alojamientos, reservas, ingresos o demanda. */
	@Column(nullable = false, length = 50)
	private String tipoReporte;

	/** Formato del archivo: pdf o excel. */
	@Column(nullable = false, length = 10)
	private String formato;

	/** Cantidad de filas que tenía el reporte. */
	@Column(nullable = false)
	private Integer cantidadRegistros;

	/** Administrador que pidió la exportación. */
	@Column(name = "id_usuario", nullable = false)
	private Long idUsuario;

	/**
	 * Clave foránea hacia {@link Usuario}; el valor se asigna con
	 * {@code idUsuario}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_usuario", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Usuario usuario;

	/** Momento de la exportación. */
	@Column(nullable = false)
	private LocalDateTime fecha;
}
