package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.math.BigDecimal;

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
 * Servicio opcional que el anfitrión ofrece con su alojamiento (desayuno,
 * traslado desde el aeropuerto, etc.). El huésped puede agregarlo a su
 * reserva y su costo se suma una vez al total.
 */
@Entity
@Table(name = "servicioAdicional")
@Getter
@Setter
@NoArgsConstructor
public class ServicioAdicional {

	/** Identificador del servicio. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idServicio;

	/** Nombre que ve el huésped, por ejemplo "Desayuno". */
	private String nombre;

	/** Costo del servicio por reserva, en pesos colombianos. */
	private BigDecimal costo;

	/** Alojamiento que ofrece el servicio. */
	@Column(name = "id_alojamiento")
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
}
