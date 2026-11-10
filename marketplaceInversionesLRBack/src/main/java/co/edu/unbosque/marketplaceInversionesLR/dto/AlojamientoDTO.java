package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Datos de un alojamiento tal como entran y salen por la API (esquemas
 * {@code AlojamientoEntrada} y {@code Alojamiento} de
 * {@code docs/openapi/alojamientos.yaml}). Al crear se ignoran el id, el
 * estado y el anfitrión.
 */
@Getter
@Setter
@NoArgsConstructor
public class AlojamientoDTO {

	/** Identificador del alojamiento. */
	private Long idAlojamiento;

	/** Título de la publicación. */
	private String titulo;

	/** Descripción que escribe el anfitrión. */
	private String descripcion;

	/** Dirección o referencia textual de la ubicación. */
	private String ubicacion;

	/** Ciudad donde queda. */
	private String ciudad;

	/** Latitud para el mapa; es opcional. */
	private Double latitud;

	/** Longitud para el mapa; es opcional. */
	private Double longitud;

	/** Tipo de alojamiento, por ejemplo Casa o Apartamento. */
	private String tipo;

	/** Cantidad máxima de huéspedes; mayor que cero. */
	private Integer capacidad;

	/** Precio por noche en pesos colombianos; mayor que cero. */
	private BigDecimal precioNoche;

	/** Porcentaje propio de limpieza; si es null se usa el global. */
	private BigDecimal porcentajeLimpieza;

	/** Porcentaje propio de servicio; si es null se usa el global. */
	private BigDecimal porcentajeServicio;

	/** Reglas de la casa. */
	private String reglas;

	/** Estado de la publicación. */
	private EstadoAlojamiento estado;

	/** Anfitrión dueño del alojamiento. */
	private Long idAnfitrion;

	/** Política de cancelación elegida. */
	private Long idPolitica;

	/** Nombre de la política de cancelación elegida. */
	private String nombrePolitica;

	/** Momento en que se creó la publicación. */
	private LocalDateTime fechaCreacion;

	/** Amenidades del alojamiento (wifi, piscina...). */
	private Set<String> amenidades = new HashSet<>();

	/** URLs de las imágenes, la principal primero. */
	private List<String> imagenes;

	/** Cantidad de habitaciones. */
	private Integer habitaciones;

	/** Cantidad de camas. */
	private Integer camas;

	/** Cantidad de baños. */
	private Integer banos;

	/** Hora de llegada (check-in), en formato HH:mm. */
	@JsonFormat(pattern = "HH:mm")
	private LocalTime horaEntrada;

	/** Hora de salida (check-out), en formato HH:mm. */
	@JsonFormat(pattern = "HH:mm")
	private LocalTime horaSalida;

	/** Última edición de la publicación. */
	private LocalDateTime fechaActualizacion;

	/** Imágenes con su id, URL y si son la principal; la principal primero. */
	private List<ImagenDTO> detalleImagenes;

	/** Servicios adicionales que ofrece. */
	private List<ServicioAdicionalDTO> serviciosAdicionales;

	/** Nombre del anfitrión. */
	private String nombreAnfitrion;

	/**
	 * Promedio de las reseñas publicadas, con una cifra decimal; {@code null} si
	 * no tiene. Lo calcula el servidor.
	 */
	private Double promedioCalificacion;

	/** Cantidad de reseñas publicadas. Lo calcula el servidor. */
	private Long cantidadResenas;
}
