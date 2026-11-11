package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.DashboardDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DemandaCiudadDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.RegistroExportacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.entity.Pago;
import co.edu.unbosque.marketplaceInversionesLR.entity.RegistroExportacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.RegistroExportacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.ArchivoReporte;
import co.edu.unbosque.marketplaceInversionesLR.util.ExportadorUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias del dashboard (HU-04, CA-022) con los repositorios
 * simulados.
 */
@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

	/** Administrador que exporta. */
	private static final Long ADMIN = 2L;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private PagoRepository pagoRepo;

	@Mock
	private CancelacionRepository cancelacionRepo;

	@Mock
	private RegistroExportacionRepository exportacionRepo;

	@Mock
	private AuditoriaService auditoriaService;

	private ReporteService servicio;

	@BeforeEach
	void preparar() {
		servicio = new ReporteService(usuarioRepo, alojamientoRepo, reservaRepo, pagoRepo, cancelacionRepo,
				exportacionRepo, auditoriaService);
	}

	/** El repositorio de exportaciones devuelve el registro con id. */
	private void guardaRegistros() {
		when(exportacionRepo.save(any(RegistroExportacion.class))).thenAnswer(inv -> {
			RegistroExportacion r = inv.getArgument(0);
			r.setIdRegistro(31L);
			return r;
		});
	}

	/** Deja los repositorios con datos registrados de ejemplo. */
	private void conDatos() {
		when(usuarioRepo.count()).thenReturn(7L);
		when(alojamientoRepo.contarPorEstado()).thenReturn(List.of(
				new Object[] { EstadoAlojamiento.ACTIVA, 3L }, new Object[] { EstadoAlojamiento.BORRADOR, 1L }));
		when(reservaRepo.contarPorEstado()).thenReturn(List.of(
				new Object[] { EstadoReserva.CONFIRMADA, 4L }, new Object[] { EstadoReserva.CANCELADA, 2L }));
		when(reservaRepo.countByEstadoAndFechaSalidaBefore(EstadoReserva.CONFIRMADA, LocalDate.now())).thenReturn(1L);
		when(pagoRepo.sumarValores(List.of(EstadoPago.APROBADO, EstadoPago.REEMBOLSADO)))
				.thenReturn(new BigDecimal("1000000"));
		when(cancelacionRepo.sumarReembolsos()).thenReturn(new BigDecimal("250000"));
		when(reservaRepo.contarCiudadesTop(any(Pageable.class))).thenReturn(
				List.of(new DemandaCiudadDTO("Bogotá", 4L), new DemandaCiudadDTO("Medellín", 2L)));
	}

	@Test
	@DisplayName("CA-022 El dashboard trae usuarios, publicaciones activas, reservas por estado, ingresos y ciudades")
	void indicadores_CA022() {
		conDatos();

		DashboardDTO dashboard = servicio.dashboard(5);

		assertThat(dashboard.totalUsuarios()).isEqualTo(7);
		assertThat(dashboard.alojamientos().total()).isEqualTo(4);
		assertThat(dashboard.alojamientos().porEstado()).containsEntry("ACTIVA", 3L).containsEntry("BORRADOR", 1L);
		assertThat(dashboard.alojamientos().confirmadasFinalizadas()).isNull();
		assertThat(dashboard.reservas().total()).isEqualTo(6);
		assertThat(dashboard.reservas().porEstado()).containsEntry("CONFIRMADA", 4L).containsEntry("CANCELADA", 2L);
		assertThat(dashboard.reservas().confirmadasFinalizadas()).isEqualTo(1L);
		assertThat(dashboard.ingresosEstimados()).isEqualByComparingTo("750000");
		assertThat(dashboard.ciudadesTop()).extracting(DemandaCiudadDTO::ciudad).containsExactly("Bogotá",
				"Medellín");
	}

	@Test
	@DisplayName("CA-022 Los estados sin registros aparecen con 0, en el orden del enum")
	void estadosEnCero_CA022() {
		conDatos();

		DashboardDTO dashboard = servicio.dashboard(5);

		assertThat(dashboard.alojamientos().porEstado()).containsOnlyKeys("BORRADOR", "PENDIENTE_REVISION", "ACTIVA",
				"PAUSADA", "BLOQUEADA").containsEntry("PAUSADA", 0L);
		assertThat(dashboard.reservas().porEstado().keySet()).containsExactly("PENDIENTE_PAGO", "CONFIRMADA",
				"EN_REVISION", "CANCELADA");
		assertThat(dashboard.reservas().porEstado()).containsEntry("EN_REVISION", 0L);
	}

	@Test
	@DisplayName("CA-022 La cantidad de ciudades del ranking queda entre 1 y el máximo")
	void limiteDeCiudades_CA022() {
		conDatos();
		ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);

		servicio.dashboard(0);
		servicio.dashboard(500);

		verify(reservaRepo, times(2)).contarCiudadesTop(limite.capture());
		assertThat(limite.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1,
				ReporteService.CIUDADES_MAXIMAS);
	}

	@Test
	@DisplayName("CA-027 Exportar en PDF genera el archivo y registra solicitante, fecha y cantidad de registros")
	void exportaPdfYRegistra_CA027() {
		guardaRegistros();
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(4L);
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setCiudad("Guatapé");
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		when(alojamientoRepo.findAll()).thenReturn(List.of(alojamiento));
		LocalDateTime antes = LocalDateTime.now();

		Resultado<ArchivoReporte> resultado = servicio.exportar(" Alojamientos ", "PDF", ADMIN);

		assertThat(resultado.esExito()).isTrue();
		ArchivoReporte archivo = resultado.getDato();
		assertThat(archivo.getNombreArchivo()).isEqualTo("reporte-alojamientos.pdf");
		assertThat(archivo.getTipoContenido()).isEqualTo("application/pdf");
		assertThat(new String(archivo.getContenido(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

		ArgumentCaptor<RegistroExportacion> registro = ArgumentCaptor.forClass(RegistroExportacion.class);
		verify(exportacionRepo).save(registro.capture());
		assertThat(registro.getValue().getIdUsuario()).isEqualTo(ADMIN);
		assertThat(registro.getValue().getFecha()).isAfterOrEqualTo(antes);
		assertThat(registro.getValue().getTipoReporte()).isEqualTo("alojamientos");
		assertThat(registro.getValue().getFormato()).isEqualTo("pdf");
		assertThat(registro.getValue().getCantidadRegistros()).isEqualTo(1);
		verify(auditoriaService).registrar(eq(ADMIN), eq(AuditoriaService.REPORTE_EXPORTADO),
				eq(AuditoriaService.ENTIDAD_REPORTE), eq(31L), eq(ResultadoAuditoria.EXITOSO), anyString());
	}

	@Test
	@DisplayName("CA-027 Exportar en Excel genera un .xlsx con los datos registrados")
	void exportaExcelConLosDatos_CA027() throws Exception {
		guardaRegistros();
		when(reservaRepo.contarPorCiudad()).thenReturn(List.of(new Object[] { "Cartagena", 5L },
				new Object[] { null, 1L }));

		ArchivoReporte archivo = servicio.exportar("demanda", "excel", ADMIN).getDato();

		assertThat(archivo.getNombreArchivo()).isEqualTo("reporte-demanda.xlsx");
		assertThat(archivo.getTipoContenido()).contains("spreadsheetml");
		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.getContenido()))) {
			Sheet hoja = libro.getSheetAt(0);
			int base = ExportadorUtil.FILA_ENCABEZADO_EXCEL;
			assertThat(hoja.getRow(base + 0).getCell(0).getStringCellValue()).isEqualTo("Ciudad");
			assertThat(hoja.getRow(base + 1).getCell(0).getStringCellValue()).isEqualTo("Cartagena");
			assertThat(hoja.getRow(base + 1).getCell(1).getStringCellValue()).isEqualTo("5");
			assertThat(hoja.getRow(base + 2).getCell(0).getStringCellValue()).isEqualTo("Sin ciudad");
		}
	}

	@Test
	@DisplayName("CA-027 El reporte de ingresos lista los pagos y cierra con los ingresos estimados")
	void reporteDeIngresos_CA027() throws Exception {
		guardaRegistros();
		Pago pago = new Pago();
		pago.setIdPago(9L);
		pago.setIdReserva(3L);
		pago.setEstado(EstadoPago.APROBADO);
		pago.setValor(new BigDecimal("600000"));
		when(pagoRepo.findAll()).thenReturn(List.of(pago));
		when(pagoRepo.sumarValores(any())).thenReturn(new BigDecimal("600000"));
		when(cancelacionRepo.sumarReembolsos()).thenReturn(new BigDecimal("100000"));

		ArchivoReporte archivo = servicio.exportar("ingresos", "excel", ADMIN).getDato();

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.getContenido()))) {
			Sheet hoja = libro.getSheetAt(0);
			int base = ExportadorUtil.FILA_ENCABEZADO_EXCEL;
			assertThat(hoja.getRow(base + 1).getCell(4).getStringCellValue()).isEqualTo("$ 600.000,00");
			assertThat(hoja.getRow(base + 2).getCell(3).getStringCellValue()).isEqualTo("Ingresos estimados");
			assertThat(hoja.getRow(base + 2).getCell(4).getStringCellValue()).isEqualTo("$ 500.000,00");
		}
	}

	@Test
	@DisplayName("CA-027 Un tipo o formato que no existe no genera archivo ni registro")
	void tipoOFormatoInvalido_CA027() {
		assertThat(servicio.exportar("clientes", "pdf", ADMIN).getMensaje()).isEqualTo(ReporteService.REPORTE_INVALIDO);
		assertThat(servicio.exportar("usuarios", "word", ADMIN).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.exportar(null, null, ADMIN).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);

		verify(exportacionRepo, never()).save(any());
		verify(auditoriaService, never()).registrar(any(), any(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("CA-027 El historial muestra quién exportó, qué y cuándo")
	void historial_CA027() {
		Pageable pagina = PageRequest.of(0, 20);
		RegistroExportacion registro = new RegistroExportacion();
		registro.setIdRegistro(31L);
		registro.setTipoReporte("reservas");
		registro.setFormato("excel");
		registro.setCantidadRegistros(15);
		registro.setIdUsuario(ADMIN);
		registro.setFecha(LocalDateTime.now());
		when(exportacionRepo.findAll(pagina)).thenReturn(new PageImpl<>(List.of(registro), pagina, 1));
		Usuario admin = new Usuario();
		admin.setIdUsuario(ADMIN);
		admin.setNombre("Admin General");
		when(usuarioRepo.findById(ADMIN)).thenReturn(Optional.of(admin));

		PaginaDTO<RegistroExportacionDTO> historial = servicio.historial(pagina);

		RegistroExportacionDTO dto = historial.getContenido().get(0);
		assertThat(dto.getNombreUsuario()).isEqualTo("Admin General");
		assertThat(dto.getIdUsuario()).isEqualTo(ADMIN);
		assertThat(dto.getFecha()).isEqualTo(registro.getFecha());
		assertThat(dto.getTipoReporte()).isEqualTo("reservas");
		assertThat(dto.getFormato()).isEqualTo("excel");
		assertThat(dto.getCantidadRegistros()).isEqualTo(15);
	}
}
