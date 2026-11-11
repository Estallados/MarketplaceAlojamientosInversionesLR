package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
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
 * Reseña de una estancia, mapeada a la tabla {@code resena} de Neon. La
 * escribe el huésped de una reserva completada (HU-03) y da la calificación de
 * los alojamientos (HU-23). En Neon, el trigger
 * {@code trg_resena_estancia_completada} rechaza la de una reserva no
 * completada, {@code uq_resena_reserva} permite una por reserva y
 * {@code trg_resena_proteger} impide borrarla o cambiar su contenido; solo el
 * estado cambia (moderación, HU-14).
 */
@Entity
@Table(name = "resena")
@Getter
@Setter
@NoArgsConstructor
public class Resena {

	/** Identificador de la reseña. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idResena;

	/** Calificación de 1 a 5. */
	@Column(nullable = false)
	private Integer calificacion;

	/** Comentario del huésped. */
	@Column(length = 2000)
	private String comentario;

	/** PUBLICADA u OCULTA. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoResena estado;

	/** Momento en que se escribió. */
	@Column(nullable = false)
	private LocalDateTime fecha;

	/** Huésped que la escribió. */
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

	/** Reserva de la estancia reseñada; una reseña por reserva. */
	@Column(name = "id_reserva", nullable = false, unique = true)
	private Long idReserva;

	/**
	 * Clave foránea hacia {@link Reserva}; el valor se asigna con
	 * {@code idReserva}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_reserva", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Reserva reserva;

	/** Alojamiento reseñado. */
	@Column(name = "id_alojamiento", nullable = false)
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
