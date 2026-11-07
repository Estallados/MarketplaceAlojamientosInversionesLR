package co.edu.unbosque.marketplaceInversionesLR.service;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Cliente HTTP común para las APIs externas (Stripe, las claves públicas de
 * Google y Nominatim).
 * <p>
 * Todas las peticiones tienen un tiempo máximo de conexión y de lectura, así
 * una API lenta no deja colgada la petición del usuario.
 * </p>
 */
@Component
public class ClienteHttp {

	/** Tiempo máximo para conectarse (5 segundos). */
	private static final Duration TIEMPO_CONEXION = Duration.ofSeconds(5);

	/** Tiempo máximo para recibir la respuesta (15 segundos). */
	private static final Duration TIEMPO_LECTURA = Duration.ofSeconds(15);

	/** Cliente HTTP de Spring ya configurado. */
	private final RestClient restClient;

	/**
	 * Cliente con los tiempos máximos por defecto; es el que usa Spring.
	 */
	@Autowired
	public ClienteHttp() {
		this(TIEMPO_CONEXION, TIEMPO_LECTURA);
	}

	/**
	 * Cliente con otros tiempos máximos, para APIs que deben responder rápido.
	 *
	 * @param tiempoConexion tiempo máximo para conectarse
	 * @param tiempoLectura tiempo máximo para recibir la respuesta
	 */
	public ClienteHttp(Duration tiempoConexion, Duration tiempoLectura) {
		this(RestClient.builder().requestFactory(fabricaConTiempos(tiempoConexion, tiempoLectura)).build());
	}

	/**
	 * Cliente con un {@link RestClient} propio. Lo usan las pruebas para
	 * simular las respuestas de las APIs.
	 *
	 * @param restClient cliente HTTP a usar
	 */
	public ClienteHttp(RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * Arma la fábrica de peticiones con los tiempos máximos.
	 *
	 * @param tiempoConexion tiempo máximo para conectarse
	 * @param tiempoLectura tiempo máximo para recibir la respuesta
	 * @return fábrica de peticiones configurada
	 */
	private static SimpleClientHttpRequestFactory fabricaConTiempos(Duration tiempoConexion, Duration tiempoLectura) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(tiempoConexion);
		fabrica.setReadTimeout(tiempoLectura);
		return fabrica;
	}

	/**
	 * GET que espera una respuesta JSON.
	 *
	 * @param url dirección a consultar
	 * @return la respuesta con sus encabezados
	 * @throws org.springframework.web.client.RestClientException si no hay
	 *                                                            comunicación o
	 *                                                            la API
	 *                                                            responde con
	 *                                                            un error
	 */
	public ResponseEntity<JsonNode> getJson(String url) {
		return restClient.get().uri(url).retrieve().toEntity(JsonNode.class);
	}

	/**
	 * GET que espera JSON, con una URI ya codificada (no se vuelve a
	 * codificar) y encabezados propios.
	 *
	 * @param uri dirección a consultar, ya codificada
	 * @param encabezados encabezados que se agregan a la petición
	 * @return el cuerpo JSON de la respuesta
	 * @throws org.springframework.web.client.RestClientException si no hay
	 *                                                            comunicación o
	 *                                                            la API
	 *                                                            responde con
	 *                                                            un error
	 */
	public JsonNode getJson(URI uri, Map<String, String> encabezados) {
		return restClient.get().uri(uri).headers(h -> encabezados.forEach(h::set)).retrieve().body(JsonNode.class);
	}

	/**
	 * POST con un formulario (application/x-www-form-urlencoded). Las
	 * respuestas de error no lanzan excepción: el intérprete recibe el estado
	 * HTTP y el cuerpo JSON y decide qué hacer.
	 *
	 * @param <T> tipo del resultado que arma el intérprete
	 * @param url dirección a la que se envía
	 * @param encabezados encabezados que se agregan a la petición
	 * @param formulario campos del formulario
	 * @param interprete función que convierte el estado y el cuerpo en el
	 *                   resultado
	 * @return lo que devuelva el intérprete
	 * @throws org.springframework.web.client.RestClientException si no hay
	 *                                                            comunicación
	 */
	public <T> T postFormulario(String url, Map<String, String> encabezados, MultiValueMap<String, String> formulario,
			BiFunction<HttpStatusCode, JsonNode, T> interprete) {
		return restClient.post().uri(url).headers(h -> encabezados.forEach(h::set))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario).exchange((peticion,
						respuesta) -> interprete.apply(respuesta.getStatusCode(), respuesta.bodyTo(JsonNode.class)));
	}
}
