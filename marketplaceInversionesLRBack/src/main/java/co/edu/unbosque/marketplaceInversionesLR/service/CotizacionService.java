package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.CotizacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.ServicioAdicional;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Cotización de una estancia (HU-22, HU-31). Calcula cada componente del
 * precio con los porcentajes vigentes (HU-24) y guarda la cotización por
 * {@value #MINUTOS_VIGENCIA} minutos; la reserva se crea desde ella con el
 * mismo monto (CA-086).
 * <p>
 * Fórmula, con redondeo a 2 decimales mitad hacia arriba: precio base = noches
 * × precio por noche; descuento = 0 (todavía no hay reglas de descuento);
 * limpieza = (base − descuento) × % limpieza; servicio = (base − descuento) ×
 * % servicio; adicionales = suma del costo de cada servicio adicional elegido
 * (una vez por estancia); total = base − descuento + limpieza + servicio +
 * adicionales.
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

	/** Repositorio de servicios adicionales. */
	private final ServicioAdicionalRepository servicioRepo;

	/** Repositorio de cotizaciones. */
	private final CotizacionRepository cotizacionRepo;

	/** Revisa que las fechas estén libres. */
	private final CalendarioService calendarioService;

	/** Da los porcentajes de limpieza y servicio. */
	private final TarifaService tarifaService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/** Guarda y lee los servicios elegidos en JSON. */
	private final ObjectMapper objectMapper;

	/**
	 * Crea el servicio.
	 *
	 * @param alojamientoRepo   repositorio de alojamientos
	 * @param servicioRepo      repositorio de servicios adicionales
	 * @param cotizacionRepo    repositorio de cotizaciones
	 * @param calendarioService servicio de calendario
	 * @param tarifaService     servicio de tarifas
	 * @param modelMapper       copiador de entidades a DTO
	 * @param objectMapper      conversor a JSON
	 */
	public CotizacionService(AlojamientoRepository alojamientoRepo, ServicioAdicionalRepository servicioRepo,
			CotizacionRepository cotizacionRepo, CalendarioService calendarioService, TarifaService tarifaService,
			ModelMapper modelMapper, ObjectMapper objectMapper) {
		this.alojamientoRepo = alojamientoRepo;
		this.servicioRepo = servicioRepo;
		this.cotizacionRepo = cotizacionRepo;
		this.calendarioService = calendarioService;
		this.tarifaService = tarifaService;
		this.modelMapper = modelMapper;
		this.objectMapper = objectMapper;
	}

	/**
	 * Componentes del precio de una estancia.
	 *
	 * @param precioBase       precio por noche multiplicado por las noches
	 * @param descuento        descuento aplicado
	 * @param valorLimpieza    valor de la limpieza
	 * @param valorServicio    valor de la tarifa de servicio
	 * @param valorAdicionales suma de los servicios adicionales
	 * @param total            valor total
	 */
	public record Desglose(BigDecimal precioBase, BigDecimal descuento, BigDecimal valorLimpieza,
			BigDecimal valorServicio, BigDecimal valorAdicionales, BigDecimal total) {
	}

	/**
	 * Calcula el desglose de una estancia. No consulta la base de datos, así se
	 * puede probar sola.
	 *
	 * @param precioNoche        precio por noche
	 * @param noches             cantidad de noches
	 * @param porcentajeLimpieza porcentaje de limpieza
	 * @param porcentajeServicio porcentaje de servicio
	 * @param costosAdicionales  costo de cada servicio adicional elegido
	 * @return desglose con todos los componentes
	 */
	public static Desglose calcular(BigDecimal precioNoche, long noches, BigDecimal porcentajeLimpieza,
			BigDecimal porcentajeServicio, List<BigDecimal> costosAdicionales) {
		BigDecimal base = redondear(precioNoche.multiply(BigDecimal.valueOf(noches)));
		BigDecimal descuento = redondear(BigDecimal.ZERO);
		BigDecimal neto = base.subtract(descuento);
		BigDecimal limpieza = redondear(neto.multiply(porcentajeLimpieza).divide(CIEN));
		BigDecimal servicio = redondear(neto.multiply(porcentajeServicio).divide(CIEN));
		BigDecimal adicionales = redondear(costosAdicionales.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
		BigDecimal total = neto.add(limpieza).add(servicio).add(adicionales);
		return new Desglose(base, descuento, limpieza, servicio, adicionales, redondear(total));
	}

	/**
	 * Cotiza una estancia y guarda la cotización.
	 *
	 * @param solicitud alojamiento, fechas, huéspedes y servicios elegidos
	 * @param idUsuario usuario que cotiza
	 * @return la cotización; DATOS_INVALIDOS si falta el alojamiento o las
	 *         fechas, los huéspedes o los servicios no son válidos;
	 *         NO_ENCONTRADO si el alojamiento no existe; ESTADO_INVALIDO si no
	 *         está ACTIVA (CA-071); NO_DISPONIBLE si las fechas no están libres
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

		List<ServicioAdicional> disponibles = servicioRepo
				.findByIdAlojamientoOrderByIdServicio(alojamiento.getIdAlojamiento());
		List<ServicioAdicional> elegidos = new ArrayList<>();
		if (solicitud.getServiciosSeleccionados() != null) {
			for (Long idServicio : new LinkedHashSet<>(solicitud.getServiciosSeleccionados())) {
				Optional<ServicioAdicional> servicio = disponibles.stream()
						.filter(s -> s.getIdServicio().equals(idServicio)).findFirst();
				if (servicio.isEmpty()) {
					return Resultado.error(Resultado.DATOS_INVALIDOS,
							"El servicio adicional " + idServicio + " no pertenece a este alojamiento.");
				}
				elegidos.add(servicio.get());
			}
		}

		long noches = ChronoUnit.DAYS.between(solicitud.getFechaEntrada(), solicitud.getFechaSalida());
		BigDecimal porcentajeLimpieza = tarifaService.porcentajeLimpieza(alojamiento);
		BigDecimal porcentajeServicio = tarifaService.porcentajeServicio(alojamiento);
		Desglose desglose = calcular(alojamiento.getPrecioNoche(), noches, porcentajeLimpieza, porcentajeServicio,
				elegidos.stream().map(ServicioAdicional::getCosto).toList());
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
		cotizacion.setDescuento(desglose.descuento());
		cotizacion.setPorcentajeLimpieza(porcentajeLimpieza);
		cotizacion.setValorLimpieza(desglose.valorLimpieza());
		cotizacion.setPorcentajeServicio(porcentajeServicio);
		cotizacion.setValorServicio(desglose.valorServicio());
		cotizacion.setValorAdicionales(desglose.valorAdicionales());
		cotizacion.setTotal(desglose.total());
		cotizacion.setDesglose(
				serializar(elegidos.stream().map(s -> modelMapper.map(s, ServicioAdicionalDTO.class)).toList()));
		cotizacion.setFechaCreacion(ahora);
		cotizacion.setFechaExpiracion(ahora.plusMinutes(MINUTOS_VIGENCIA));
		cotizacion = cotizacionRepo.save(cotizacion);

		CotizacionDTO dto = convertirDTO(cotizacion);
		dto.setServiciosDisponibles(
				disponibles.stream().map(s -> modelMapper.map(s, ServicioAdicionalDTO.class)).toList());
		return Resultado.exito(dto);
	}

	/**
	 * Convierte una cotización guardada a DTO, con los servicios adicionales tal
	 * como quedaron al cotizar.
	 *
	 * @param cotizacion cotización guardada
	 * @return cotización para la API
	 */
	public CotizacionDTO convertirDTO(Cotizacion cotizacion) {
		CotizacionDTO dto = modelMapper.map(cotizacion, CotizacionDTO.class);
		List<ServicioAdicionalDTO> elegidos = leerServicios(cotizacion.getDesglose());
		dto.setServiciosAdicionales(elegidos);
		dto.setServiciosSeleccionados(elegidos.stream().map(ServicioAdicionalDTO::getIdServicio).toList());
		return dto;
	}

	/**
	 * Convierte los servicios elegidos a JSON para guardarlos en la cotización.
	 *
	 * @param servicios servicios elegidos
	 * @return JSON con nombre y costo de cada servicio
	 * @throws IllegalStateException si no se puede convertir
	 */
	private String serializar(List<ServicioAdicionalDTO> servicios) {
		try {
			return objectMapper.writeValueAsString(servicios);
		} catch (JacksonException e) {
			throw new IllegalStateException("No se pudo guardar el desglose de la cotización", e);
		}
	}

	/**
	 * Lee los servicios guardados en JSON.
	 *
	 * @param json JSON guardado; puede ser {@code null}
	 * @return servicios, o lista vacía si no hay
	 * @throws IllegalStateException si el JSON no se puede leer
	 */
	private List<ServicioAdicionalDTO> leerServicios(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<ServicioAdicionalDTO>>() {
			});
		} catch (JacksonException e) {
			throw new IllegalStateException("No se pudo leer el desglose de la cotización", e);
		}
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
