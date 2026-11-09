package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.CotizacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Cotización básica de una estancia, que la reserva necesita (HU-22). Calcula
 * el precio con los porcentajes vigentes (HU-24) y guarda la cotización por
 * {@value #MINUTOS_VIGENCIA} minutos; la reserva se crea desde ella con el
 * mismo monto (CA-086).
 * <p>
 * Fórmula, con redondeo a 2 decimales mitad hacia arriba: precio base = noches
 * × precio por noche; limpieza = base × % limpieza; servicio = base × %
 * servicio; total = base + limpieza + servicio. El descuento y los servicios
 * adicionales quedan en 0: los agrega la cotización completa (HU-31).
 * </p>
 */
@Service
public class CotizacionService {

	/** Minutos que una cotización sirve para reservar. */
	public static final int MINUTOS_VIGENCIA = 30;

	/** Mensaje cuando falta el alojamiento. */
	public static final String FALTA_ALOJAMIENTO = "Debes indicar el alojamiento a cotizar.";

	/** Mensaje cuando los huéspedes no son positivos. */
	public static final String HUESPEDES_INVALIDOS = "La cantidad de huéspedes debe ser mayor que cero.";

	/** Mensaje cuando las fechas no están libres. */
	public static final String FECHAS_NO_DISPONIBLES = "El alojamiento no está disponible en esas fechas.";

	/** Cien, para pasar de porcentaje a fracción. */
	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Repositorio de cotizaciones. */
	private final CotizacionRepository cotizacionRepo;

	/** Revisa que las fechas estén libres. */
	private final CalendarioService calendarioService;

	/** Da los porcentajes de limpieza y servicio. */
	private final TarifaService tarifaService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/**
	 * Crea el servicio.
	 *
	 * @param alojamientoRepo   repositorio de alojamientos
	 * @param cotizacionRepo    repositorio de cotizaciones
	 * @param calendarioService servicio de calendario
	 * @param tarifaService     servicio de tarifas
	 * @param modelMapper       copiador de entidades a DTO
	 */
	public CotizacionService(AlojamientoRepository alojamientoRepo, CotizacionRepository cotizacionRepo,
			CalendarioService calendarioService, TarifaService tarifaService, ModelMapper modelMapper) {
		this.alojamientoRepo = alojamientoRepo;
		this.cotizacionRepo = cotizacionRepo;
		this.calendarioService = calendarioService;
		this.tarifaService = tarifaService;
		this.modelMapper = modelMapper;
	}

	/**
	 * Componentes del precio de una estancia.
	 *
	 * @param precioBase    precio por noche multiplicado por las noches
	 * @param valorLimpieza valor de la limpieza
	 * @param valorServicio valor de la tarifa de servicio
	 * @param total         valor total
	 */
	public record Desglose(BigDecimal precioBase, BigDecimal valorLimpieza, BigDecimal valorServicio,
			BigDecimal total) {
	}

	/**
	 * Calcula el desglose de una estancia. No consulta la base de datos, así se
	 * puede probar sola.
	 *
	 * @param precioNoche        precio por noche
	 * @param noches             cantidad de noches
	 * @param porcentajeLimpieza porcentaje de limpieza
	 * @param porcentajeServicio porcentaje de servicio
	 * @return desglose con todos los componentes
	 */
	public static Desglose calcular(BigDecimal precioNoche, long noches, BigDecimal porcentajeLimpieza,
			BigDecimal porcentajeServicio) {
		BigDecimal base = redondear(precioNoche.multiply(BigDecimal.valueOf(noches)));
		BigDecimal limpieza = redondear(base.multiply(porcentajeLimpieza).divide(CIEN));
		BigDecimal servicio = redondear(base.multiply(porcentajeServicio).divide(CIEN));
		return new Desglose(base, limpieza, servicio, base.add(limpieza).add(servicio));
	}

	/**
	 * Cotiza una estancia y guarda la cotización.
	 *
	 * @param solicitud alojamiento, fechas y huéspedes
	 * @param idUsuario usuario que cotiza
	 * @return la cotización; DATOS_INVALIDOS si falta el alojamiento, las
	 *         fechas no son válidas o los huéspedes no caben; NO_ENCONTRADO si
	 *         el alojamiento no existe; ESTADO_INVALIDO si no está ACTIVA
	 *         (CA-071); NO_DISPONIBLE si las fechas no están libres
	 */
	@Transactional
	public Resultado<CotizacionDTO> cotizar(CotizacionDTO solicitud, Long idUsuario) {
		if (solicitud == null || solicitud.getIdAlojamiento() == null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, FALTA_ALOJAMIENTO);
		}
		Optional<Alojamiento> alojamientoOpt = alojamientoRepo.findById(solicitud.getIdAlojamiento());
		if (alojamientoOpt.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, CalendarioService.NO_EXISTE);
		}
		Alojamiento alojamiento = alojamientoOpt.get();
		if (alojamiento.getEstado() != EstadoAlojamiento.ACTIVA) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, CalendarioService.noActiva(alojamiento));
		}
		String errorFechas = CalendarioService.validarFechas(solicitud.getFechaEntrada(), solicitud.getFechaSalida());
		if (errorFechas != null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, errorFechas);
		}
		int huespedes = solicitud.getHuespedes() == null ? 1 : solicitud.getHuespedes();
		if (huespedes <= 0) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, HUESPEDES_INVALIDOS);
		}
		if (huespedes > alojamiento.getCapacidad()) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, "La cantidad de huéspedes (" + huespedes
					+ ") supera la capacidad del alojamiento (" + alojamiento.getCapacidad() + ").");
		}
		if (!calendarioService.estaLibre(alojamiento.getIdAlojamiento(), solicitud.getFechaEntrada(),
				solicitud.getFechaSalida())) {
			return Resultado.error(Resultado.NO_DISPONIBLE, FECHAS_NO_DISPONIBLES);
		}

		long noches = ChronoUnit.DAYS.between(solicitud.getFechaEntrada(), solicitud.getFechaSalida());
		BigDecimal porcentajeLimpieza = tarifaService.porcentajeLimpieza(alojamiento);
		BigDecimal porcentajeServicio = tarifaService.porcentajeServicio(alojamiento);
		Desglose desglose = calcular(alojamiento.getPrecioNoche(), noches, porcentajeLimpieza, porcentajeServicio);
		LocalDateTime ahora = LocalDateTime.now();

		Cotizacion cotizacion = new Cotizacion();
		cotizacion.setIdAlojamiento(alojamiento.getIdAlojamiento());
		cotizacion.setIdUsuario(idUsuario);
		cotizacion.setFechaEntrada(solicitud.getFechaEntrada());
		cotizacion.setFechaSalida(solicitud.getFechaSalida());
		cotizacion.setHuespedes(huespedes);
		cotizacion.setNoches((int) noches);
		cotizacion.setPrecioNoche(alojamiento.getPrecioNoche());
		cotizacion.setPrecioBase(desglose.precioBase());
		cotizacion.setDescuento(BigDecimal.ZERO);
		cotizacion.setPorcentajeLimpieza(porcentajeLimpieza);
		cotizacion.setValorLimpieza(desglose.valorLimpieza());
		cotizacion.setPorcentajeServicio(porcentajeServicio);
		cotizacion.setValorServicio(desglose.valorServicio());
		cotizacion.setValorAdicionales(BigDecimal.ZERO);
		cotizacion.setTotal(desglose.total());
		cotizacion.setFechaCreacion(ahora);
		cotizacion.setFechaExpiracion(ahora.plusMinutes(MINUTOS_VIGENCIA));
		return Resultado.exito(convertirDTO(cotizacionRepo.save(cotizacion)));
	}

	/**
	 * Convierte una cotización guardada a DTO.
	 *
	 * @param cotizacion cotización guardada
	 * @return cotización para la API
	 */
	public CotizacionDTO convertirDTO(Cotizacion cotizacion) {
		return modelMapper.map(cotizacion, CotizacionDTO.class);
	}

	/**
	 * Redondea un valor a 2 decimales, mitad hacia arriba.
	 *
	 * @param valor valor a redondear
	 * @return valor redondeado
	 */
	private static BigDecimal redondear(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}
}
