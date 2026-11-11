package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConteoPorEstadoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DashboardDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.RegistroExportacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.RegistroExportacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Reserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.RegistroExportacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.ArchivoReporte;
import co.edu.unbosque.marketplaceInversionesLR.util.ExportadorUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Dashboard administrativo (HU-04) y exportación de reportes en PDF o Excel
 * (HU-05). Todos los datos salen de la información registrada. Cada
 * exportación queda en {@link RegistroExportacion} (quién la pidió y cuándo) y
 * en la auditoría.
 * <p>
 * Ingresos estimados = pagos aprobados o reembolsados menos las devoluciones
 * registradas en las cancelaciones.
 * </p>
 */
@Service
public class ReporteService {

	/** Reportes disponibles. */
	public static final List<String> TIPOS = List.of("usuarios", "alojamientos", "reservas", "ingresos", "demanda");

	/** Formatos disponibles. */
	public static final List<String> FORMATOS = List.of("pdf", "excel");

	/** Mensaje cuando el tipo o el formato del reporte no existen. */
	public static final String REPORTE_INVALIDO = "Reporte no válido. Tipos: usuarios, alojamientos, reservas, "
			+ "ingresos, demanda; formatos: pdf, excel.";

	/** Formato de las fechas en los reportes. */
	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

	/** Historial de exportaciones. */
	private final RegistroExportacionRepository exportacionRepo;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo     repositorio de usuarios
	 * @param alojamientoRepo repositorio de alojamientos
	 * @param reservaRepo     repositorio de reservas
	 * @param pagoRepo        repositorio de pagos
	 * @param cancelacionRepo  repositorio de cancelaciones
	 * @param exportacionRepo  historial de exportaciones
	 * @param auditoriaService servicio de auditoría
	 */
	public ReporteService(UsuarioRepository usuarioRepo, AlojamientoRepository alojamientoRepo,
			ReservaRepository reservaRepo, PagoRepository pagoRepo, CancelacionRepository cancelacionRepo,
			RegistroExportacionRepository exportacionRepo, AuditoriaService auditoriaService) {
		this.usuarioRepo = usuarioRepo;
		this.alojamientoRepo = alojamientoRepo;
		this.reservaRepo = reservaRepo;
		this.pagoRepo = pagoRepo;
		this.cancelacionRepo = cancelacionRepo;
		this.exportacionRepo = exportacionRepo;
		this.auditoriaService = auditoriaService;
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
	 * Exporta un reporte con la información registrada y deja constancia de
	 * quién lo pidió y cuándo (CA-027).
	 *
	 * @param tipo    usuarios, alojamientos, reservas, ingresos o demanda
	 * @param formato pdf o excel
	 * @param idActor administrador que exporta
	 * @return el archivo; o DATOS_INVALIDOS si el tipo o el formato no existen
	 */
	@Transactional
	public Resultado<ArchivoReporte> exportar(String tipo, String formato, Long idActor) {
		String tipoNormalizado = tipo == null ? "" : tipo.trim().toLowerCase();
		String formatoNormalizado = formato == null ? "" : formato.trim().toLowerCase();
		if (!TIPOS.contains(tipoNormalizado) || !FORMATOS.contains(formatoNormalizado)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, REPORTE_INVALIDO);
		}

		List<String> encabezados = new ArrayList<>();
		List<List<String>> filas = new ArrayList<>();
		llenar(tipoNormalizado, encabezados, filas);

		String titulo = "Reporte de " + tipoNormalizado;
		boolean pdf = "pdf".equals(formatoNormalizado);
		byte[] contenido = pdf ? ExportadorUtil.pdf(titulo, encabezados, filas)
				: ExportadorUtil.excel(titulo, encabezados, filas);

		RegistroExportacion registro = new RegistroExportacion();
		registro.setTipoReporte(tipoNormalizado);
		registro.setFormato(formatoNormalizado);
		registro.setCantidadRegistros(filas.size());
		registro.setIdUsuario(idActor);
		registro.setFecha(LocalDateTime.now());
		registro = exportacionRepo.save(registro);
		auditoriaService.registrar(idActor, AuditoriaService.REPORTE_EXPORTADO, AuditoriaService.ENTIDAD_REPORTE,
				registro.getIdRegistro(), ResultadoAuditoria.EXITOSO,
				tipoNormalizado + " en " + formatoNormalizado + " (" + filas.size() + " registros)");

		String nombre = "reporte-" + tipoNormalizado + (pdf ? ".pdf" : ".xlsx");
		String tipoContenido = pdf ? "application/pdf"
				: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		return Resultado.exito(new ArchivoReporte(nombre, tipoContenido, contenido));
	}

