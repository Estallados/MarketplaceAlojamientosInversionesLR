package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de inicio de sesión.
 */
class LoginDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda el correo, la contraseña y el código")
	void accesores() {
		LoginDTO dto = new LoginDTO();
		dto.setCorreo("ana@correo.com");
		dto.setContrasenia("Clave#2026");
		dto.setCodigo("123456");

		assertThat(dto.getCorreo()).isEqualTo("ana@correo.com");
		assertThat(dto.getContrasenia()).isEqualTo("Clave#2026");
		assertThat(dto.getCodigo()).isEqualTo("123456");
	}

	@Test
	@DisplayName("Lee el JSON del login; el código es opcional")
	void leeElJson() {
		LoginDTO conCodigo = json.readValue(
				"{\"correo\":\"ana@correo.com\",\"contrasenia\":\"Clave#2026\",\"codigo\":\"123456\"}", LoginDTO.class);
		LoginDTO sinCodigo = json.readValue("{\"correo\":\"ana@correo.com\",\"contrasenia\":\"Clave#2026\"}",
				LoginDTO.class);

		assertThat(conCodigo.getCodigo()).isEqualTo("123456");
		assertThat(sinCodigo.getCorreo()).isEqualTo("ana@correo.com");
		assertThat(sinCodigo.getContrasenia()).isEqualTo("Clave#2026");
		assertThat(sinCodigo.getCodigo()).isNull();
	}
}
