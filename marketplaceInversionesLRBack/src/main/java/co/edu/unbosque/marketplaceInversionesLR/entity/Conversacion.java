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
 * Conversación entre un huésped ({@code idUsuario}) y el anfitrión
 * ({@code idAnfitrion}) sobre un alojamiento (HU-28). Los mensajes se guardan
 * en {@link Mensaje}.
 */
@Entity
@Table(name = "conversacion")
@Getter
@Setter
@NoArgsConstructor
public class Conversacion {

	/** Identificador de la conversación. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idConversacion;

	/** Momento en que se inició. */
	private LocalDateTime fechaCreacion;

	/** Estado de la conversación. */
	@Enumerated(EnumType.STRING)
	private EstadoConversacion estado;

	/** Huésped que inició la conversación. */
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
	private Usuario huesped;

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

	/** Alojamiento sobre el que se conversa. */
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
