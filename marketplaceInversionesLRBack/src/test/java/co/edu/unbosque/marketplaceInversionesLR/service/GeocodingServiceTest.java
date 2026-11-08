package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import co.edu.unbosque.marketplaceInversionesLR.service.GeocodingService.Coordenadas;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas unitarias de la búsqueda de coordenadas. Nominatim se simula.
 */
class GeocodingServiceTest {

	/** Convierte las respuestas simuladas. */
	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** URL de búsqueda de prueba. */
	private static final String URL = "https://nominatim.prueba/search";

	private final ClienteHttp cliente = mock(ClienteHttp.class);

	/** Consultas que recibió el cliente simulado. */
	private final List<String> consultas = new ArrayList<>();

	private final GeocodingService servicio = new GeocodingService(cliente, URL, "Prueba/1.0", true, Duration.ZERO);

	@Test
	@DisplayName("Encuentra la dirección dentro de la ciudad")
	void encuentraLaDireccion() {
		responder("[{\"display_name\":\"Calle 1, Bogotá, Colombia\",\"lat\":\"4.6\",\"lon\":\"-74.08\"}]");

		assertThat(servicio.ubicar("Calle 1 # 2-3", "Bogotá")).contains(new Coordenadas(4.6, -74.08));
		// El "#" de la dirección viaja codificado, no como fragmento de la URL
		assertThat(consultas.get(0)).contains("Calle%201%20%23%202-3").contains("countrycodes=co")
				.doesNotContain("#");
		verify(cliente).getJson(any(URI.class), any());
	}

	@Test
	@DisplayName("Si no encuentra la dirección completa prueba sin el barrio y luego el centro de la ciudad")
	void usaRespaldos() {
		responder("[]", "[{\"display_name\":\"Medellín, Colombia\",\"lat\":\"1\",\"lon\":\"2\"}]",
				"[{\"display_name\":\"Bogotá, Colombia\",\"lat\":\"4.7\",\"lon\":\"-74.1\"}]");

		// El segundo resultado está en otra ciudad: no sirve
		assertThat(servicio.ubicar("Calle 1, Chapinero", "Bogotá")).contains(new Coordenadas(4.7, -74.1));
		assertThat(consultas).hasSize(3);
	}

	@Test
	@DisplayName("Ignora coordenadas inválidas o fuera de rango y respuestas vacías")
	void coordenadasInvalidas() {
		responder("[{\"display_name\":\"Bogotá\",\"lat\":\"x\",\"lon\":\"1\"}]");
		assertThat(servicio.ubicar(null, "Bogotá")).isEmpty();

		responder("[{\"display_name\":\"Bogotá\",\"lat\":\"95\",\"lon\":\"1\"}]");
		assertThat(servicio.ubicar(null, "Bogotá")).isEmpty();

		when(cliente.getJson(any(URI.class), any())).thenReturn(null);
		assertThat(servicio.ubicar("Calle 1", null)).isEmpty();
	}

	@Test
	@DisplayName("Deshabilitado o sin datos no consulta Nominatim")
	void sinConsultar() {
		GeocodingService apagado = new GeocodingService(cliente, URL, "Prueba/1.0", false, Duration.ZERO);

		assertThat(apagado.ubicar("Calle 1", "Bogotá")).isEmpty();
		assertThat(servicio.ubicar(" ", null)).isEmpty();
		verifyNoInteractions(cliente);
	}

	@Test
	@DisplayName("Si Nominatim no responde, el alojamiento queda sin coordenadas")
	void nominatimNoResponde() {
		when(cliente.getJson(any(URI.class), any())).thenThrow(new RestClientException("sin red"));

		assertThat(servicio.ubicar("Calle 1", "Bogotá")).isEmpty();
	}

	@Test
	@DisplayName("Si se interrumpe la espera entre peticiones, no busca y conserva la interrupción")
	void interrumpido() {
		GeocodingService lento = new GeocodingService(cliente, URL, "Prueba/1.0", true, Duration.ofSeconds(5));
		responder("[{\"display_name\":\"Bogotá\",\"lat\":\"4.6\",\"lon\":\"-74\"}]");
		lento.ubicar(null, "Bogotá");

		Thread.currentThread().interrupt();
		Optional<Coordenadas> resultado = lento.ubicar(null, "Bogotá");

		assertThat(resultado).isEmpty();
		assertThat(Thread.interrupted()).isTrue();
	}

	@Test
	@DisplayName("El constructor de Spring arma su propio cliente HTTP")
	void constructorDeSpring() {
		assertThat(new GeocodingService(URL, "Prueba/1.0", false).ubicar("Calle 1", "Bogotá")).isEmpty();
	}

	/**
	 * Hace que cada consulta a Nominatim responda el JSON siguiente y guarda la
	 * URI pedida.
	 *
	 * @param respuestas cuerpos JSON en orden
	 */
	private void responder(String... respuestas) {
		List<JsonNode> nodos = new ArrayList<>();
		for (String respuesta : respuestas) {
			nodos.add(JSON.readTree(respuesta));
		}
		int[] indice = { 0 };
		when(cliente.getJson(any(URI.class), any())).thenAnswer(invocacion -> {
			URI uri = invocacion.getArgument(0);
			Map<String, String> encabezados = invocacion.getArgument(1);
			assertThat(encabezados).containsEntry("User-Agent", "Prueba/1.0");
			consultas.add(uri.toString());
			return nodos.get(Math.min(indice[0]++, nodos.size() - 1));
		});
	}
}
