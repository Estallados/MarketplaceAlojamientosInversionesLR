package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de favoritos.
 */
class FavoritoDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda los datos del favorito y los del alojamiento")
	void accesores() {
		LocalDateTime fecha = LocalDateTime.of(2026, 11, 9, 10, 30);
		AlojamientoDTO alojamiento = new AlojamientoDTO();
		FavoritoDTO dto = new FavoritoDTO();
		dto.setIdFavorito(1L);
		dto.setFechaAgregado(fecha);
		dto.setIdUsuario(2L);
		dto.setIdAlojamiento(3L);
		dto.setAlojamiento(alojamiento);

		assertThat(dto.getIdFavorito()).isEqualTo(1L);
		assertThat(dto.getFechaAgregado()).isEqualTo(fecha);
		assertThat(dto.getIdUsuario()).isEqualTo(2L);
		assertThat(dto.getIdAlojamiento()).isEqualTo(3L);
		assertThat(dto.getAlojamiento()).isSameAs(alojamiento);
	}

	@Test
	@DisplayName("Se convierte a JSON con los nombres del contrato")
	void json() {
		FavoritoDTO dto = new FavoritoDTO();
		dto.setIdFavorito(1L);
		dto.setIdAlojamiento(3L);

		assertThat(json.writeValueAsString(dto)).contains("\"idFavorito\":1").contains("\"idAlojamiento\":3");
	}
}
