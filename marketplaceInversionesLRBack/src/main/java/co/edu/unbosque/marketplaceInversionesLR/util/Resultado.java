package co.edu.unbosque.marketplaceInversionesLR.util;

/**
 * Resultado de una operación de un servicio.
 * <p>
 * Junta tres cosas: un código numérico que dice cómo terminó la operación, el
 * dato que produjo cuando salió bien y un mensaje con la causa cuando fue
 * rechazada. Así los servicios no dependen de HTTP: son los controladores los
 * que traducen el código a una respuesta con
 * {@link RespuestaUtil#desde(Resultado)}.
 * </p>
 * <p>
 * Códigos que uso en todo el proyecto:
 * </p>
 * <ul>
 * <li>{@link #EXITO} (0): la operación se realizó.</li>
 * <li>{@link #NO_ENCONTRADO} (1): el recurso no existe o el usuario no puede
 * verlo.</li>
 * <li>{@link #DATOS_INVALIDOS} (2): faltan datos o no son válidos.</li>
 * <li>{@link #SIN_PERMISO} (3): el usuario no es el dueño ni administrador.</li>
 * <li>{@link #ESTADO_INVALIDO} (4): el estado actual no permite la
 * operación.</li>
 * <li>{@link #NO_DISPONIBLE} (5): las fechas pedidas no están disponibles.</li>
 * <li>{@link #PAGO_RECHAZADO} (6): el pago de prueba fue rechazado.</li>
 * <li>{@link #PAGO_ERROR} (7): no se pudo confirmar el pago con la
 * pasarela.</li>
 * </ul>
 *
 * @param <T> tipo del dato que produce la operación
 */
public final class Resultado<T> {

	/** La operación se realizó correctamente. */
	public static final int EXITO = 0;

	/** El recurso no existe o el usuario no tiene acceso a él. */
	public static final int NO_ENCONTRADO = 1;

	/** Faltan datos o los enviados no son válidos. */
	public static final int DATOS_INVALIDOS = 2;

	/** El usuario no es el dueño del recurso ni administrador. */
	public static final int SIN_PERMISO = 3;

	/** El estado actual del recurso no permite la operación. */
	public static final int ESTADO_INVALIDO = 4;

	/** Las fechas pedidas no están disponibles. */
	public static final int NO_DISPONIBLE = 5;

	/** La pasarela de pagos rechazó el pago. */
	public static final int PAGO_RECHAZADO = 6;

	/** No se pudo confirmar el pago (falla de comunicación con la pasarela). */
	public static final int PAGO_ERROR = 7;

	/** Código con el que terminó la operación. */
	private final int codigo;

	/** Dato producido; normalmente {@code null} cuando la operación falla. */
	private final T dato;

	/** Causa del rechazo, pensada para mostrarla al usuario. */
	private final String mensaje;

	/**
	 * Constructor privado: los resultados se crean con {@link #exito(Object)} o
	 * con alguno de los métodos {@code error}.
	 *
	 * @param codigo  código de la operación
	 * @param dato    dato producido
	 * @param mensaje causa del rechazo
	 */
	private Resultado(int codigo, T dato, String mensaje) {
		this.codigo = codigo;
		this.dato = dato;
		this.mensaje = mensaje;
	}

	/**
	 * Crea un resultado exitoso.
	 *
	 * @param <T>  tipo del dato
	 * @param dato dato producido por la operación
	 * @return resultado con código {@link #EXITO}
	 */
	public static <T> Resultado<T> exito(T dato) {
		return new Resultado<>(EXITO, dato, null);
	}

	/**
	 * Crea un resultado rechazado.
	 *
	 * @param <T>     tipo del dato
	 * @param codigo  código del error (ver las constantes de esta clase)
	 * @param mensaje causa del rechazo para el usuario
	 * @return resultado sin dato
	 */
	public static <T> Resultado<T> error(int codigo, String mensaje) {
		return new Resultado<>(codigo, null, mensaje);
	}

	/**
	 * Crea un resultado rechazado que igual entrega un dato. Lo uso, por ejemplo,
	 * cuando el pago se rechaza y aun así devuelvo la reserva ya cancelada.
	 *
	 * @param <T>     tipo del dato
	 * @param codigo  código del error
	 * @param mensaje causa del rechazo
	 * @param dato    dato que acompaña al error
	 * @return resultado con código de error y dato
	 */
	public static <T> Resultado<T> error(int codigo, String mensaje, T dato) {
		return new Resultado<>(codigo, dato, mensaje);
	}

	/**
	 * Devuelve {@code true} si la operación terminó con {@link #EXITO}.
	 *
	 * @return {@code true} si la operación terminó con {@link #EXITO}
	 */
	public boolean esExito() {
		return codigo == EXITO;
	}

	/**
	 * Devuelve el código con el que terminó la operación.
	 *
	 * @return código con el que terminó la operación
	 */
	public int getCodigo() {
		return codigo;
	}

	/**
	 * Devuelve el dato producido, o {@code null} si no hay.
	 *
	 * @return dato producido, o {@code null} si no hay
	 */
	public T getDato() {
		return dato;
	}

	/**
	 * Devuelve la causa del rechazo, o {@code null} si la operación fue exitosa.
	 *
	 * @return causa del rechazo, o {@code null} si la operación fue exitosa
	 */
	public String getMensaje() {
		return mensaje;
	}
}
