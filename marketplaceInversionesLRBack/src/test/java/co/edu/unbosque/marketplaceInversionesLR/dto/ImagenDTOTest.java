package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de imagen.
 */
class ImagenDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda el id, la URL, el alojamiento, si es la portada y la ruta en Cloudinary")
	void accesores() {
		ImagenDTO dto = new ImagenDTO();
		dto.setIdImagen(5L);
		dto.setUrl("https://img/a");
		dto.setIdAlojamiento(9L);
		dto.setPrincipal(true);
		dto.setRutaArchivo("marketplace/alojamientos/9/a");

		assertThat(dto.getIdImagen()).isEqualTo(5L);
		assertThat(dto.getUrl()).isEqualTo("https://img/a");
		assertThat(dto.getIdAlojamiento()).isEqualTo(9L);
		assertThat(dto.getPrincipal()).isTrue();
		assertThat(dto.getRutaArchivo()).isEqualTo("marketplace/alojamientos/9/a");
		assertThat(json.writeValueAsString(dto)).contains("\"idImagen\":5").contains("\"url\":\"https://img/a\"")
				.contains("\"principal\":true");
	}
}
