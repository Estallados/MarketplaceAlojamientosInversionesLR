package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * Se lanza cuando un texto no tiene el formato de un correo. La lanza {@link ExceptionCheker}.
 */
public class MailException extends Exception {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción con su mensaje.
	 */
	public MailException() {
		super("Debe tener el formato de un correo electrónico.");
	}
}
