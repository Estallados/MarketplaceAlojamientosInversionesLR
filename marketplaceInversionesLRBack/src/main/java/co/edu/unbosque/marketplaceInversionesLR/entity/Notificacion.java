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
 * Aviso para un usuario, mapeado a la tabla {@code notificacion} de Neon. Por
 * ahora lo generan las decisiones de revisión de publicaciones (HU-32); la
 * consulta de las notificaciones llega con su historia.
 */
@Entity
@Table(name = "notificacion")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion {

	/** Identificador de la notificación. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idNotificacion;

	/** Evento que la originó, por ejemplo ALOJAMIENTO_RECHAZADO. */
	@Column(nullable = false, length = 50)
	private String tipo;

	/** Texto para el usuario. */
	@Column(nullable = false, length = 1000)
	private String mensaje;

	/** Momento en que se generó. */
	@Column(nullable = false)
	private LocalDateTime fecha;

	/** Si el usuario ya la leyó. */
	@Column(nullable = false)
	private Boolean leida;

	/** Usuario que la recibe. */
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
}
