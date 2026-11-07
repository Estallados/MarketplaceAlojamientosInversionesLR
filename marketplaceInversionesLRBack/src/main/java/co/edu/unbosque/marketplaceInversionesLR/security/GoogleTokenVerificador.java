package co.edu.unbosque.marketplaceInversionesLR.security;

import java.security.Key;
import java.security.PublicKey;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplaceInversionesLR.service.GoogleClavesService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.UnsupportedJwtException;

/**
 * Verifica el ID token que entrega el botón "Iniciar sesión con Google" (Google
 * Identity Services) en el front. El token es un JWT firmado por Google; aquí
 * se comprueba sin llamar a otros servicios de Google en cada inicio de sesión:
 * <ul>
 * <li>la firma, con las claves públicas de Google, que descarga y guarda en
 * memoria {@link GoogleClavesService};</li>
 * <li>el emisor ({@code accounts.google.com}) y el vencimiento;</li>
 * <li>la audiencia: el token debe haberse emitido para nuestro
 * {@code app.google.client-id}, así no sirve uno generado para otra app;</li>
 * <li>que Google haya verificado el correo.</li>
 * </ul>
 * Sin {@code GOOGLE_CLIENT_ID} el inicio con Google queda deshabilitado.
 */
@Component
public class GoogleTokenVerificador {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(GoogleTokenVerificador.class);

	/** Emisores que Google pone en sus ID tokens (con y sin esquema). */
	private static final Set<String> EMISORES = Set.of("accounts.google.com", "https://accounts.google.com");

	/** Margen para diferencias de reloj al revisar el vencimiento. */
	private static final long TOLERANCIA_RELOJ_SEGUNDOS = 60;

	/**
	 * Datos de la cuenta de Google que se usan para iniciar sesión o registrarse.
	 *
	 * @param idGoogle identificador único de la cuenta en Google (claim
	 *                 {@code sub})
	 * @param correo   correo verificado de la cuenta
	 * @param nombre   nombre que muestra Google; puede venir {@code null}
	 */
	public record CuentaGoogle(String idGoogle, String correo, String nombre) {
	}

	/** Client ID de la aplicación en Google Cloud; vacío si no se configuró. */
	private final String clientId;

	/** Busca la clave pública de Google a partir de su identificador. */
	private final Function<String, PublicKey> buscarClave;

	/**
	 * Constructor que usa Spring: las claves salen de {@link GoogleClavesService}.
	 *
	 * @param clientId client ID de Google ({@code app.google.client-id})
	 * @param claves   servicio que descarga y guarda las claves públicas de Google
	 */
	@Autowired
	public GoogleTokenVerificador(@Value("${app.google.client-id:}") String clientId, GoogleClavesService claves) {
		this(clientId, claves::buscar);
	}

	/**
	 * Constructor con otra fuente de claves (identificador de clave → clave
	 * pública). Lo usan las pruebas para firmar sus propios tokens.
	 *
	 * @param clientId    client ID esperado como audiencia
	 * @param buscarClave función que devuelve la clave pública de cada
	 *                    identificador, o {@code null} si no la conoce
	 */
	public GoogleTokenVerificador(String clientId, Function<String, PublicKey> buscarClave) {
		this.clientId = clientId == null ? "" : clientId.trim();
		this.buscarClave = buscarClave;
	}

	/**
	 * Devuelve {@code true} si se configuró el client ID de Google.
	 *
	 * @return {@code true} si se configuró el client ID de Google
	 */
	public boolean estaConfigurado() {
		return !clientId.isEmpty();
	}

	/**
	 * Client ID con el que el frontend inicializa el botón de Google. No es
	 * secreto.
	 *
	 * @return client ID, o {@code null} si no está configurado
	 */
	public String getClientId() {
		return estaConfigurado() ? clientId : null;
	}

	/**
	 * Verifica el ID token.
	 *
	 * @param idToken ID token que entregó el botón de Google en el frontend
	 * @return la cuenta de Google, o vacío si el token no es válido, venció, no es
	 *         para esta aplicación o el correo no está verificado.
	 */
	public Optional<CuentaGoogle> verificar(String idToken) {
		if (!estaConfigurado() || idToken == null || idToken.isBlank()) {
			return Optional.empty();
		}
		try {
			Claims claims = Jwts.parser().keyLocator(new LocatorAdapter<Key>() {
				@Override
				protected Key locate(JwsHeader header) {
					PublicKey clave = header.getKeyId() == null ? null : buscarClave.apply(header.getKeyId());
					if (clave == null) {
						throw new UnsupportedJwtException("El token no está firmado con una clave de Google");
					}
					return clave;
				}
			}).requireAudience(clientId).clockSkewSeconds(TOLERANCIA_RELOJ_SEGUNDOS).build()
					.parseSignedClaims(idToken.trim()).getPayload();

			Object verificado = claims.get("email_verified");
			String correo = claims.get("email", String.class);
			if (!EMISORES.contains(claims.getIssuer()) || correo == null || correo.isBlank()
					|| !(Boolean.TRUE.equals(verificado) || "true".equals(verificado))) {
				return Optional.empty();
			}
			return Optional.of(new CuentaGoogle(claims.getSubject(), correo, claims.get("name", String.class)));
		} catch (JwtException | IllegalArgumentException e) {
			LOG.info("ID token de Google rechazado: {}", e.getMessage());
			return Optional.empty();
		}
	}
}
