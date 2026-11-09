package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * Descarga las claves públicas de Google (formato JWK) con las que Google
 * firma los ID token del inicio de sesión.
 * <p>
 * Las claves se guardan en memoria y se vuelven a descargar cuando vence el
 * tiempo que indica Google (Cache-Control max-age) o cuando llega un token
 * firmado con una clave nueva, como mucho una vez por minuto.
 * </p>
 */
@Service
public class GoogleClavesService {

	/** Claves públicas con las que Google firma los ID token (formato JWK). */
	public static final String URL_CLAVES = "https://www.googleapis.com/oauth2/v3/certs";

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(GoogleClavesService.class);

	/** Expresión para leer el max-age de la cabecera Cache-Control. */
	private static final Pattern MAX_AGE = Pattern.compile("max-age=(\\d+)");

	/** Vigencia de las claves si Google no la indica (1 hora). */
	private static final Duration VIGENCIA_POR_DEFECTO = Duration.ofHours(1);

	/** Tiempo mínimo entre dos descargas (1 minuto). */
	private static final Duration ESPERA_ENTRE_DESCARGAS = Duration.ofMinutes(1);

	/**
	 * Claves descargadas y cuándo se descargaron; solo se usan dentro de
	 * {@link #buscar}.
	 */
	private static final class Cache {
		/** Claves por identificador. */
		private Map<String, PublicKey> claves = Map.of();

		/** Momento en que las claves dejan de ser vigentes. */
		private Instant vence = Instant.EPOCH;

		/** Momento de la última descarga. */
		private Instant ultimaDescarga = Instant.EPOCH;
	}

	/** Cliente HTTP para descargar las claves. */
	private final ClienteHttp clienteHttp;

	/** Claves descargadas. */
	private final Cache cache;

	/**
	 * Crea el servicio.
	 *
	 * @param clienteHttp cliente HTTP
	 */
	public GoogleClavesService(ClienteHttp clienteHttp) {
		this.clienteHttp = clienteHttp;
		this.cache = new Cache();
	}

	/**
	 * Clave pública de Google con el identificador indicado. Si las claves
	 * vencieron o no se conoce el identificador, las vuelve a descargar (como
	 * mucho una vez por minuto).
	 *
	 * @param idClave identificador de la clave (kid del token)
	 * @return la clave, o {@code null} si Google no tiene una con ese
	 *         identificador
	 */
	public synchronized PublicKey buscar(String idClave) {
		Instant ahora = Instant.now();
		boolean desconocida = !cache.claves.containsKey(idClave)
				&& ahora.isAfter(cache.ultimaDescarga.plus(ESPERA_ENTRE_DESCARGAS));
		// Vencidas desde el instante de vencimiento: con max-age=0 se descargan en cada consulta
		if (!ahora.isBefore(cache.vence) || desconocida) {
			descargar(ahora);
		}
		return cache.claves.get(idClave);
	}

	/**
	 * Descarga las claves de Google y las guarda en memoria. Si falla, deja
	 * las anteriores y lo registra en el log.
	 *
	 * @param ahora momento de la descarga
	 */
	private void descargar(Instant ahora) {
		cache.ultimaDescarga = ahora;
		try {
			ResponseEntity<JsonNode> respuesta = clienteHttp.getJson(URL_CLAVES);
			Map<String, PublicKey> nuevas = new HashMap<>();
			JsonNode cuerpo = respuesta.getBody();
			if (cuerpo != null) {
				for (JsonNode jwk : cuerpo.path("keys")) {
					if ("RSA".equals(jwk.path("kty").asString())) {
						nuevas.put(jwk.path("kid").asString(), claveRsa(jwk.path("n").asString(), jwk.path("e").asString()));
					}
				}
			}
			cache.claves = nuevas;
			cache.vence = ahora.plus(vigencia(respuesta.getHeaders().getCacheControl()));
		} catch (RestClientException | GeneralSecurityException | IllegalArgumentException e) {
			LOG.warn("No se pudieron descargar las claves públicas de Google: {}", e.getMessage());
		}
	}

	/**
	 * Lee cuánto tiempo son válidas las claves según la cabecera
	 * Cache-Control.
	 *
	 * @param cacheControl valor de la cabecera; puede ser {@code null}
	 * @return vigencia indicada, o la de por defecto
	 */
	private static Duration vigencia(String cacheControl) {
		Matcher m = cacheControl == null ? null : MAX_AGE.matcher(cacheControl);
		return m != null && m.find() ? Duration.ofSeconds(Long.parseLong(m.group(1))) : VIGENCIA_POR_DEFECTO;
	}

	/**
	 * Arma una clave pública RSA a partir del módulo y el exponente en Base64
	 * URL.
	 *
	 * @param modulo módulo de la clave (n)
	 * @param exponente exponente de la clave (e)
	 * @return clave pública RSA
	 * @throws GeneralSecurityException si los valores no forman una clave
	 *                                  válida
	 */
	private static PublicKey claveRsa(String modulo, String exponente) throws GeneralSecurityException {
		Base64.Decoder base64 = Base64.getUrlDecoder();
		return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
				new BigInteger(1, base64.decode(modulo)), new BigInteger(1, base64.decode(exponente))));
	}
}