	/**
	 * Historial de exportaciones con el nombre de quien exportó (CA-027).
	 *
	 * @param pagina página y orden
	 * @return página de exportaciones
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<RegistroExportacionDTO> historial(Pageable pagina) {
		return PaginaDTO.de(exportacionRepo.findAll(pagina), registro -> {
			RegistroExportacionDTO dto = new RegistroExportacionDTO();
			dto.setIdRegistro(registro.getIdRegistro());
			dto.setTipoReporte(registro.getTipoReporte());
			dto.setFormato(registro.getFormato());
			dto.setCantidadRegistros(registro.getCantidadRegistros());
			dto.setIdUsuario(registro.getIdUsuario());
			dto.setFecha(registro.getFecha());
			usuarioRepo.findById(registro.getIdUsuario()).ifPresent(u -> dto.setNombreUsuario(u.getNombre()));
			return dto;
		});
	}

	/**
	 * Llena los encabezados y las filas del reporte según su tipo.
	 *
	 * @param tipo        tipo de reporte
	 * @param encabezados lista donde se agregan los encabezados
	 * @param filas       lista donde se agregan las filas
	 */
	private void llenar(String tipo, List<String> encabezados, List<List<String>> filas) {
		switch (tipo) {
		case "usuarios":
			encabezados.addAll(List.of("Id", "Nombre", "Correo", "Rol", "Estado"));
			for (Usuario u : usuarioRepo.findAll()) {
				filas.add(List.of(texto(u.getIdUsuario()), texto(u.getNombre()),
						texto(AESUtil.decrypt(u.getCorreo())), texto(u.getRol()), texto(u.getEstado())));
			}
			break;
		case "alojamientos":
			encabezados.addAll(List.of("Id", "Título", "Ciudad", "Tipo", "Capacidad", "Precio noche", "Estado"));
			for (Alojamiento a : alojamientoRepo.findAll()) {
				filas.add(List.of(texto(a.getIdAlojamiento()), texto(a.getTitulo()), texto(a.getCiudad()),
						texto(a.getTipo()), texto(a.getCapacidad()), dinero(a.getPrecioNoche()), texto(a.getEstado())));
			}
			break;
		case "reservas":
			encabezados.addAll(List.of("Id", "Alojamiento", "Huésped", "Llegada", "Salida", "Estado", "Total"));
			for (Reserva r : reservaRepo.findAll()) {
				filas.add(List.of(texto(r.getIdReserva()), texto(r.getIdAlojamiento()), texto(r.getIdHuesped()),
						texto(r.getFechaEntrada()), texto(r.getFechaSalida()), texto(r.getEstado()),
						dinero(r.getTotal())));
			}
			break;
		case "ingresos":
			encabezados.addAll(List.of("Pago", "Reserva", "Fecha", "Estado", "Valor"));
			for (Pago p : pagoRepo.findAll()) {
				filas.add(List.of(texto(p.getIdPago()), texto(p.getIdReserva()),
						p.getFecha() == null ? "" : p.getFecha().format(FORMATO_FECHA), texto(p.getEstado()),
						dinero(p.getValor())));
			}
			filas.add(List.of("", "", "", "Ingresos estimados", dinero(ingresosEstimados())));
			break;
		default:
			encabezados.addAll(List.of("Ciudad", "Reservas"));
			for (Object[] fila : reservaRepo.contarPorCiudad()) {
				filas.add(List.of(fila[0] == null ? "Sin ciudad" : fila[0].toString(), texto(fila[1])));
			}
			break;
		}
	}

	/**
	 * Convierte un valor a texto para el reporte.
	 *
	 * @param valor valor; puede ser {@code null}
	 * @return el texto, o vacío si es {@code null}
	 */
	private static String texto(Object valor) {
		return valor == null ? "" : valor.toString();
	}

	/**
	 * Formatea un valor en pesos colombianos para el reporte, por ejemplo
	 * {@code $ 600.000,00}.
	 *
	 * @param valor valor; puede ser {@code null}
	 * @return el valor formateado, o vacío si es {@code null}
	 */
	static String dinero(BigDecimal valor) {
		if (valor == null) {
			return "";
		}
		// Se crea en cada llamada porque DecimalFormat no es seguro entre hilos
		return new DecimalFormat("$ #,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")))
				.format(valor);
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
