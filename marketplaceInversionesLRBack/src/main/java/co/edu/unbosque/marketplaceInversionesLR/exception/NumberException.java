package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * Se lanza cuando una contraseña no tiene ningún número. La lanza {@link ExceptionCheker}.
 */
public class NumberException extends Exception {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción con su mensaje.
	 */
	public NumberException() {
		super("Debe contener al menos un número.");
	}
}
