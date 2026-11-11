package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resumen de una reserva para el listado del administrador (HU-11, CA-112).
 * Se arma en una sola consulta por página, con los nombres del huésped y del
 * anfitrión y el título del alojamiento.
 *
 * @param idReserva         identificador de la reserva
 * @param nombreHuesped     nombre del huésped
 * @param tituloAlojamiento título del alojamiento
 * @param ciudad            ciudad del alojamiento
 * @param nombreAnfitrion   nombre del anfitrión
 * @param fechaEntrada      día de llegada
 * @param fechaSalida       día de salida
 * @param noches            cantidad de noches
 * @param total             valor total
 * @param estado            estado de la reserva
 * @param completada        {@code true} si está CONFIRMADA y la salida ya llegó
 */
@SuppressWarnings("java:S107") // Proyección JPQL: un componente por columna.
@Schema(description = "Resumen de una reserva para el listado del administrador")
public record ReservaAdminDTO(@Schema(example = "15") Long idReserva,
		@Schema(example = "Ana Pérez") String nombreHuesped,
		@Schema(example = "Apartamento en Chapinero") String tituloAlojamiento,
		@Schema(example = "Bogotá") String ciudad,
		@Schema(example = "Luis Gómez") String nombreAnfitrion,
		LocalDate fechaEntrada, LocalDate fechaSalida,
		@Schema(example = "3") Integer noches,
		@Schema(example = "740000") BigDecimal total,
		EstadoReserva estado,
		@Schema(description = "Está CONFIRMADA y la fecha de salida ya llegó") Boolean completada) {
}
