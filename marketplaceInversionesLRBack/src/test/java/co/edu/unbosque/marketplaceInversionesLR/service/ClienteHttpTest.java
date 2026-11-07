package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * Pruebas unitarias del cliente HTTP común. Las respuestas de las APIs se
 * simulan con {@link MockRestServiceServer}.
 */
class ClienteHttpTest {

	/** Dirección de prueba. */
	private static final String URL = "https://api.prueba.local/recurso";

	private MockRestServiceServer servidor;

	private ClienteHttp cliente;

	@BeforeEach
	void preparar() {
		RestClient.Builder builder = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(builder).build();
		cliente = new ClienteHttp(builder.build());
	}

	@Test
	@DisplayName("GET JSON devuelve el cuerpo y los encabezados")
	void getJson() {
		servidor.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("{\"valor\": 1}", MediaType.APPLICATION_JSON));

		ResponseEntity<JsonNode> respuesta = cliente.getJson(URL);

		assertThat(respuesta.getBody().path("valor").asInt()).isEqualTo(1);
		servidor.verify();
	}

	@Test
	@DisplayName("GET JSON con URI y encabezados propios")
	void getJsonConEncabezados() {
		servidor.expect(requestTo(URL)).andExpect(header("User-Agent", "Prueba/1.0"))
				.andRespond(withSuccess("{\"nombre\": \"Bogotá\"}", MediaType.APPLICATION_JSON));

		JsonNode cuerpo = cliente.getJson(URI.create(URL), Map.of("User-Agent", "Prueba/1.0"));

		assertThat(cuerpo.path("nombre").asString()).isEqualTo("Bogotá");
		servidor.verify();
	}

	@Test
	@DisplayName("Un error de la API en el GET lanza RestClientException")
	void getJsonConError() {
		servidor.expect(requestTo(URL)).andRespond(withBadRequest());

		assertThatThrownBy(() -> cliente.getJson(URL)).isInstanceOf(RestClientException.class);
	}

	@Test
	@DisplayName("POST de formulario entrega el estado y el cuerpo al intérprete, también en errores")
	void postFormulario() {
		servidor.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer clave"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("{\"error\": \"x\"}"));
		MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
		formulario.add("monto", "100");

		String resultado = cliente.postFormulario(URL, Map.of("Authorization", "Bearer clave"), formulario,
				(estado, cuerpo) -> estado.value() + ":" + cuerpo.path("error").asString());

		assertThat(resultado).isEqualTo("400:x");
		servidor.verify();
	}

	@Test
	@DisplayName("Se puede crear con los tiempos por defecto o con otros")
	void constructores() {
		assertThat(new ClienteHttp()).isNotNull();
		assertThat(new ClienteHttp(Duration.ofSeconds(1), Duration.ofSeconds(2))).isNotNull();
	}
}
