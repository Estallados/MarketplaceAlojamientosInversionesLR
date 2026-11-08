package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de verificación del correo.
 */
class VerificacionCorreoDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda el correo y el código")
	void accesores() {
		VerificacionCorreoDTO dto = new VerificacionCorreoDTO();
		dto.setCorreo("ana@correo.com");
		dto.setCodigo("123456");

		assertThat(dto.getCorreo()).isEqualTo("ana@correo.com");
		assertThat(dto.getCodigo()).isEqualTo("123456");
	}

	@Test
	@DisplayName("Lee el JSON de la verificación del correo")
	void leeElJson() {
		VerificacionCorreoDTO dto = json.readValue("{\"correo\":\"ana@correo.com\",\"codigo\":\"089110\"}",
				VerificacionCorreoDTO.class);

		assertThat(dto.getCorreo()).isEqualTo("ana@correo.com");
		assertThat(dto.getCodigo()).isEqualTo("089110");
	}
}
