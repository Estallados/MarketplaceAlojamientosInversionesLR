package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * Se lanza cuando una contraseña no tiene ningún símbolo. La lanza {@link ExceptionCheker}.
 */
public class SymbolException extends Exception {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción con su mensaje.
	 */
	public SymbolException() {
		super("Debe contener al menos un símbolo.");
	}
}
