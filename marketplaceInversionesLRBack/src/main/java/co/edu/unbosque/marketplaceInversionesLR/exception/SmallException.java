package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * Se lanza cuando una contraseña no tiene ninguna minúscula. La lanza {@link ExceptionCheker}.
 */
public class SmallException extends Exception {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción con su mensaje.
	 */
	public SmallException() {
		super("Debe contener al menos una letra minúscula.");
	}
}
