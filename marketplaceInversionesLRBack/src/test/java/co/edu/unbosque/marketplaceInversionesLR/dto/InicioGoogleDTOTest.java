package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO del inicio de sesión con Google.
 */
class InicioGoogleDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda el ID token, el rol y el código")
	void accesores() {
		InicioGoogleDTO dto = new InicioGoogleDTO();
		dto.setIdToken("token-de-google");
		dto.setRol(Role.ANFITRION);
		dto.setCodigo("123456");

		assertThat(dto.getIdToken()).isEqualTo("token-de-google");
		assertThat(dto.getRol()).isEqualTo(Role.ANFITRION);
		assertThat(dto.getCodigo()).isEqualTo("123456");
	}

	@Test
	@DisplayName("Lee el JSON con el rol como texto; el rol y el código son opcionales")
	void leeElJson() {
		InicioGoogleDTO completo = json.readValue("{\"idToken\":\"t\",\"rol\":\"ANFITRION\",\"codigo\":\"123456\"}",
				InicioGoogleDTO.class);
		InicioGoogleDTO soloToken = json.readValue("{\"idToken\":\"t\"}", InicioGoogleDTO.class);

		assertThat(completo.getRol()).isEqualTo(Role.ANFITRION);
		assertThat(completo.getCodigo()).isEqualTo("123456");
		assertThat(soloToken.getIdToken()).isEqualTo("t");
		assertThat(soloToken.getRol()).isNull();
		assertThat(soloToken.getCodigo()).isNull();
	}
}
