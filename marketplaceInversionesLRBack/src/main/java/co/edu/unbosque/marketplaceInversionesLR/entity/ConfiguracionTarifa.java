package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.math.BigDecimal;
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
 * Porcentajes globales de limpieza y de servicio que se aplican al cotizar.
 * Cada cambio se guarda como una fila nueva, así se conserva el historial; la
 * configuración vigente es la más reciente. Lombok genera los getters, los
 * setters y el constructor vacío que requiere JPA.
 */
@Entity
@Table(name = "configuracionTarifa")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionTarifa {

	/** Identificador de la configuración. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idConfiguracion;

	/** Porcentaje sobre el precio base que se cobra por limpieza. */
	private BigDecimal porcentajeLimpieza;

	/** Porcentaje sobre el precio base que se cobra como tarifa de servicio. */
	private BigDecimal porcentajeServicio;

	/** Administrador o anfitrión que hizo el cambio. */
	@Column(name = "id_usuario")
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

	/** Momento en que se guardó la configuración. */
	private LocalDateTime fecha;
}
