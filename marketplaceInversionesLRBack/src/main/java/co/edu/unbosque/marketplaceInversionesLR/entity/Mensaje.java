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
 * Mensaje dentro de una conversación entre un huésped y un anfitrión (HU-28):
 * quién lo envió ({@code idUsuario}), qué dice y cuándo se envió.
 */
@Entity
@Table(name = "mensaje")
@Getter
@Setter
@NoArgsConstructor
public class Mensaje {

	/** Identificador del mensaje. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idMensaje;

	/** Texto del mensaje. */
	@Column(length = 2000)
	private String contenido;

	/** Momento en que se envió. */
	private LocalDateTime fechaEnvio;

	/** Conversación a la que pertenece. */
	@Column(name = "id_conversacion")
	private Long idConversacion;

	/**
	 * Clave foránea hacia {@link Conversacion}; el valor se asigna con
	 * {@code idConversacion}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "id_conversacion", insertable = false, updatable = false)
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Conversacion conversacion;

	/** Usuario que envió el mensaje. */
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
}
