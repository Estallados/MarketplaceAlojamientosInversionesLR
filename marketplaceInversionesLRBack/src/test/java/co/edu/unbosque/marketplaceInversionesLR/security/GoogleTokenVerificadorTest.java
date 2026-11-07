package co.edu.unbosque.marketplaceInversionesLR.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.security.GoogleTokenVerificador.CuentaGoogle;
import io.jsonwebtoken.Jwts;

/**
 * Pruebas unitarias del verificador del ID token de Google. Los tokens se
 * firman con una clave RSA de prueba que reemplaza a las claves de Google.
 */
class GoogleTokenVerificadorTest {

	/** Client ID esperado como audiencia. */
	private static final String CLIENTE = "cliente-prueba.apps.googleusercontent.com";

	/** Identificador de la clave de prueba. */
	private static final String ID_CLAVE = "clave-prueba";

	/** Correo de la cuenta de Google de prueba. */
	private static final String CORREO = "ana@gmail.com";

	/** Claves con las que "firma Google". */
	private static final KeyPair CLAVES = generarClaves();

	/** Claves de un tercero que no es Google. */
	private static final KeyPair OTRAS_CLAVES = generarClaves();

	/** Verificador que conoce solo la clave de prueba. */
	private final GoogleTokenVerificador verificador = new GoogleTokenVerificador(CLIENTE,
			id -> ID_CLAVE.equals(id) ? CLAVES.getPublic() : null);

	@Test
	@DisplayName("Un ID token válido de Google entrega el id, el correo y el nombre de la cuenta")
	void tokenValido() {
		Optional<CuentaGoogle> cuenta = verificador.verificar(token(Map.of()));

		assertThat(cuenta).isPresent();
		assertThat(cuenta.get().correo()).isEqualTo(CORREO);
		assertThat(cuenta.get().nombre()).isEqualTo("Ana María Prieto");
		assertThat(cuenta.get().idGoogle()).isEqualTo("109876");
	}

	@Test
	@DisplayName("Acepta email_verified como texto y el emisor sin esquema")
	void variantesAceptadas() {
		assertThat(verificador.verificar(token(Map.of("email_verified", "true", "iss", "accounts.google.com"))))
				.isPresent();
	}

	@Test
	@DisplayName("Rechaza tokens de otra aplicación, de otro emisor, vencidos o con el correo sin verificar")
	void rechazaClaimsInvalidos() {
		assertThat(verificador.verificar(token(Map.of("aud", "otra-app.apps.googleusercontent.com")))).isEmpty();
		assertThat(verificador.verificar(token(Map.of("iss", "https://evil.example.com")))).isEmpty();
		assertThat(verificador.verificar(token(Map.of("email_verified", false)))).isEmpty();
		assertThat(verificador.verificar(token(Map.of("email", " ")))).isEmpty();
		assertThat(verificador.verificar(token(Map.of("exp", Instant.now().minusSeconds(600).getEpochSecond()))))
				.isEmpty();
	}

	@Test
	@DisplayName("Rechaza tokens firmados con otra clave, sin identificador de clave o mal formados")
	void rechazaFirmasInvalidas() {
		assertThat(verificador.verificar(token(Map.of(), OTRAS_CLAVES.getPrivate(), ID_CLAVE))).isEmpty();
		assertThat(verificador.verificar(token(Map.of(), CLAVES.getPrivate(), "clave-desconocida"))).isEmpty();
		assertThat(verificador.verificar(token(Map.of(), CLAVES.getPrivate(), null))).isEmpty();
		assertThat(verificador.verificar("no.es.un-token")).isEmpty();
		assertThat(verificador.verificar(" ")).isEmpty();
		assertThat(verificador.verificar(null)).isEmpty();
	}

	@Test
	@DisplayName("Sin client ID el inicio con Google queda deshabilitado")
	void sinClientId() {
		GoogleTokenVerificador sinConfigurar = new GoogleTokenVerificador(null, id -> CLAVES.getPublic());

		assertThat(sinConfigurar.estaConfigurado()).isFalse();
		assertThat(sinConfigurar.getClientId()).isNull();
		assertThat(sinConfigurar.verificar(token(Map.of()))).isEmpty();
		assertThat(new GoogleTokenVerificador("  ", id -> null).estaConfigurado()).isFalse();
		assertThat(verificador.estaConfigurado()).isTrue();
		assertThat(verificador.getClientId()).isEqualTo(CLIENTE);
	}

	/**
	 * ID token como el que entrega Google, firmado con la clave de prueba.
	 *
	 * @param cambios claims que se reemplazan o agregan
	 * @return token firmado
	 */
	private static String token(Map<String, Object> cambios) {
		return token(cambios, CLAVES.getPrivate(), ID_CLAVE);
	}

	/**
	 * ID token como el que entrega Google.
	 *
	 * @param cambios claims que se reemplazan o agregan
	 * @param clave   clave privada con la que se firma
	 * @param idClave identificador de la clave en el encabezado; puede ser null
	 * @return token firmado
	 */
	static String token(Map<String, Object> cambios, PrivateKey clave, String idClave) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("iss", "https://accounts.google.com");
		claims.put("aud", CLIENTE);
		claims.put("sub", "109876");
		claims.put("email", CORREO);
		claims.put("email_verified", true);
		claims.put("name", "Ana María Prieto");
		Instant ahora = Instant.now();
		claims.put("iat", ahora.getEpochSecond());
		claims.put("exp", ahora.plusSeconds(3600).getEpochSecond());
		claims.putAll(cambios);
		return Jwts.builder().header().keyId(idClave).and().claims(claims).signWith(clave).compact();
	}

	/**
	 * Genera un par de claves RSA de 2048 bits.
	 *
	 * @return par de claves
	 */
	private static KeyPair generarClaves() {
		try {
			KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
			generador.initialize(2048);
			return generador.generateKeyPair();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
