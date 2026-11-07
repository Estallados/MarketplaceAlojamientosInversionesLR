package co.edu.unbosque.marketplaceInversionesLR.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Pruebas de los tokens JWT de sesión.
 */
class JwtUtilTest {

	private final JwtUtil jwtUtil = new JwtUtil(3_600_000);

	private final UserDetails ana = User.withUsername("ana@correo.com").password("x").roles("HUESPED").build();

	@Test
	@DisplayName("El token generado es válido para su usuario y lleva su correo")
	void tokenValido() {
		String token = jwtUtil.generateToken(ana);

		assertThat(jwtUtil.validateToken(token, ana)).isTrue();
		assertThat(jwtUtil.extractUsername(token)).isEqualTo("ana@correo.com");
	}

	@Test
	@DisplayName("El token no sirve para otro usuario, vencido ni alterado")
	void tokenInvalido() {
		UserDetails luis = User.withUsername("luis@correo.com").password("x").roles("HUESPED").build();
		String token = jwtUtil.generateToken(ana);
		String vencido = new JwtUtil(-1000).generateToken(ana);

		assertThat(jwtUtil.validateToken(token, luis)).isFalse();
		assertThat(jwtUtil.validateToken(vencido, ana)).isFalse();
		assertThat(jwtUtil.validateToken(token + "x", ana)).isFalse();
		assertThat(jwtUtil.validateToken("no-es-un-token", ana)).isFalse();
	}
}
