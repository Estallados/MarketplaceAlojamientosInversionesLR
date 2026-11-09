package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de servicios adicionales.
 */
class ServicioAdicionalDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Lee el nombre y el costo del JSON de entrada del contrato")
	void leeElJson() {
		ServicioAdicionalDTO dto = json.readValue("{\"nombre\":\"Desayuno\",\"costo\":20000}",
				ServicioAdicionalDTO.class);

		assertThat(dto.getNombre()).isEqualTo("Desayuno");
		assertThat(dto.getCosto()).isEqualByComparingTo("20000");
		assertThat(dto.getIdServicio()).isNull();
	}

	@Test
	@DisplayName("Responde con el id del servicio y el de su alojamiento")
	void escribeElJson() {
		ServicioAdicionalDTO dto = new ServicioAdicionalDTO();
		dto.setIdServicio(7L);
		dto.setNombre("Traslado");
		dto.setCosto(new BigDecimal("35000"));
		dto.setIdAlojamiento(20L);

		assertThat(json.writeValueAsString(dto)).contains("\"idServicio\":7").contains("\"nombre\":\"Traslado\"")
				.contains("\"costo\":35000").contains("\"idAlojamiento\":20");
	}
}
