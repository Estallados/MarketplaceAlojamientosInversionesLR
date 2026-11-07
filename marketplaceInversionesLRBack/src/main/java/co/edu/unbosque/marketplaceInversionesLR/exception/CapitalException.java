package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * Se lanza cuando una contraseña no tiene ninguna mayúscula. La lanza {@link ExceptionCheker}.
 */
public class CapitalException extends Exception {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción con su mensaje.
	 */
	public CapitalException() {
		super("Debe contener al menos una letra mayúscula.");
	}
}
