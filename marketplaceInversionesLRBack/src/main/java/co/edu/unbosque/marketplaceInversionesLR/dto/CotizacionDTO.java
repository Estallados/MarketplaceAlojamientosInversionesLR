package co.edu.unbosque.marketplaceInversionesLR.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cotización de una estancia. En la solicitud el huésped envía el alojamiento,
 * las fechas, los huéspedes y los servicios adicionales que quiere; el resto lo
 * calcula el servidor.
 */
@Getter
@Setter
@NoArgsConstructor
public class CotizacionDTO {

	/** Identificador; se envía en {@code POST /reservas}. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long idCotizacion;

	/** Alojamiento a cotizar. */
	private Long idAlojamiento;

	/** Día de llegada; no puede estar en el pasado. */
	private LocalDate fechaEntrada;

	/** Día de salida; debe ser posterior a la llegada. */
	private LocalDate fechaSalida;

	/** Cantidad de huéspedes; si no se envía, 1. */
	private Integer huespedes;

	/** Cantidad de noches. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Integer noches;

	/** Precio por noche al momento de cotizar. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal precioNoche;

	/** Precio por noche multiplicado por las noches. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal precioBase;

	/** Descuento aplicado. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal descuento;

	/** Porcentaje de limpieza aplicado. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal porcentajeLimpieza;

	/** Valor de la limpieza. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal valorLimpieza;

	/** Porcentaje de la tarifa de servicio aplicado. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal porcentajeServicio;

	/** Valor de la tarifa de servicio. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal valorServicio;

	/** Suma de los servicios adicionales elegidos. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal valorAdicionales;

	/** Valor total a pagar. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private BigDecimal total;

	/** Ids de los servicios adicionales que eligió el huésped (entrada). */
	private List<Long> serviciosSeleccionados;

	/** Servicios adicionales incluidos, con su costo (salida). */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private List<ServicioAdicionalDTO> serviciosAdicionales;

	/** Todos los servicios adicionales que ofrece el alojamiento. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private List<ServicioAdicionalDTO> serviciosDisponibles;

	/** Servicios adicionales elegidos en JSON, con nombre y costo congelados. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private String desglose;

	/** Usuario que pidió la cotización. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long idUsuario;

	/** Reserva creada con esta cotización; {@code null} si aún no se usa. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private Long idReserva;

	/** Momento en que se cotizó. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime fechaCreacion;

	/** Momento en que deja de servir para reservar. */
	@Schema(accessMode = Schema.AccessMode.READ_ONLY)
	private LocalDateTime fechaExpiracion;
}
