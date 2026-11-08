package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de política de cancelación.
 */
class PoliticaCancelacionDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda el nombre, las condiciones y los días y porcentaje de reembolso")
	void accesores() {
		PoliticaCancelacionDTO dto = new PoliticaCancelacionDTO();
		dto.setIdPolitica(1L);
		dto.setNombre("Flexible");
		dto.setCondiciones("Reembolso total hasta 1 día antes");
		dto.setDiasReembolsoTotal(1);
		dto.setDiasReembolsoParcial(0);
		dto.setPorcentajeParcial(50);

		assertThat(dto.getIdPolitica()).isEqualTo(1L);
		assertThat(dto.getNombre()).isEqualTo("Flexible");
		assertThat(dto.getCondiciones()).isEqualTo("Reembolso total hasta 1 día antes");
		assertThat(dto.getDiasReembolsoTotal()).isEqualTo(1);
		assertThat(dto.getDiasReembolsoParcial()).isZero();
		assertThat(dto.getPorcentajeParcial()).isEqualTo(50);
		assertThat(json.writeValueAsString(dto)).contains("\"nombre\":\"Flexible\"")
				.contains("\"diasReembolsoTotal\":1").contains("\"porcentajeParcial\":50");
	}
}
