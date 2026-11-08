package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

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
 * Cambio de estado de un alojamiento: de qué estado a cuál, quién lo hizo,
 * cuándo y por qué. Es inmutable: el historial no se edita. Lombok genera los
 * getters, los setters y el constructor vacío que requiere JPA.
 */
@Entity
@Immutable
@Table(name = "historialEstadoAlojamiento")
@Getter
@Setter
@NoArgsConstructor
public class HistorialEstadoAlojamiento {

	/** Identificador del registro. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idHistorial;

	/** Alojamiento que cambió de estado. */
	@Column(name = "id_alojamiento", updatable = false)
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

	/** Estado anterior; null al crear la publicación. */
	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private EstadoAlojamiento estadoAnterior;

	/** Estado nuevo. */
	@Enumerated(EnumType.STRING)
	@Column(updatable = false)
	private EstadoAlojamiento estadoNuevo;

	/** Motivo del cambio; es opcional. */
	@Column(updatable = false)
	private String motivo;

	/** Usuario que hizo el cambio. */
	@Column(name = "id_usuario", updatable = false)
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

	/** Momento del cambio. */
	@Column(updatable = false)
	private LocalDateTime fecha;
}
