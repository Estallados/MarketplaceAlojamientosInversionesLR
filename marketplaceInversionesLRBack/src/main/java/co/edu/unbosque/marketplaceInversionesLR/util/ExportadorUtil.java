package co.edu.unbosque.marketplaceInversionesLR.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Genera los reportes en forma de tabla, en PDF (con OpenPDF) o en Excel (con
 * Apache POI), con la misma presentación en los dos: encabezado con el nombre
 * de la plataforma y el título, fecha de generación y cantidad de registros,
 * tabla con encabezado de color y filas alternadas.
 * <p>
 * Las dos salidas reciben lo mismo: un título, los encabezados de las columnas
 * y las filas como listas de textos. Así el servicio de reportes arma los datos
 * una sola vez y solo elige el formato al final. Una fila cuyo primer valor es
 * vacío se trata como fila de total (por ejemplo, los ingresos estimados) y se
 * resalta.
 * </p>
 */
public final class ExportadorUtil {

	/** Nombre de la plataforma que encabeza cada reporte. */
	public static final String MARCA = "Marketplace de Alojamientos · Inversiones LR";

	/** Fila de Excel (desde 0) donde van los encabezados de las columnas. */
	public static final int FILA_ENCABEZADO_EXCEL = 3;

	/** Formato de la fecha de generación. */
	private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	/** Color principal (azul oscuro) del encabezado y de la tabla. */
	private static final Color PRIMARIO = new Color(0x1F, 0x3A, 0x5F);

	/** Color de acento para la línea bajo el encabezado. */
	private static final Color ACENTO = new Color(0x2E, 0x86, 0xC1);

	/** Fondo de las filas pares. */
	private static final Color FILA_ALTERNA = new Color(0xF2, 0xF6, 0xFA);

	/** Fondo de la fila de total. */
	private static final Color FILA_TOTAL = new Color(0xDC, 0xE9, 0xF5);

	/** Color de los bordes de la tabla. */
	private static final Color BORDE = new Color(0xD0, 0xD7, 0xDE);

	/** Color del texto secundario. */
	private static final Color TEXTO_SECUNDARIO = new Color(0x5F, 0x6B, 0x7A);

	/** Ancho mínimo de una columna de Excel, en caracteres. */
	private static final int ANCHO_MINIMO = 10;

	/** Ancho máximo de una columna de Excel, en caracteres. */
	private static final int ANCHO_MAXIMO = 50;

	/** Clase utilitaria: no se instancia. */
	private ExportadorUtil() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Genera un PDF horizontal con el encabezado de la plataforma, el título, la
	 * fecha de generación, el número de registros, la tabla con los datos (su
	 * encabezado se repite en cada página) y el número de página al pie.
	 *
	 * @param titulo      título del reporte
	 * @param encabezados nombres de las columnas
	 * @param filas       valores de cada fila, en el mismo orden de los
	 *                    encabezados; los {@code null} se imprimen vacíos
	 * @return bytes del PDF
	 * @throws IllegalStateException si OpenPDF no puede armar el documento
	 */
	public static byte[] pdf(String titulo, List<String> encabezados, List<List<String>> filas) {
		ByteArrayOutputStream salida = new ByteArrayOutputStream();
		Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
		try {
			PdfWriter escritor = PdfWriter.getInstance(documento, salida);
			escritor.setPageEvent(new PiePagina());
			documento.addTitle(titulo);
			documento.addCreator(MARCA);
			documento.open();
			documento.add(encabezadoPdf(titulo, filas.size()));
			documento.add(tablaPdf(encabezados, filas));
		} catch (DocumentException e) {
			throw new IllegalStateException("No se pudo generar el PDF", e);
		} finally {
			documento.close();
		}
		return salida.toByteArray();
	}

