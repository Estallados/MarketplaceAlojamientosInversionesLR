package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.entity.TokenRecuperacion;

/**
 * Pruebas unitarias del almacén en memoria de los tokens de recuperación.
 */
class TokenRecuperacionRepositoryTest {

	private final TokenRecuperacionRepository repositorio = new TokenRecuperacionRepository();

	@Test
	@DisplayName("Guarda un solo token por usuario y lo encuentra por su hash")
	void unTokenPorUsuario() {
		repositorio.save(token(1L, "hash-1", false, 30));
		repositorio.save(token(1L, "hash-2", false, 30));
		repositorio.save(token(2L, "hash-3", false, 30));

		assertThat(repositorio.count()).isEqualTo(2);
		assertThat(repositorio.findAll()).hasSize(2);
		assertThat(repositorio.findByTokenHash("hash-1")).isEmpty();
		assertThat(repositorio.findByTokenHash("hash-2")).get().extracting(TokenRecuperacion::getIdUsuario)
				.isEqualTo(1L);
	}

	@Test
	@DisplayName("Solo devuelve como pendientes los tokens sin usar")
	void tokensSinUsar() {
		repositorio.save(token(1L, "hash-1", false, 30));
		repositorio.save(token(2L, "hash-2", true, 30));

		assertThat(repositorio.findByIdUsuarioAndUsadoFalse(1L)).hasSize(1);
		assertThat(repositorio.findByIdUsuarioAndUsadoFalse(2L)).isEmpty();
		assertThat(repositorio.findByIdUsuarioAndUsadoFalse(3L)).isEmpty();
	}

	@Test
	@DisplayName("La limpieza quita los tokens usados o vencidos y deleteAll los borra todos")
	void limpieza() {
		repositorio.save(token(1L, "vigente", false, 30));
		repositorio.save(token(2L, "usado", true, 30));
		repositorio.save(token(3L, "vencido", false, -1));

		repositorio.limpiar();

		assertThat(repositorio.findAll()).extracting(TokenRecuperacion::getTokenHash).containsExactly("vigente");
		repositorio.deleteAll();
		assertThat(repositorio.count()).isZero();
	}

	/**
	 * Crea un token de prueba.
	 *
	 * @param idUsuario dueño
	 * @param hash      hash del token
	 * @param usado     si ya se usó
	 * @param minutos   minutos hasta que vence (negativo si ya venció)
	 * @return token
	 */
	private static TokenRecuperacion token(Long idUsuario, String hash, boolean usado, int minutos) {
		TokenRecuperacion token = new TokenRecuperacion();
		token.setIdUsuario(idUsuario);
		token.setTokenHash(hash);
		token.setUsado(usado);
		token.setFechaExpiracion(LocalDateTime.now().plusMinutes(minutos));
		return token;
	}
}
