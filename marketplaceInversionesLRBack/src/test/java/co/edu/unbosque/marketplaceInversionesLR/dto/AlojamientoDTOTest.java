package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del DTO de alojamiento.
 */
class AlojamientoDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Lee el JSON de entrada del contrato, con las horas en formato HH:mm")
	void leeElJson() {
		AlojamientoDTO dto = json.readValue("""
				{"titulo":"Casa","descripcion":"Vista","ubicacion":"Calle 1","ciudad":"Bogotá","latitud":4.6,
				 "longitud":-74.1,"tipo":"Casa","capacidad":4,"precioNoche":250000,"habitaciones":2,"camas":3,
				 "banos":1,"horaEntrada":"15:00","horaSalida":"11:30","reglas":"No fumar",
				 "amenidades":["wifi","piscina"],"idPolitica":1,"porcentajeLimpieza":5,"porcentajeServicio":10}
				""", AlojamientoDTO.class);

		assertThat(dto.getTitulo()).isEqualTo("Casa");
		assertThat(dto.getDescripcion()).isEqualTo("Vista");
		assertThat(dto.getUbicacion()).isEqualTo("Calle 1");
		assertThat(dto.getCiudad()).isEqualTo("Bogotá");
		assertThat(dto.getLatitud()).isEqualTo(4.6);
		assertThat(dto.getLongitud()).isEqualTo(-74.1);
		assertThat(dto.getTipo()).isEqualTo("Casa");
		assertThat(dto.getCapacidad()).isEqualTo(4);
		assertThat(dto.getPrecioNoche()).isEqualByComparingTo("250000");
		assertThat(dto.getHabitaciones()).isEqualTo(2);
		assertThat(dto.getCamas()).isEqualTo(3);
		assertThat(dto.getBanos()).isEqualTo(1);
		assertThat(dto.getHoraEntrada()).isEqualTo(LocalTime.of(15, 0));
		assertThat(dto.getHoraSalida()).isEqualTo(LocalTime.of(11, 30));
		assertThat(dto.getReglas()).isEqualTo("No fumar");
		assertThat(dto.getAmenidades()).containsExactlyInAnyOrder("wifi", "piscina");
		assertThat(dto.getIdPolitica()).isEqualTo(1L);
		assertThat(dto.getPorcentajeLimpieza()).isEqualByComparingTo("5");
		assertThat(dto.getPorcentajeServicio()).isEqualByComparingTo("10");
	}

	@Test
	@DisplayName("Responde con el estado, el anfitrión, las imágenes y las horas en HH:mm")
	void escribeElJson() {
		ImagenDTO imagen = new ImagenDTO();
		imagen.setIdImagen(5L);
		imagen.setUrl("https://img/a");
		imagen.setPrincipal(true);
		AlojamientoDTO dto = new AlojamientoDTO();
		dto.setIdAlojamiento(9L);
		dto.setTitulo("Casa");
		dto.setEstado(EstadoAlojamiento.BORRADOR);
		dto.setIdAnfitrion(2L);
		dto.setNombreAnfitrion("Ana");
		dto.setNombrePolitica("Flexible");
		dto.setHoraEntrada(LocalTime.of(15, 0));
		dto.setPrecioNoche(new BigDecimal("100"));
		dto.setImagenes(List.of("https://img/a"));
		dto.setDetalleImagenes(List.of(imagen));
		dto.setFechaCreacion(LocalDateTime.of(2026, 11, 7, 10, 0));
		dto.setFechaActualizacion(LocalDateTime.of(2026, 11, 7, 10, 0));
		dto.setAmenidades(Set.of("wifi"));

		String texto = json.writeValueAsString(dto);

		assertThat(texto).contains("\"estado\":\"BORRADOR\"").contains("\"idAnfitrion\":2")
				.contains("\"nombreAnfitrion\":\"Ana\"").contains("\"nombrePolitica\":\"Flexible\"")
				.contains("\"horaEntrada\":\"15:00\"").contains("\"imagenes\":[\"https://img/a\"]")
				.contains("\"principal\":true").contains("\"amenidades\":[\"wifi\"]");
		assertThat(dto.getIdAlojamiento()).isEqualTo(9L);
		assertThat(dto.getFechaCreacion()).isEqualTo(dto.getFechaActualizacion());
		assertThat(dto.getDetalleImagenes()).containsExactly(imagen);
	}

	@Test
	@DisplayName("Las amenidades empiezan vacías")
	void amenidadesVacias() {
		assertThat(new AlojamientoDTO().getAmenidades()).isEmpty();
	}
}
