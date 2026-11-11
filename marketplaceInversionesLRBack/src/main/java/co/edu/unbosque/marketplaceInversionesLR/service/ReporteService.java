package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConteoPorEstadoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DashboardDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;

/**
 * Dashboard administrativo (HU-04). Todos los indicadores salen de consultas
 * agregadas sobre la información registrada.
 * <p>
 * Ingresos estimados = pagos aprobados o reembolsados menos las devoluciones
 * registradas en las cancelaciones.
 * </p>
 */
@Service
public class ReporteService {

	/** Máximo de ciudades en el ranking del dashboard. */
	public static final int CIUDADES_MAXIMAS = 50;

	/** Estados de pago que cuentan como cobrados. */
	private static final List<EstadoPago> PAGOS_COBRADOS = List.of(EstadoPago.APROBADO, EstadoPago.REEMBOLSADO);

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Repositorio de reservas. */
	private final ReservaRepository reservaRepo;

	/** Repositorio de pagos. */
	private final PagoRepository pagoRepo;

	/** Repositorio de cancelaciones. */
	private final CancelacionRepository cancelacionRepo;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo     repositorio de usuarios
	 * @param alojamientoRepo repositorio de alojamientos
	 * @param reservaRepo     repositorio de reservas
	 * @param pagoRepo        repositorio de pagos
	 * @param cancelacionRepo repositorio de cancelaciones
	 */
	public ReporteService(UsuarioRepository usuarioRepo, AlojamientoRepository alojamientoRepo,
			ReservaRepository reservaRepo, PagoRepository pagoRepo, CancelacionRepository cancelacionRepo) {
		this.usuarioRepo = usuarioRepo;
		this.alojamientoRepo = alojamientoRepo;
		this.reservaRepo = reservaRepo;
		this.pagoRepo = pagoRepo;
		this.cancelacionRepo = cancelacionRepo;
	}

	/**
	 * Indicadores del dashboard (CA-022): usuarios, publicaciones y reservas
	 * por estado, ingresos estimados y ciudades con más reservas. Los conteos
	 * por estado traen todos los estados, con 0 si no hay.
	 *
	 * @param topCiudades cantidad de ciudades con más reservas (entre 1 y
	 *                    {@value #CIUDADES_MAXIMAS})
	 * @return indicadores del dashboard
	 */
	@Transactional(readOnly = true)
	public DashboardDTO dashboard(int topCiudades) {
		long finalizadas = reservaRepo.countByEstadoAndFechaSalidaBefore(EstadoReserva.CONFIRMADA, LocalDate.now());
		int limite = Math.min(Math.max(topCiudades, 1), CIUDADES_MAXIMAS);
		return new DashboardDTO(usuarioRepo.count(),
				conteo(EstadoAlojamiento.values(), alojamientoRepo.contarPorEstado(), null),
				conteo(EstadoReserva.values(), reservaRepo.contarPorEstado(), finalizadas),
				ingresosEstimados(), reservaRepo.contarCiudadesTop(PageRequest.of(0, limite)));
	}

	/**
	 * Arma un conteo por estado: pone 0 en cada estado, aplica las filas
	 * [estado, cantidad] y suma el total.
	 *
	 * @param estados     todos los valores del enum de estados
	 * @param filas       filas [estado, cantidad] de la consulta
	 * @param finalizadas reservas confirmadas ya finalizadas; {@code null} si
	 *                    no aplica
	 * @return conteo por estado
	 */
	private static ConteoPorEstadoDTO conteo(Enum<?>[] estados, List<Object[]> filas, Long finalizadas) {
		Map<String, Long> porEstado = new LinkedHashMap<>();
		for (Enum<?> estado : estados) {
			porEstado.put(estado.name(), 0L);
		}
		long total = 0;
		for (Object[] fila : filas) {
			porEstado.put(fila[0].toString(), (Long) fila[1]);
			total += (Long) fila[1];
		}
		return new ConteoPorEstadoDTO(total, porEstado, finalizadas);
	}

	/**
	 * Ingresos estimados: pagos cobrados menos devoluciones.
	 *
	 * @return ingresos estimados
	 */
	private BigDecimal ingresosEstimados() {
		return pagoRepo.sumarValores(PAGOS_COBRADOS).subtract(cancelacionRepo.sumarReembolsos());
	}
}
