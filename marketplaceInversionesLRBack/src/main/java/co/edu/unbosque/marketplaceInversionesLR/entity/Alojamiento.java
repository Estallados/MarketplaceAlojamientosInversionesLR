package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Alojamiento: la publicación que hace un anfitrión.
 * <p>
 * Las imágenes están en su propia clase ({@link Imagen}); las amenidades (lo
 * que incluye el alojamiento, como wifi o piscina) son una colección de
 * textos. Lombok genera los getters, los setters y el constructor vacío que
 * requiere JPA.
 * </p>
 */
@Entity
@Table(name = "alojamiento")
@Getter
@Setter
@NoArgsConstructor
public class Alojamiento {

	/** Identificador del alojamiento. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idAlojamiento;

	/** Título de la publicación. */
	private String titulo;

	/** Descripción que escribe el anfitrión. */
	private String descripcion;

	/** Dirección o referencia textual de la ubicación. */
	private String ubicacion;

	/** Ciudad donde queda; es el filtro principal de la búsqueda. */
	private String ciudad;

	/** Latitud para el mapa; es opcional. */
	private Double latitud;

	/** Longitud para el mapa; es opcional. */
	private Double longitud;

	/** Tipo de alojamiento, por ejemplo Casa o Apartamento. */
	private String tipo;

	/** Cantidad máxima de huéspedes. */
	private Integer capacidad;

	/** Cantidad de habitaciones. */
	private Integer habitaciones;

	/** Cantidad de camas. */
	private Integer camas;

	/** Cantidad de baños. */
	private Integer banos;

	/** Hora desde la que el huésped puede llegar (check-in). */
	private LocalTime horaEntrada;

	/** Hora límite de salida del huésped (check-out). */
	private LocalTime horaSalida;

	/** Precio por noche, en pesos colombianos. */
	private BigDecimal precioNoche;

	/**
	 * Porcentaje propio de limpieza; si es null se usa el global de
	 * ConfiguracionTarifa.
	 */
	private BigDecimal porcentajeLimpieza;

	/**
	 * Porcentaje propio de servicio; si es null se usa el global de
	 * ConfiguracionTarifa.
	 */
	private BigDecimal porcentajeServicio;

	/** Reglas de la casa, por ejemplo "No fumar". */
	private String reglas;

	/** Estado de la publicación. */
	@Enumerated(EnumType.STRING)
	private EstadoAlojamiento estado;

	/** Anfitrión dueño del alojamiento. */
	@Column(name = "id_anfitrion")
	private Long idAnfitrion;

	/**
	 * Clave foránea hacia {@link Usuario}; el valor se asigna con
	 * {@code idAnfitrion}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_anfitrion", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Usuario anfitrion;

	/** Política de cancelación que eligió el anfitrión. */
	@Column(name = "id_politica")
	private Long idPolitica;

	/**
	 * Clave foránea hacia {@link PoliticaCancelacion}; el valor se asigna con
	 * {@code idPolitica}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_politica", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private PoliticaCancelacion politica;

	/** Momento en que se creó la publicación. */
	private LocalDateTime fechaCreacion;

	/** Última edición de los datos o de las imágenes de la publicación. */
	private LocalDateTime fechaActualizacion;

	/**
	 * Amenidades del alojamiento (wifi, piscina, cocina...). En la base de datos
	 * la clave primaria es (id_alojamiento, amenidad): un alojamiento no repite
	 * amenidades.
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "alojamientoAmenidad", joinColumns = @JoinColumn(name = "id_alojamiento"))
	@Column(name = "amenidad")
	private Set<String> amenidades = new HashSet<>();
}
