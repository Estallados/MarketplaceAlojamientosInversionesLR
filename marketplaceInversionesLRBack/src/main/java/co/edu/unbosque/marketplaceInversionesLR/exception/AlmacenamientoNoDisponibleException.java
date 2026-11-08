package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * No se pudo guardar o leer una imagen en Cloudinary (falta la configuración o
 * el servicio no responde). La API responde 503 con el mensaje.
 */
public class AlmacenamientoNoDisponibleException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción.
	 *
	 * @param mensaje mensaje para el usuario
	 */
	public AlmacenamientoNoDisponibleException(String mensaje) {
		super(mensaje);
	}
}
