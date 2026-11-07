package co.edu.unbosque.marketplaceInversionesLR.security;

import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Generación y validación de los tokens JWT de sesión. La clave de firma se
 * deriva de {@code AES_SECRET_KEY}, así no hay que configurar otro secreto ni
 * escribirlo en el proyecto.
 */
@Service
public class JwtUtil {

	/** Tiempo de vida de los tokens en milisegundos ({@code spring.jwt.expirationtime}). */
	private final long expirationTime;

	/**
	 * Crea la utilidad.
	 *
	 * @param expirationTime tiempo de vida de los tokens en milisegundos
	 */
	public JwtUtil(@Value("${spring.jwt.expirationtime:3600000}") long expirationTime) {
		this.expirationTime = expirationTime;
	}

	/**
	 * Genera el token de un usuario: el sujeto es su correo y lleva sus
	 * autoridades (rol).
	 *
	 * @param userDetails usuario autenticado
	 * @return token JWT firmado
	 */
	public String generateToken(UserDetails userDetails) {
		long ahora = System.currentTimeMillis();
		return Jwts.builder()
				.subject(userDetails.getUsername())
				.claims(Map.of("authorities",
						userDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()))
				.issuedAt(new Date(ahora))
				.expiration(new Date(ahora + expirationTime))
				.signWith(obtenerKey())
				.compact();
	}

	/**
	 * Indica si el token es válido para el usuario: firma correcta, mismo sujeto
	 * y sin vencer.
	 *
	 * @param token       token recibido
	 * @param userDetails usuario con el que se compara
	 * @return {@code true} si el token es válido
	 */
	public boolean validateToken(String token, UserDetails userDetails) {
		try {
			Claims claims = extraerClaims(token);
			return userDetails.getUsername().equals(claims.getSubject()) && claims.getExpiration().after(new Date());
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Extrae el sujeto (correo) del token.
	 *
	 * @param token token recibido
	 * @return correo del usuario
	 * @throws JwtException si el token no es válido o venció
	 */
	public String extractUsername(String token) {
		return extraerClaims(token).getSubject();
	}

	/**
	 * Verifica la firma del token y devuelve su contenido.
	 *
	 * @param token token recibido
	 * @return contenido del token
	 */
	private Claims extraerClaims(String token) {
		return Jwts.parser().verifyWith(obtenerKey()).build().parseSignedClaims(token).getPayload();
	}

	/**
	 * Clave HMAC-SHA256 derivada de {@code AES_SECRET_KEY}.
	 *
	 * @return clave de firma
	 */
	private static SecretKey obtenerKey() {
		return Keys.hmacShaKeyFor(AESUtil.derivarClave("jwt"));
	}
}
