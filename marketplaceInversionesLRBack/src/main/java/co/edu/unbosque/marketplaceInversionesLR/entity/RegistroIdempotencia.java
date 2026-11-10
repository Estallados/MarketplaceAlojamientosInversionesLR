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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uso de una clave {@code Idempotency-Key} (RNF-008, CA-140, CA-141), mapeado a
 * la tabla {@code registro_idempotencia} de Neon. Guarda la huella de la
 * petición y la respuesta, para devolver la misma respuesta si el cliente
 * reintenta. La clave es única por usuario y operación.
 */
@Entity
@Table(name = "registroIdempotencia",
		uniqueConstraints = @UniqueConstraint(name = "uq_idempotencia", columnNames = { "id_usuario", "operacion",
				"clave" }))
@Getter
@Setter
@NoArgsConstructor
public class RegistroIdempotencia {

	/** Identificador del registro. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idRegistro;

	/** Valor de la cabecera Idempotency-Key. */
	@Column(nullable = false, length = 100)
	private String clave;

	/** Usuario que hizo la petición. */
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

	/** Operación protegida, por ejemplo CANCELAR_RESERVA. */
	@Column(nullable = false, length = 50)
	private String operacion;

	/** SHA-256 de la petición, para detectar la misma clave con otros datos. */
	@Column(nullable = false, length = 64)
	private String huellaPeticion;

	/** EN_PROCESO o COMPLETADA. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoIdempotencia estado;

	/** Código HTTP de la respuesta guardada. */
	private Integer codigoRespuesta;

	/** Cuerpo JSON de la respuesta guardada. */
	@Column(columnDefinition = "text")
	private String cuerpoRespuesta;

	/** Momento del primer uso de la clave. */
	@Column(nullable = false)
	private LocalDateTime fechaCreacion;
}
