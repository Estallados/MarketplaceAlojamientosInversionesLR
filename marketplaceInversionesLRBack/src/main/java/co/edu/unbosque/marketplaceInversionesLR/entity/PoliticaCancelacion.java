package co.edu.unbosque.marketplaceInversionesLR.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Política de cancelación que el anfitrión asigna a su alojamiento: cuántos
 * días antes se devuelve todo o una parte del pago. Lombok genera los getters,
 * los setters y el constructor vacío que requiere JPA.
 */
@Entity
@Table(name = "politicaCancelacion")
@Getter
@Setter
@NoArgsConstructor
public class PoliticaCancelacion {

	/** Identificador de la política. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long idPolitica;

	/** Nombre de la política, por ejemplo Flexible o Estricta. */
	private String nombre;

	/** Condiciones explicadas para el huésped. */
	private String condiciones;

	/** Días de anticipación para recibir el reembolso total. */
	private Integer diasReembolsoTotal;

	/** Días de anticipación para recibir el reembolso parcial. */
	private Integer diasReembolsoParcial;

	/** Porcentaje que se devuelve en el reembolso parcial. */
	private Integer porcentajeParcial;
}
