package co.edu.unbosque.marketplaceInversionesLR.exception;

/**
 * Se lanza cuando una contraseña no tiene entre 8 y 64 caracteres. La lanza {@link ExceptionCheker}.
 */
public class CharacterException extends Exception {

	/** Versión de serialización. */
	private static final long serialVersionUID = 1L;

	/**
	 * Crea la excepción con su mensaje.
	 */
	public CharacterException() {
		super("Debe tener entre 8 y 64 caracteres.");
	}
}
