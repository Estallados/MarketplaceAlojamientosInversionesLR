package co.edu.unbosque.marketplaceInversionesLR.util;

/**
 * Largos máximos de los textos, iguales a los de las columnas de la base de
 * datos.
 */
public final class LimitesTexto {

	/** Textos cortos como nombres (VARCHAR 255). */
	public static final int CORTO = 255;

	/** Textos largos como descripciones y reglas (VARCHAR 2000). */
	public static final int LARGO = 2000;

	/** Correo electrónico. */
	public static final int CORREO = 150;

	/** Motivo de una decisión del administrador, por ejemplo un rechazo. */
	public static final int MOTIVO = 500;

	/** Texto de una notificación o de un motivo guardado con su prefijo. */
	public static final int MEDIO = 1000;

	private LimitesTexto() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Indica si un texto supera el máximo, sin contar los espacios de los lados.
	 *
	 * @param texto  texto a revisar; {@code null} no excede
	 * @param maximo largo máximo
	 * @return {@code true} si lo excede
	 */
	public static boolean excede(String texto, int maximo) {
		return texto != null && texto.trim().length() > maximo;
	}

	/**
	 * Corta un texto al máximo, para que siempre quepa en su columna.
	 *
	 * @param texto  texto a cortar; puede ser {@code null}
	 * @param maximo largo máximo
	 * @return el texto, cortado si hacía falta
	 */
	public static String recortar(String texto, int maximo) {
		return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
	}
}
