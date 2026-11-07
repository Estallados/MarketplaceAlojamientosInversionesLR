package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
