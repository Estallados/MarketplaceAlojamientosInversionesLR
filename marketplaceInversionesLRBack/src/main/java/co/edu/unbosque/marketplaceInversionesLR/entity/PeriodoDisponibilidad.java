package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDate;

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
 * Período en que el anfitrión ofrece su alojamiento (HU-20, RF-025). Las dos
 * fechas están incluidas: [fechaInicio, fechaFin]. Los períodos de un mismo
 * alojamiento no se cruzan (en Neon lo asegura la restricción
 * {@code ex_periodo_sin_cruces}). Lombok genera los getters, los setters y el
 * constructor vacío que requiere JPA.
 */
@Entity
@Table(name = "periodoDisponibilidad")
@Getter
@Setter
@NoArgsConstructor
public class PeriodoDisponibilidad {

	/** Identificador del período. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idPeriodo;

	/** Alojamiento al que pertenece el período. */
	@Column(name = "id_alojamiento", nullable = false, updatable = false)
	private Long idAlojamiento;

	/**
	 * Clave foránea hacia {@link Alojamiento}; el valor se asigna con
	 * {@code idAlojamiento}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_alojamiento", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Alojamiento alojamiento;

	/** Primer día disponible. */
	@Column(nullable = false)
	private LocalDate fechaInicio;

	/** Último día disponible; posterior a {@code fechaInicio}. */
	@Column(nullable = false)
	private LocalDate fechaFin;
}
