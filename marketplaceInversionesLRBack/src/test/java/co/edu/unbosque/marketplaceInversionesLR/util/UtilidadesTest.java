package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;

/**
 * Pruebas de las utilidades de texto y de respuestas.
 */
class UtilidadesTest {

	@Test
	@DisplayName("excede detecta los textos más largos que el máximo, sin contar espacios de los extremos")
	void excede() {
		assertThat(LimitesTexto.excede(null, 5)).isFalse();
		assertThat(LimitesTexto.excede("  abc  ", 3)).isFalse();
		assertThat(LimitesTexto.excede("abcd", 3)).isTrue();
	}

	@Test
	@DisplayName("mensaje arma la respuesta {message} con el código pedido")
	void mensaje() {
		ResponseEntity<Object> respuesta = RespuestaUtil.mensaje(HttpStatus.CONFLICT, "Ya existe");

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", "Ya existe"));
	}

	@Test
	@DisplayName("esAdmin reconoce el rol ADMIN y no a los demás roles ni a la petición sin sesión")
	void esAdmin() {
		assertThat(RespuestaUtil.esAdmin(sesion(Role.ADMIN))).isTrue();
		assertThat(RespuestaUtil.esAdmin(sesion(Role.ANFITRION))).isFalse();
		assertThat(RespuestaUtil.esAdmin(null)).isFalse();
	}

	/**
	 * Sesión de un usuario con el rol indicado.
	 *
	 * @param rol rol del usuario
	 * @return autenticación con sus autoridades
	 */
	private static UsernamePasswordAuthenticationToken sesion(Role rol) {
		Usuario usuario = new Usuario();
		usuario.setRol(rol);
		return new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
	}
}
