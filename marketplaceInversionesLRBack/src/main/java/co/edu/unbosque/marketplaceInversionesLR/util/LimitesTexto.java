package co.edu.unbosque.marketplaceInversionesLR.util;

/**
 * Largos máximos de los textos, iguales a los de las columnas de la base de
 * datos.
 */
public final class LimitesTexto {

	/** Textos cortos como nombres (VARCHAR 255). */
	public static final int CORTO = 255;

	/** Correo electrónico. */
	public static final int CORREO = 150;

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
}
