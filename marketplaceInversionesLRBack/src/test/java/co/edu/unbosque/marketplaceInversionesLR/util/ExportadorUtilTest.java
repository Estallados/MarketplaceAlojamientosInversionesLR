package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la presentación de los reportes en PDF y Excel (HU-05).
 */
class ExportadorUtilTest {

	/** Encabezados de ejemplo. */
	private static final List<String> ENCABEZADOS = List.of("Pago", "Reserva", "Fecha", "Estado", "Valor");

	/** Filas de ejemplo, con una fila de total al final. */
	private static final List<List<String>> FILAS = List.of(
			List.of("1", "10", "2026-11-01 10:00", "APROBADO", "$ 600.000,00"),
			List.of("2", "11", "2026-11-02 11:00", "REEMBOLSADO", "$ 400.000,00"),
			List.of("", "", "", "Ingresos estimados", "$ 900.000,00"));

	@Test
	@DisplayName("CA-027 El Excel lleva título, resumen, encabezados de color fijos y la fila de total resaltada")
	void excelConPresentacion_CA027() throws Exception {
		byte[] excel = ExportadorUtil.excel("Reporte de ingresos", ENCABEZADOS, FILAS);

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			Sheet hoja = libro.getSheetAt(0);
			int base = ExportadorUtil.FILA_ENCABEZADO_EXCEL;
			assertThat(hoja.getSheetName()).isEqualTo("Reporte de ingresos");
			assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Reporte de ingresos");
			assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).contains(ExportadorUtil.MARCA, "3 registros");
			assertThat(hoja.getMergedRegions()).hasSize(2);

			XSSFCellStyle encabezado = (XSSFCellStyle) hoja.getRow(base).getCell(0).getCellStyle();
			assertThat(hoja.getRow(base).getCell(4).getStringCellValue()).isEqualTo("Valor");
			assertThat(encabezado.getFont().getBold()).isTrue();
			assertThat(encabezado.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
			assertThat(hoja.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) (base + 1));

			XSSFCellStyle total = (XSSFCellStyle) hoja.getRow(base + 3).getCell(4).getCellStyle();
			XSSFCellStyle normal = (XSSFCellStyle) hoja.getRow(base + 1).getCell(4).getCellStyle();
			assertThat(total.getFont().getBold()).isTrue();
			assertThat(normal.getFont().getBold()).isFalse();
			assertThat(hoja.getColumnWidth(2)).isGreaterThan(hoja.getColumnWidth(0));
		}
	}

	@Test
	@DisplayName("CA-027 El PDF se genera con datos y también sin registros")
	void pdfConYSinDatos_CA027() {
		byte[] conDatos = ExportadorUtil.pdf("Reporte de ingresos", ENCABEZADOS, FILAS);
		byte[] vacio = ExportadorUtil.pdf("Reporte de reservas", ENCABEZADOS, List.of());

		assertThat(new String(conDatos, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
		assertThat(new String(vacio, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
	}

	@Test
	@DisplayName("CA-027 Un reporte de una sola columna o sin filas también se exporta a Excel")
	void excelDeUnaColumna_CA027() throws Exception {
		byte[] excel = ExportadorUtil.excel("Un título muy largo que supera los treinta y un caracteres",
				List.of("Ciudad"), List.of());

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			assertThat(libro.getSheetAt(0).getSheetName()).hasSize(31);
			assertThat(libro.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).contains("0 registros");
		}
	}
}