	/**
	 * Banda superior del PDF: marca, título y resumen.
	 *
	 * @param titulo    título del reporte
	 * @param registros cantidad de registros
	 * @return tabla de una celda con el encabezado
	 */
	private static PdfPTable encabezadoPdf(String titulo, int registros) {
		PdfPTable banda = new PdfPTable(1);
		banda.setWidthPercentage(100);
		banda.setSpacingAfter(14);

		Paragraph marca = new Paragraph(MARCA.toUpperCase(),
				FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(0xBF, 0xD7, 0xEA)));
		Paragraph encabezado = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.WHITE));
		encabezado.setSpacingBefore(4);
		encabezado.setLeading(24);
		Paragraph resumen = new Paragraph("Generado el " + LocalDateTime.now().format(FORMATO_FECHA) + "   ·   "
				+ registros + (registros == 1 ? " registro" : " registros"),
				FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0xDD, 0xE6, 0xEF)));
		resumen.setSpacingBefore(4);

		PdfPCell celda = new PdfPCell();
		celda.addElement(marca);
		celda.addElement(encabezado);
		celda.addElement(resumen);
		celda.setBackgroundColor(PRIMARIO);
		celda.setBorder(Rectangle.BOTTOM);
		celda.setBorderColorBottom(ACENTO);
		celda.setBorderWidthBottom(4);
		celda.setPaddingTop(10);
		celda.setPaddingBottom(14);
		celda.setPaddingLeft(16);
		celda.setPaddingRight(16);
		banda.addCell(celda);
		return banda;
	}

	/**
	 * Tabla de datos del PDF con encabezado de color, filas alternadas y la fila
	 * de total resaltada.
	 *
	 * @param encabezados nombres de las columnas
	 * @param filas       valores de cada fila
	 * @return la tabla
	 */
	private static PdfPTable tablaPdf(List<String> encabezados, List<List<String>> filas) {
		PdfPTable tabla = new PdfPTable(encabezados.size());
		tabla.setWidthPercentage(100);
		tabla.setHeaderRows(1);
		int[] anchos = anchos(encabezados, filas);
		float[] relativos = new float[anchos.length];
		for (int i = 0; i < anchos.length; i++) {
			relativos[i] = anchos[i];
		}
		tabla.setWidths(relativos);

		Font fuenteEncabezado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
		for (String encabezado : encabezados) {
			PdfPCell celda = celdaPdf(encabezado, fuenteEncabezado, PRIMARIO);
			celda.setHorizontalAlignment(Element.ALIGN_CENTER);
			celda.setPaddingTop(7);
			celda.setPaddingBottom(7);
			tabla.addCell(celda);
		}

		if (filas.isEmpty()) {
			PdfPCell vacia = celdaPdf("No hay registros para mostrar.",
					FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, TEXTO_SECUNDARIO), Color.WHITE);
			vacia.setColspan(encabezados.size());
			vacia.setHorizontalAlignment(Element.ALIGN_CENTER);
			vacia.setPadding(12);
			tabla.addCell(vacia);
			return tabla;
		}

		Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x22, 0x2B, 0x36));
		Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, PRIMARIO);
		for (int f = 0; f < filas.size(); f++) {
			List<String> fila = filas.get(f);
			boolean total = esTotal(fila);
			Color fondo = total ? FILA_TOTAL : (f % 2 == 1 ? FILA_ALTERNA : Color.WHITE);
			for (String valor : fila) {
				tabla.addCell(celdaPdf(valor == null ? "" : valor, total ? negrita : normal, fondo));
			}
		}
		return tabla;
	}

	/**
	 * Celda del PDF con borde fino y relleno.
	 *
	 * @param texto  contenido
	 * @param fuente fuente del texto
	 * @param fondo  color de fondo
	 * @return la celda
	 */
	private static PdfPCell celdaPdf(String texto, Font fuente, Color fondo) {
		PdfPCell celda = new PdfPCell(new Phrase(texto, fuente));
		celda.setBackgroundColor(fondo);
		celda.setBorderColor(BORDE);
		celda.setBorderWidth(0.5f);
		celda.setPadding(5);
		celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
		return celda;
	}

	/** Escribe la marca y el número de página al pie de cada página del PDF. */
	private static final class PiePagina extends PdfPageEventHelper {

		/**
		 * Dibuja el pie al terminar cada página.
		 *
		 * @param writer   escritor del PDF
		 * @param document documento
		 */
		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			Font fuente = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXTO_SECUNDARIO);
			float y = document.bottom() - 20;
			ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, new Phrase(MARCA, fuente),
					document.left(), y, 0);
			ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
					new Phrase("Página " + writer.getPageNumber(), fuente), document.right(), y, 0);
		}
	}

	/**
	 * Genera un libro de Excel con una sola hoja: título y resumen arriba,
	 * encabezados de color en la fila {@value #FILA_ENCABEZADO_EXCEL} (fijos al
	 * desplazarse y con filtros), filas alternadas, la fila de total resaltada y
	 * anchos de columna según el contenido.
	 *
	 * @param titulo      título del reporte y nombre de la hoja; Excel solo
	 *                    admite 31 caracteres en el nombre, así que se corta si
	 *                    es más largo
	 * @param encabezados nombres de las columnas
	 * @param filas       valores de cada fila; los {@code null} quedan vacíos
	 * @return bytes del archivo .xlsx
	 * @throws IllegalStateException si POI no puede escribir el archivo
	 */
	public static byte[] excel(String titulo, List<String> encabezados, List<List<String>> filas) {
		try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
			Sheet hoja = libro.createSheet(titulo.length() > 31 ? titulo.substring(0, 31) : titulo);
			int columnas = encabezados.size();
			int ultimaColumna = Math.max(columnas - 1, 0);

			// Título y resumen
			Row filaTitulo = hoja.createRow(0);
			filaTitulo.setHeightInPoints(30);
			XSSFCellStyle estiloTitulo = estilo(libro, PRIMARIO, fuente(libro, true, 16, Color.WHITE));
			estiloTitulo.setVerticalAlignment(VerticalAlignment.CENTER);
			for (int c = 0; c <= ultimaColumna; c++) {
				filaTitulo.createCell(c).setCellStyle(estiloTitulo);
			}
			filaTitulo.getCell(0).setCellValue(titulo);

			Row filaResumen = hoja.createRow(1);
			XSSFCellStyle estiloResumen = libro.createCellStyle();
			estiloResumen.setFont(fuente(libro, false, 9, TEXTO_SECUNDARIO));
			filaResumen.createCell(0).setCellValue(MARCA + "   ·   Generado el "
					+ LocalDateTime.now().format(FORMATO_FECHA) + "   ·   " + filas.size()
					+ (filas.size() == 1 ? " registro" : " registros"));
			filaResumen.getCell(0).setCellStyle(estiloResumen);
			if (ultimaColumna > 0) {
				hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, ultimaColumna));
				hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, ultimaColumna));
			}

			// Encabezados
			XSSFCellStyle estiloEncabezado = conBordes(estilo(libro, PRIMARIO, fuente(libro, true, 10, Color.WHITE)));
			estiloEncabezado.setAlignment(HorizontalAlignment.CENTER);
			estiloEncabezado.setVerticalAlignment(VerticalAlignment.CENTER);
			Row encabezado = hoja.createRow(FILA_ENCABEZADO_EXCEL);
			encabezado.setHeightInPoints(22);
			for (int i = 0; i < columnas; i++) {
				encabezado.createCell(i).setCellValue(encabezados.get(i));
				encabezado.getCell(i).setCellStyle(estiloEncabezado);
			}

			// Datos
			XSSFFont fuenteNormal = fuente(libro, false, 10, new Color(0x22, 0x2B, 0x36));
			XSSFCellStyle estiloPar = conBordes(estilo(libro, Color.WHITE, fuenteNormal));
			XSSFCellStyle estiloImpar = conBordes(estilo(libro, FILA_ALTERNA, fuenteNormal));
			XSSFCellStyle estiloTotal = conBordes(estilo(libro, FILA_TOTAL, fuente(libro, true, 10, PRIMARIO)));
			for (int f = 0; f < filas.size(); f++) {
				Row fila = hoja.createRow(FILA_ENCABEZADO_EXCEL + 1 + f);
				List<String> valores = filas.get(f);
				XSSFCellStyle estiloFila = esTotal(valores) ? estiloTotal : (f % 2 == 1 ? estiloImpar : estiloPar);
				for (int c = 0; c < valores.size(); c++) {
					fila.createCell(c).setCellValue(valores.get(c) == null ? "" : valores.get(c));
					fila.getCell(c).setCellStyle(estiloFila);
				}
			}

			// Anchos según el contenido: autoSizeColumn necesita las fuentes del sistema
			// y falla en servidores sin interfaz gráfica
			int[] anchos = anchos(encabezados, filas);
			for (int i = 0; i < columnas; i++) {
				hoja.setColumnWidth(i, (anchos[i] + 3) * 256);
			}
			hoja.createFreezePane(0, FILA_ENCABEZADO_EXCEL + 1);
			if (columnas > 0) {
				hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADO_EXCEL,
						FILA_ENCABEZADO_EXCEL + Math.max(filas.size(), 1), 0, ultimaColumna));
			}
			hoja.getPrintSetup().setLandscape(true);
			hoja.setFitToPage(true);
			hoja.getPrintSetup().setFitWidth((short) 1);
			hoja.getPrintSetup().setFitHeight((short) 0);

			libro.write(salida);
			return salida.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("No se pudo generar el Excel", e);
		}
	}

	/**
	 * Estilo de Excel con fondo sólido y fuente.
	 *
	 * @param libro  libro de Excel
	 * @param fondo  color de fondo
	 * @param fuente fuente del texto
	 * @return el estilo
	 */
	private static XSSFCellStyle estilo(XSSFWorkbook libro, Color fondo, XSSFFont fuente) {
		XSSFCellStyle estilo = libro.createCellStyle();
		estilo.setFillForegroundColor(color(fondo));
		estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		estilo.setFont(fuente);
		return estilo;
	}

	/**
	 * Agrega bordes finos de color gris claro al estilo.
	 *
	 * @param estilo estilo a modificar
	 * @return el mismo estilo
	 */
	private static XSSFCellStyle conBordes(XSSFCellStyle estilo) {
		estilo.setBorderTop(BorderStyle.THIN);
		estilo.setBorderBottom(BorderStyle.THIN);
		estilo.setBorderLeft(BorderStyle.THIN);
		estilo.setBorderRight(BorderStyle.THIN);
		XSSFColor borde = color(BORDE);
		estilo.setTopBorderColor(borde);
		estilo.setBottomBorderColor(borde);
		estilo.setLeftBorderColor(borde);
		estilo.setRightBorderColor(borde);
		return estilo;
	}

	/**
	 * Fuente de Excel.
	 *
	 * @param libro   libro de Excel
	 * @param negrita si va en negrita
	 * @param tamanio tamaño en puntos
	 * @param color   color del texto
	 * @return la fuente
	 */
	private static XSSFFont fuente(XSSFWorkbook libro, boolean negrita, int tamanio, Color color) {
		XSSFFont fuente = libro.createFont();
		fuente.setFontName("Calibri");
		fuente.setBold(negrita);
		fuente.setFontHeightInPoints((short) tamanio);
		fuente.setColor(color(color));
		return fuente;
	}

	/**
	 * Convierte un color RGB al color de POI.
	 *
	 * @param color color RGB
	 * @return color de POI
	 */
	private static XSSFColor color(Color color) {
		return new XSSFColor(new byte[] { (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue() },
				null);
	}

	/**
	 * Ancho de cada columna en caracteres: el texto más largo entre el
	 * encabezado y sus valores, entre {@value #ANCHO_MINIMO} y
	 * {@value #ANCHO_MAXIMO}.
	 *
	 * @param encabezados nombres de las columnas
	 * @param filas       valores de cada fila
	 * @return ancho de cada columna
	 */
	private static int[] anchos(List<String> encabezados, List<List<String>> filas) {
		int[] anchos = new int[encabezados.size()];
		for (int i = 0; i < anchos.length; i++) {
			anchos[i] = encabezados.get(i).length();
		}
		for (List<String> fila : filas) {
			for (int c = 0; c < Math.min(fila.size(), anchos.length); c++) {
				anchos[c] = Math.max(anchos[c], fila.get(c) == null ? 0 : fila.get(c).length());
			}
		}
		for (int i = 0; i < anchos.length; i++) {
			anchos[i] = Math.min(Math.max(anchos[i], ANCHO_MINIMO), ANCHO_MAXIMO);
		}
		return anchos;
	}

	/**
	 * Indica si la fila es de total: su primer valor está vacío.
	 *
	 * @param fila valores de la fila
	 * @return {@code true} si es fila de total
	 */
	private static boolean esTotal(List<String> fila) {
		return !fila.isEmpty() && (fila.get(0) == null || fila.get(0).isEmpty());
	}
}
