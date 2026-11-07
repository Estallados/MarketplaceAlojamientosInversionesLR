package co.edu.unbosque.marketplaceInversionesLR.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado de datos personales con AES-GCM y hash con HMAC-SHA256.
 * <p>
 * La clave se lee de la variable de entorno {@code AES_SECRET_KEY} (Base64 de
 * 16, 24 o 32 bytes). Es el mismo esquema del borrador {@code marketplace-backend}:
 * un IV aleatorio de 12 bytes delante del texto cifrado, todo en Base64. Con la
 * misma clave, los datos de Neon se leen igual desde los dos proyectos.
 * </p>
 */
public final class AESUtil {

	/** Algoritmo de la clave. */
	private static final String ALGORITMO = "AES";

	/** Cifrado autenticado. */
	private static final String TIPO_CIFRADO = "AES/GCM/NoPadding";

	/** Algoritmo del hash. */
	private static final String ALGORITMO_HASH = "HmacSHA256";

	/** Tamaño del IV en bytes. */
	private static final int TAMANO_IV = 12;

	/** Tamaño de la etiqueta de autenticación en bits. */
	private static final int TAMANO_TAG = 128;

	/** Tamaño mínimo de un texto cifrado: IV más la etiqueta. */
	private static final int TAMANO_MINIMO = TAMANO_IV + 16;

	/** Generador de IV aleatorios. */
	private static final SecureRandom RANDOM = new SecureRandom();

	private AESUtil() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Lee la clave de {@code AES_SECRET_KEY}.
	 *
	 * @return clave AES
	 * @throws IllegalStateException si falta o no tiene un tamaño válido
	 */
	private static SecretKeySpec obtenerClave() {
		String valor = System.getenv("AES_SECRET_KEY");
		if (valor == null || valor.isBlank()) {
			throw new IllegalStateException("No se ha configurado AES_SECRET_KEY");
		}
		byte[] clave;
		try {
			clave = Base64.getDecoder().decode(valor.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("AES_SECRET_KEY debe estar codificada en Base64", e);
		}
		if (clave.length != 16 && clave.length != 24 && clave.length != 32) {
			throw new IllegalStateException("La clave AES debe tener 16, 24 o 32 bytes");
		}
		return new SecretKeySpec(clave, ALGORITMO);
	}

	/**
	 * Crea el cifrador para un modo y un IV.
	 *
	 * @param modo {@link Cipher#ENCRYPT_MODE} o {@link Cipher#DECRYPT_MODE}
	 * @param iv   vector de inicialización
	 * @return cifrador listo
	 * @throws GeneralSecurityException si el algoritmo falla
	 */
	private static Cipher crearCipher(int modo, byte[] iv) throws GeneralSecurityException {
		Cipher cipher = Cipher.getInstance(TIPO_CIFRADO);
		cipher.init(modo, obtenerClave(), new GCMParameterSpec(TAMANO_TAG, iv));
		return cipher;
	}

	/**
	 * Cifra un texto.
	 *
	 * @param texto texto en claro
	 * @return IV y texto cifrado, en Base64
	 */
	public static String encrypt(String texto) {
		Objects.requireNonNull(texto, "El texto no puede ser null");
		try {
			byte[] iv = new byte[TAMANO_IV];
			RANDOM.nextBytes(iv);
			byte[] cifrado = crearCipher(Cipher.ENCRYPT_MODE, iv).doFinal(texto.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder()
					.encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Error al cifrar el texto", e);
		}
	}

	/**
	 * Descifra un texto cifrado con {@link #encrypt(String)}.
	 *
	 * @param encrypted IV y texto cifrado, en Base64
	 * @return texto en claro
	 * @throws IllegalArgumentException si el formato no es válido o se cifró con
	 *                                  otra clave
	 */
	public static String decrypt(String encrypted) {
		Objects.requireNonNull(encrypted, "El texto cifrado no puede ser null");
		try {
			byte[] datos = Base64.getDecoder().decode(encrypted);
			if (datos.length < TAMANO_MINIMO) {
				throw new IllegalArgumentException("Los datos cifrados no tienen un formato válido");
			}
			ByteBuffer buffer = ByteBuffer.wrap(datos);
			byte[] iv = new byte[TAMANO_IV];
			buffer.get(iv);
			byte[] cifrado = new byte[buffer.remaining()];
			buffer.get(cifrado);
			return new String(crearCipher(Cipher.DECRYPT_MODE, iv).doFinal(cifrado), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException("No fue posible descifrar el texto", e);
		}
	}

	/**
	 * HMAC-SHA256 de un texto: siempre da lo mismo para el mismo texto, por eso
	 * sirve para buscar datos cifrados.
	 *
	 * @param texto texto en claro
	 * @return hash en Base64
	 */
	public static String hash(String texto) {
		Objects.requireNonNull(texto, "El texto no puede ser null");
		try {
			Mac mac = Mac.getInstance(ALGORITMO_HASH);
			mac.init(new SecretKeySpec(obtenerClave().getEncoded(), ALGORITMO_HASH));
			return Base64.getEncoder().encodeToString(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Error al calcular el hash", e);
		}
	}

	/**
	 * Deriva de {@code AES_SECRET_KEY} una clave para otro uso (por ejemplo,
	 * firmar los JWT), así no hay que configurar otro secreto.
	 *
	 * @param proposito uso de la clave, por ejemplo "jwt"
	 * @return 32 bytes de clave
	 */
	public static byte[] derivarClave(String proposito) {
		Objects.requireNonNull(proposito, "El propósito no puede ser null");
		try {
			Mac mac = Mac.getInstance(ALGORITMO_HASH);
			mac.init(new SecretKeySpec(obtenerClave().getEncoded(), ALGORITMO_HASH));
			return mac.doFinal(("clave-derivada:" + proposito).getBytes(StandardCharsets.UTF_8));
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Error al derivar la clave", e);
		}
	}
}
