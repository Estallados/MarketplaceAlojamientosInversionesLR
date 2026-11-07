package co.edu.unbosque.marketplaceInversionesLR.util;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

/**
 * Códigos de un solo uso basados en tiempo (TOTP, RFC 6238), que uso para el
 * segundo factor de autenticación.
 * <p>
 * Son compatibles con Google Authenticator y apps parecidas: HMAC-SHA1, 6
 * dígitos y pasos de 30 segundos. El secreto se comparte con la app una sola
 * vez (por el código QR) y desde ahí la app y el servidor calculan el mismo
 * código con la hora actual.
 * </p>
 */
public final class TotpUtil {

	/** Duración de cada código en segundos. */
	private static final int PASO_SEGUNDOS = 30;

	/** Cantidad de dígitos del código. */
	private static final int DIGITOS = 6;

	/** 10 elevado a {@link #DIGITOS}: deja el número en 6 cifras. */
	private static final int MODULO = 1_000_000;

	/** Largo del secreto en bytes (160 bits, lo que recomienda el estándar). */
	private static final int BYTES_SECRETO = 20;

	/** Se aceptan el paso anterior y el siguiente por diferencias de reloj. */
	private static final int TOLERANCIA_PASOS = 1;

	/** Algoritmo del estándar TOTP. */
	private static final String ALGORITMO = "HmacSHA1";

	/** Generador de los secretos. */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Codificador Base32, el formato en que las apps reciben el secreto. */
	private static final Base32 BASE32 = new Base32();

	/** Clase utilitaria: no se instancia. */
	private TotpUtil() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Genera un secreto nuevo para un usuario.
	 *
	 * @return secreto en Base32, sin el relleno {@code =}
	 */
	public static String generarSecreto() {
		byte[] bytes = new byte[BYTES_SECRETO];
		RANDOM.nextBytes(bytes);
		return BASE32.encodeToString(bytes).replace("=", "");
	}

	/**
	 * Arma la URI {@code otpauth://} que el frontend convierte en código QR para
	 * registrar el secreto en la app autenticadora.
	 *
	 * @param emisor  nombre de la aplicación que se ve en la app
	 * @param cuenta  cuenta del usuario (su correo)
	 * @param secreto secreto en Base32
	 * @return URI con el secreto, el emisor, los dígitos y el periodo
	 */
	public static String uri(String emisor, String cuenta, String secreto) {
		return "otpauth://totp/" + emisor + ":" + cuenta + "?secret=" + secreto + "&issuer=" + emisor + "&digits="
				+ DIGITOS + "&period=" + PASO_SEGUNDOS;
	}

	/**
	 * Calcula el código que corresponde a un instante. Lo uso sobre todo en las
	 * pruebas para generar un código válido.
	 *
	 * @param secreto  secreto en Base32
	 * @param instante momento para el que se calcula el código
	 * @return código de 6 dígitos (con ceros a la izquierda si hace falta)
	 */
	public static String codigo(String secreto, Instant instante) {
		return codigoPara(secreto, instante.getEpochSecond() / PASO_SEGUNDOS);
	}

	/**
	 * Verifica un código que escribió el usuario. Acepta el paso actual y uno
	 * antes o después, por si el reloj del celular está un poco corrido. La
	 * comparación es de tiempo constante para no dar pistas por la demora.
	 *
	 * @param secreto secreto del usuario en Base32
	 * @param codigo  código que escribió el usuario
	 * @return {@code true} si el código es válido en esa ventana de tiempo
	 */
	public static boolean verificar(String secreto, String codigo) {
		if (secreto == null || codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
			return false;
		}
		long pasoActual = Instant.now().getEpochSecond() / PASO_SEGUNDOS;
		byte[] recibido = codigo.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		for (int i = -TOLERANCIA_PASOS; i <= TOLERANCIA_PASOS; i++) {
			byte[] esperado = codigoPara(secreto, pasoActual + i)
					.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
			if (MessageDigest.isEqual(esperado, recibido)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Calcula el código de un paso de tiempo (algoritmo de RFC 6238 / RFC 4226):
	 * HMAC del número de paso, truncado dinámico a 31 bits y módulo 10^6.
	 * <p>
	 * Sobre la advertencia de Sonar java:S4790: HMAC-SHA1 es el algoritmo del
	 * estándar TOTP y el que usan las apps autenticadoras; si lo cambio se pierde
	 * la compatibilidad y dejan de servir los segundos factores ya registrados.
	 * La debilidad de SHA-1 son las colisiones, que no afectan a HMAC. Además
	 * aquí no se guarda ni se compara ningún hash: solo se deriva un código de 6
	 * dígitos que vence a los 30 segundos.
	 * </p>
	 *
	 * @param secreto secreto en Base32
	 * @param paso    número de intervalos de 30 segundos desde 1970
	 * @return código de 6 dígitos
	 * @throws IllegalStateException si el proveedor criptográfico falla
	 */
	@SuppressWarnings("java:S4790")
	private static String codigoPara(String secreto, long paso) {
		try {
			byte[] clave = BASE32.decode(secreto);
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(new SecretKeySpec(clave, ALGORITMO));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
			int desplazamiento = hash[hash.length - 1] & 0x0f;
			int binario = ((hash[desplazamiento] & 0x7f) << 24) | ((hash[desplazamiento + 1] & 0xff) << 16)
					| ((hash[desplazamiento + 2] & 0xff) << 8) | (hash[desplazamiento + 3] & 0xff);
			return String.format("%0" + DIGITOS + "d", binario % MODULO);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("No se pudo calcular el código TOTP", e);
		}
	}
}
