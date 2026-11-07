package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.marketplaceInversionesLR.entity.TokenRecuperacion;

/**
 * Almacén en memoria de los tokens de recuperación de contraseña; no usa la
 * base de datos.
 * <p>
 * Guarda un solo token por usuario, porque cada token nuevo invalida el
 * anterior. Los tokens se pierden al reiniciar la aplicación (el usuario pide
 * otro) y no se comparten entre instancias.
 * </p>
 */
@Repository
public class TokenRecuperacionRepository {

	/** Cada cuánto se limpian los tokens usados o vencidos (5 minutos). */
	private static final long INTERVALO_LIMPIEZA_MS = 300_000;

	/** Token vigente de cada usuario. */
	private final Map<Long, TokenRecuperacion> tokens = new ConcurrentHashMap<>();

	/**
	 * Constructor de TokenRecuperacionRepository.
	 */
	public TokenRecuperacionRepository() {
	}
	
	/**
	 * Guarda el token como el único del usuario.
	 *
	 * @param token Token a guardar.
	 * @return El mismo token.
	 */
	public TokenRecuperacion save(TokenRecuperacion token) {
		tokens.put(token.getIdUsuario(), token);
		return token;
	}

	/**
	 * Busca un token por su hash.
	 *
	 * @param tokenHash Hash del token recibido.
	 * @return El token si existe.
	 */
	public Optional<TokenRecuperacion> findByTokenHash(String tokenHash) {
		return tokens.values().stream().filter(token -> tokenHash.equals(token.getTokenHash())).findFirst();
	}

	/**
	 * Tokens que el usuario aún no ha usado; se invalidan al pedir uno nuevo.
	 *
	 * @param idUsuario Usuario dueño de los tokens.
	 * @return Tokens sin usar.
	 */
	public List<TokenRecuperacion> findByIdUsuarioAndUsadoFalse(Long idUsuario) {
		return Optional.ofNullable(tokens.get(idUsuario)).filter(token -> !Boolean.TRUE.equals(token.getUsado()))
				.stream().toList();
	}

	/**
	 * Devuelve Todos los tokens guardados.
	 *
	 * @return Todos los tokens guardados.
	 */
	public List<TokenRecuperacion> findAll() {
		return new ArrayList<>(tokens.values());
	}

	/**
	 * Devuelve Cantidad de tokens guardados.
	 *
	 * @return Cantidad de tokens guardados.
	 */
	public long count() {
		return tokens.size();
	}

	/** Borra todos los tokens. */
	public void deleteAll() {
		tokens.clear();
	}

	/** Quita de la memoria los tokens ya usados o vencidos. */
	@Scheduled(fixedDelay = INTERVALO_LIMPIEZA_MS)
	public void limpiar() {
		LocalDateTime ahora = LocalDateTime.now();
		tokens.values().removeIf(
				token -> Boolean.TRUE.equals(token.getUsado()) || token.getFechaExpiracion().isBefore(ahora));
	}
}
