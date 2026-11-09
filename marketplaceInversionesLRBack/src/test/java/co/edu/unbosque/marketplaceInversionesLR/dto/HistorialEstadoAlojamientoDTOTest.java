package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO del historial de estados.
 */
class HistorialEstadoAlojamientoDTOTest {

	@Test
	@DisplayName("Responde con el estado anterior, el nuevo, el motivo y el usuario")
	void escribeElJson() {
		HistorialEstadoAlojamientoDTO dto = new HistorialEstadoAlojamientoDTO();
		dto.setIdHistorial(3L);
		dto.setEstadoAnterior(EstadoAlojamiento.ACTIVA);
		dto.setEstadoNuevo(EstadoAlojamiento.PAUSADA);
		dto.setMotivo("Vacaciones");
		dto.setIdUsuario(4L);

		assertThat(JsonMapper.builder().build().writeValueAsString(dto)).contains("\"idHistorial\":3")
				.contains("\"estadoAnterior\":\"ACTIVA\"").contains("\"estadoNuevo\":\"PAUSADA\"")
				.contains("\"motivo\":\"Vacaciones\"").contains("\"idUsuario\":4");
	}
}
