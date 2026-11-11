package co.edu.unbosque.marketplaceInversionesLR.util;

/**
 * Archivo que produce la exportación de un reporte (PDF o Excel), listo para
 * enviarlo como descarga.
 * <p>
 * Es inmutable: guarda una copia del contenido al crearse y entrega otra copia
 * cada vez que se consulta, para que nadie pueda modificar los bytes desde
 * afuera.
 * </p>
 */
public final class ArchivoReporte {

	/** Nombre con el que se descarga el archivo, por ejemplo {@code reservas.pdf}. */
	private final String nombreArchivo;

	/** Tipo MIME del archivo, que se envía en la cabecera {@code Content-Type}. */
	private final String tipoContenido;

	/** Bytes del archivo generado. */
	private final byte[] contenido;

	/**
	 * Crea el archivo del reporte.
	 *
	 * @param nombreArchivo nombre con el que se descargará
	 * @param tipoContenido tipo MIME del contenido
	 * @param contenido     bytes del archivo; se guarda una copia
	 */
	public ArchivoReporte(String nombreArchivo, String tipoContenido, byte[] contenido) {
		this.nombreArchivo = nombreArchivo;
		this.tipoContenido = tipoContenido;
		this.contenido = contenido.clone();
	}

	/**
	 * Devuelve el nombre con el que se descarga el archivo.
	 *
	 * @return nombre con el que se descarga el archivo
	 */
	public String getNombreArchivo() {
		return nombreArchivo;
	}

	/**
	 * Devuelve el tipo MIME del archivo.
	 *
	 * @return tipo MIME del archivo
	 */
	public String getTipoContenido() {
		return tipoContenido;
	}

	/**
	 * Devuelve la copia de los bytes del archivo.
	 *
	 * @return copia de los bytes del archivo
	 */
	public byte[] getContenido() {
		return contenido.clone();
	}
}
