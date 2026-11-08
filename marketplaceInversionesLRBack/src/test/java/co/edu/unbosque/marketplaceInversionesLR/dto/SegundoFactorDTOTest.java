package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO del segundo factor.
 */
class SegundoFactorDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda el secreto, la URI y el código")
	void accesores() {
		SegundoFactorDTO dto = new SegundoFactorDTO();
		dto.setSecreto("SECRETO");
		dto.setUri("otpauth://totp/App:ana@correo.com?secret=SECRETO");
		dto.setCodigo("123456");

		assertThat(dto.getSecreto()).isEqualTo("SECRETO");
		assertThat(dto.getUri()).isEqualTo("otpauth://totp/App:ana@correo.com?secret=SECRETO");
		assertThat(dto.getCodigo()).isEqualTo("123456");
	}

	@Test
	@DisplayName("Se convierte a JSON con el secreto y la URI, y lee el código al confirmar")
	void json() {
		SegundoFactorDTO dto = new SegundoFactorDTO();
		dto.setSecreto("SECRETO");
		dto.setUri("otpauth://totp/x");

		assertThat(json.writeValueAsString(dto)).contains("\"secreto\":\"SECRETO\"")
				.contains("\"uri\":\"otpauth://totp/x\"");
		assertThat(json.readValue("{\"codigo\":\"654321\"}", SegundoFactorDTO.class).getCodigo()).isEqualTo("654321");
	}
}
