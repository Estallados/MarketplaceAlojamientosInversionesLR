package co.edu.unbosque.marketplaceInversionesLR.exception;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Validaciones de datos de entrada, sobre todo de contraseñas (RNF-005,
 * CA-135).
 * <p>
 * Cada regla tiene su propio método y su propia excepción, para poder decirle
 * al usuario exactamente qué le falta a su contraseña. Una contraseña válida
 * tiene entre 8 y 64 caracteres, al menos una mayúscula, una minúscula, un
 * número y un símbolo.
 * </p>
 */
public final class ExceptionCheker {

	/** Formato de un correo: usuario, arroba, dominio y extensión de 2 letras o más. */
	private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

	/** Un dígito. */
	private static final Pattern DIGITO = Pattern.compile("\\d");

	/** Un carácter que no es letra ni número. */
	private static final Pattern SIMBOLO = Pattern.compile("[^A-Za-z0-9]");

	/** Largo mínimo de la contraseña. */
	private static final int MINIMO = 8;

	/** Largo máximo de la contraseña. */
	private static final int MAXIMO = 64;

	/** Bytes que usa BCrypt; lo que pase de aquí se ignoraría. */
	private static final int MAXIMO_BYTES = 72;

	private ExceptionCheker() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Verifica que una contraseña cumpla todas las reglas. Se detiene en la
	 * primera que falle.
	 *
	 * @param a contraseña a validar
	 * @throws CharacterException si no tiene entre 8 y 64 caracteres
	 * @throws CapitalException   si no tiene ninguna mayúscula
	 * @throws SmallException     si no tiene ninguna minúscula
	 * @throws NumberException    si no tiene ningún número
	 * @throws SymbolException    si no tiene ningún símbolo
	 */
	public static void checkerPasword(String a)
			throws CharacterException, CapitalException, SmallException, NumberException, SymbolException {
		checkerCharacter(a);
		checkerCapital(a);
		checkerSmall(a);
		checkerNumber(a);
		checkerSymbol(a);
	}

	/**
	 * Indica si una contraseña cumple todas las reglas de
	 * {@link #checkerPasword(String)}, sin lanzar excepciones.
	 *
	 * @param a contraseña a validar; {@code null} no es válida
	 * @return {@code true} si la contraseña es válida
	 */
	public static boolean contraseniaValida(String a) {
		if (a == null) {
			return false;
		}
		try {
			checkerPasword(a);
			return true;
		} catch (CharacterException | CapitalException | SmallException | NumberException | SymbolException e) {
			return false;
		}
	}

	/**
	 * Verifica que un texto tenga el formato de un correo.
	 *
	 * @param a correo a validar
	 * @throws MailException si el formato no es de correo
	 */
	public static void checkerMail(String a) throws MailException {
		if (a == null || !CORREO.matcher(a).matches()) {
			throw new MailException();
		}
	}

	/**
	 * Verifica que la contraseña tenga entre 8 y 64 caracteres y no pase de 72
	 * bytes. El máximo existe porque BCrypt solo usa los primeros 72 bytes: sin
	 * él, dos contraseñas distintas con el mismo comienzo abrirían la misma
	 * cuenta.
	 *
	 * @param a contraseña a validar
	 * @throws CharacterException si es muy corta o muy larga
	 */
	public static void checkerCharacter(String a) throws CharacterException {
		if (a.length() < MINIMO || a.length() > MAXIMO || a.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
			throw new CharacterException();
		}
	}

	/**
	 * Verifica que la contraseña tenga al menos una letra mayúscula.
	 *
	 * @param a contraseña a validar
	 * @throws CapitalException si no tiene mayúsculas
	 */
	public static void checkerCapital(String a) throws CapitalException {
		if (a.chars().noneMatch(Character::isUpperCase)) {
			throw new CapitalException();
		}
	}

	/**
	 * Verifica que la contraseña tenga al menos una letra minúscula.
	 *
	 * @param a contraseña a validar
	 * @throws SmallException si no tiene minúsculas
	 */
	public static void checkerSmall(String a) throws SmallException {
		if (a.chars().noneMatch(Character::isLowerCase)) {
			throw new SmallException();
		}
	}

	/**
	 * Verifica que la contraseña tenga al menos un número.
	 *
	 * @param a contraseña a validar
	 * @throws NumberException si no tiene números
	 */
	public static void checkerNumber(String a) throws NumberException {
		if (!DIGITO.matcher(a).find()) {
			throw new NumberException();
		}
	}

	/**
	 * Verifica que la contraseña tenga al menos un símbolo: un carácter que no
	 * sea letra ni número.
	 *
	 * @param a contraseña a validar
	 * @throws SymbolException si no tiene símbolos
	 */
	public static void checkerSymbol(String a) throws SymbolException {
		if (!SIMBOLO.matcher(a).find()) {
			throw new SymbolException();
		}
	}
}
