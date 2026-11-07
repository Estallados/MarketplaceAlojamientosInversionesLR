package co.edu.unbosque.marketplaceInversionesLR.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Evento de la auditoría: quién hizo qué, sobre qué recurso, cuándo y con qué
 * resultado (RNF-006).
 * <p>
 * Es de solo inserción: {@code @Immutable} hace que Hibernate ignore cualquier
 * actualización, y el repositorio no expone update ni delete. No tiene claves
 * foráneas: debe poder registrar intentos con usuarios o recursos que no
 * existen.
 * </p>
 */
@Entity
@Immutable
@Table(name = "evento_auditoria")
public class EventoAuditoria {

	/** Identificador del evento. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idEvento;

	/** Acción realizada, por ejemplo REGISTRO_USUARIO. */
	@Column(nullable = false, updatable = false, length = 50)
	private String accion;

	/** Momento del evento. */
	@Column(nullable = false, updatable = false)
	private LocalDateTime fecha;

	/** Información adicional, por ejemplo el motivo de un rechazo. */
	@Column(updatable = false, length = 1000)
	private String detalle;

	/** Usuario que hizo la acción; null si no se conoce. */
	@Column(updatable = false)
	private Long idUsuario;

	/** Tipo de recurso afectado, por ejemplo USUARIO. */
	@Column(updatable = false, length = 50)
	private String entidad;

	/** Id del recurso afectado, en la tabla que indica {@code entidad}. */
	@Column(updatable = false)
	private Long idEntidad;

	/** Resultado de la operación. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ResultadoAuditoria resultado;

	/** Constructor vacío, requerido por JPA. */
	public EventoAuditoria() {
		// JPA necesita un constructor sin parámetros
	}

	/**
	 * Devuelve el identificador del evento.
	 *
	 * @return identificador del evento
	 */
	public Long getIdEvento() {
		return idEvento;
	}

	/**
	 * Devuelve la acción.
	 *
	 * @return acción realizada
	 */
	public String getAccion() {
		return accion;
	}

	/**
	 * Asigna la acción.
	 *
	 * @param accion acción realizada
	 */
	public void setAccion(String accion) {
		this.accion = accion;
	}

	/**
	 * Devuelve la fecha del evento.
	 *
	 * @return momento del evento
	 */
	public LocalDateTime getFecha() {
		return fecha;
	}

	/**
	 * Asigna la fecha del evento.
	 *
	 * @param fecha momento del evento
	 */
	public void setFecha(LocalDateTime fecha) {
		this.fecha = fecha;
	}

	/**
	 * Devuelve el detalle.
	 *
	 * @return información adicional, o {@code null}
	 */
	public String getDetalle() {
		return detalle;
	}

	/**
	 * Asigna el detalle.
	 *
	 * @param detalle información adicional
	 */
	public void setDetalle(String detalle) {
		this.detalle = detalle;
	}

	/**
	 * Devuelve el usuario que hizo la acción.
	 *
	 * @return id del usuario, o {@code null}
	 */
	public Long getIdUsuario() {
		return idUsuario;
	}

	/**
	 * Asigna el usuario que hizo la acción.
	 *
	 * @param idUsuario id del usuario
	 */
	public void setIdUsuario(Long idUsuario) {
		this.idUsuario = idUsuario;
	}

	/**
	 * Devuelve el tipo de recurso afectado.
	 *
	 * @return tipo de recurso, o {@code null}
	 */
	public String getEntidad() {
		return entidad;
	}

	/**
	 * Asigna el tipo de recurso afectado.
	 *
	 * @param entidad tipo de recurso
	 */
	public void setEntidad(String entidad) {
		this.entidad = entidad;
	}

	/**
	 * Devuelve el id del recurso afectado.
	 *
	 * @return id del recurso, o {@code null}
	 */
	public Long getIdEntidad() {
		return idEntidad;
	}

	/**
	 * Asigna el id del recurso afectado.
	 *
	 * @param idEntidad id del recurso
	 */
	public void setIdEntidad(Long idEntidad) {
		this.idEntidad = idEntidad;
	}

	/**
	 * Devuelve el resultado.
	 *
	 * @return resultado de la operación
	 */
	public ResultadoAuditoria getResultado() {
		return resultado;
	}

	/**
	 * Asigna el resultado.
	 *
	 * @param resultado resultado de la operación
	 */
	public void setResultado(ResultadoAuditoria resultado) {
		this.resultado = resultado;
	}
}
