package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de los porcentajes de tarifa.
 */
class ConfiguracionTarifaDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Guarda los porcentajes, quién y cuándo los cambió, y si son los de por defecto")
	void accesores() {
		LocalDateTime fecha = LocalDateTime.of(2026, 11, 7, 10, 30);
		ConfiguracionTarifaDTO dto = new ConfiguracionTarifaDTO();
		dto.setIdConfiguracion(3L);
		dto.setPorcentajeLimpieza(new BigDecimal("7.5"));
		dto.setPorcentajeServicio(new BigDecimal("12"));
		dto.setIdUsuario(1L);
		dto.setFecha(fecha);
		dto.setPorDefecto(false);

		assertThat(dto.getIdConfiguracion()).isEqualTo(3L);
		assertThat(dto.getPorcentajeLimpieza()).isEqualByComparingTo("7.5");
		assertThat(dto.getPorcentajeServicio()).isEqualByComparingTo("12");
		assertThat(dto.getIdUsuario()).isEqualTo(1L);
		assertThat(dto.getFecha()).isEqualTo(fecha);
		assertThat(dto.getPorDefecto()).isFalse();
	}

	@Test
	@DisplayName("Lee los porcentajes del JSON de entrada y responde con los campos del contrato")
	void json() {
		ConfiguracionTarifaDTO entrada = json.readValue("{\"porcentajeLimpieza\":5,\"porcentajeServicio\":10.25}",
				ConfiguracionTarifaDTO.class);
		assertThat(entrada.getPorcentajeLimpieza()).isEqualByComparingTo("5");
		assertThat(entrada.getPorcentajeServicio()).isEqualByComparingTo("10.25");
		assertThat(entrada.getPorDefecto()).isNull();

		ConfiguracionTarifaDTO salida = new ConfiguracionTarifaDTO();
		salida.setPorcentajeLimpieza(BigDecimal.valueOf(5));
		salida.setPorcentajeServicio(BigDecimal.valueOf(10));
		salida.setPorDefecto(true);
		assertThat(json.writeValueAsString(salida)).contains("\"porcentajeLimpieza\":5")
				.contains("\"porcentajeServicio\":10").contains("\"porDefecto\":true")
				.contains("\"idConfiguracion\":null");
	}
}
