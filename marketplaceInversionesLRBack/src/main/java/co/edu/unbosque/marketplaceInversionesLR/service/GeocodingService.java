package co.edu.unbosque.marketplaceInversionesLR.service;

import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;

/**
 * Convierte la dirección y la ciudad de un alojamiento en coordenadas con
 * Nominatim (OpenStreetMap), el mismo proveedor del mapa del frontend.
 * <p>
 * Primero busca la dirección completa; después solo lo que va antes de la
 * primera coma (sin el barrio); y si tampoco la encuentra, o el resultado
 * queda en otra ciudad, usa el centro de la ciudad. Nunca lanza excepción: si
 * Nominatim no responde, el alojamiento se guarda sin coordenadas.
 * </p>
 * <p>
 * Política de uso de Nominatim: máximo una petición por segundo y un
 * User-Agent que identifique la aplicación.
 * </p>
 */
@Service
public class GeocodingService {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(GeocodingService.class);

	/** País que se agrega a todas las búsquedas. */
	private static final String PAIS = "Colombia";
	/** Resultados que se piden por búsqueda, para elegir el que está en la ciudad. */
	private static final int RESULTADOS = 3;

	/** Espera mínima entre peticiones (un poco más de un segundo). */
	private static final Duration INTERVALO = Duration.ofMillis(1100);

	/**
	 * Punto encontrado en el mapa.
	 *
	 * @param latitud latitud
	 * @param longitud longitud
	 */
	public record Coordenadas(double latitud, double longitud) {
	}

	/** Cliente HTTP para llamar a Nominatim. */
	private final ClienteHttp clienteHttp;

	/** Dirección de búsqueda de Nominatim. */
	private final String url;

	/** User-Agent que identifica la aplicación ante Nominatim. */
	private final String agente;

	/** Indica si la búsqueda de coordenadas está activa. */
	private final boolean habilitado;

	/** Espera mínima entre peticiones, en milisegundos. */
	private final long intervaloMs;

	/** Momento de la última petición, en milisegundos. */
	private long ultimaPeticion;

	/**
	 * Constructor que usa Spring.
	 *
	 * @param url dirección de búsqueda de Nominatim
	 * @param agente User-Agent de la aplicación
	 * @param habilitado {@code false} para no buscar coordenadas
	 */
	@Autowired
	public GeocodingService(@Value("${app.geocoding.url:https://nominatim.openstreetmap.org/search}") String url,
			@Value("${app.geocoding.agente:MarketplaceAlojamientos/1.0 (proyecto academico)}") String agente,
			@Value("${app.geocoding.habilitado:true}") boolean habilitado) {
		this(new ClienteHttp(Duration.ofSeconds(3), Duration.ofSeconds(5)), url, agente, habilitado, INTERVALO);
	}

	/**
	 * Constructor de las pruebas: otro cliente HTTP y otra espera entre
	 * peticiones.
	 *
	 * @param clienteHttp cliente HTTP
	 * @param url dirección de búsqueda
	 * @param agente User-Agent
	 * @param habilitado si la búsqueda está activa
	 * @param intervalo espera entre peticiones
	 */
	GeocodingService(ClienteHttp clienteHttp, String url, String agente, boolean habilitado, Duration intervalo) {
		this.clienteHttp = clienteHttp;
		this.url = url;
		this.agente = agente;
		this.habilitado = habilitado;
		this.intervaloMs = intervalo.toMillis();
	}

	/**
	 * Busca la dirección dentro de la ciudad y, si no la encuentra, el centro
	 * de la ciudad.
	 *
	 * @param direccion dirección del alojamiento
	 * @param ciudad ciudad del alojamiento
	 * @return las coordenadas, o vacío si no hay datos, no se encontró nada o
	 *         Nominatim no respondió
	 */
	public Optional<Coordenadas> ubicar(String direccion, String ciudad) {
		if (!habilitado || (vacio(direccion) && vacio(ciudad))) {
			return Optional.empty();
		}
		try {
			Optional<Coordenadas> encontrada = Optional.empty();
			if (!vacio(direccion)) {
				encontrada = buscar(conCiudad(direccion, ciudad), ciudad);
				// El barrio o la referencia después de la coma a veces impiden encontrar la calle
				int coma = direccion.indexOf(',');
				if (encontrada.isEmpty() && coma > 0) {
					encontrada = buscar(conCiudad(direccion.substring(0, coma), ciudad), ciudad);
				}
			}
			if (encontrada.isEmpty() && !vacio(ciudad)) {
				LOG.info("No se encontró la dirección '{}' en el mapa; se usa el centro de {}.", direccion, ciudad);
				encontrada = buscar(ciudad.trim(), null);
			}
			return encontrada;
		} catch (RestClientException e) {
			LOG.warn("Nominatim no respondió ({}); el alojamiento queda sin coordenadas.", e.getMessage());
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	/**
	 * Hace una búsqueda en Nominatim.
	 *
	 * @param consulta texto a buscar
	 * @param ciudadEsperada si no es {@code null}, solo sirve un resultado que
	 *                       esté en esa ciudad (hay calles con el mismo nombre
	 *                       en muchas)
	 * @return las coordenadas del primer resultado que sirva, o vacío
	 * @throws InterruptedException si se interrumpe la espera entre peticiones
	 */
	private Optional<Coordenadas> buscar(String consulta, String ciudadEsperada) throws InterruptedException {
		// La variable {q} se codifica completa: el "#" de las direcciones no se toma como fragmento
		URI uri = UriComponentsBuilder.fromUriString(url).queryParam("q", "{q}").queryParam("format", "jsonv2")
				.queryParam("limit", RESULTADOS).queryParam("countrycodes", "co").encode()
				.buildAndExpand(consulta + ", " + PAIS).toUri();
		esperarTurno();
		JsonNode resultados = clienteHttp.getJson(uri,
				Map.of(HttpHeaders.USER_AGENT, agente, HttpHeaders.ACCEPT_LANGUAGE, "es"));
		if (resultados == null) {
			return Optional.empty();
		}
		for (JsonNode resultado : resultados) {
			if (vacio(ciudadEsperada)
					|| normalizar(resultado.path("display_name").asString()).contains(normalizar(ciudadEsperada))) {
				return coordenadas(resultado);
			}
		}
		return Optional.empty();
	}

	/**
	 * Lee la latitud y la longitud de un resultado de Nominatim y revisa que
	 * estén en rango.
	 *
	 * @param resultado resultado JSON
	 * @return las coordenadas, o vacío si no son válidas
	 */
	private static Optional<Coordenadas> coordenadas(JsonNode resultado) {
		try {
			double latitud = Double.parseDouble(resultado.path("lat").asString());
			double longitud = Double.parseDouble(resultado.path("lon").asString());
			if (Math.abs(latitud) > 90 || Math.abs(longitud) > 180) {
				return Optional.empty();
			}
			return Optional.of(new Coordenadas(latitud, longitud));
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	/**
	 * Espera lo necesario para respetar el máximo de una petición por segundo
	 * de Nominatim.
	 *
	 * @throws InterruptedException si se interrumpe la espera
	 */
	private synchronized void esperarTurno() throws InterruptedException {
		long espera = ultimaPeticion + intervaloMs - System.currentTimeMillis();
		while (espera > 0) {
			wait(espera);
			espera = ultimaPeticion + intervaloMs - System.currentTimeMillis();
		}
		ultimaPeticion = System.currentTimeMillis();
	}
	/**
	 * Agrega la ciudad a la dirección.
	 *
	 * @param direccion dirección
	 * @param ciudad ciudad; puede estar vacía
	 * @return dirección con la ciudad
	 */
	private static String conCiudad(String direccion, String ciudad) {
		return vacio(ciudad) ? direccion.trim() : direccion.trim() + ", " + ciudad.trim();
	}

	/**
	 * Indica si un texto es {@code null} o solo tiene espacios.
	 *
	 * @param texto texto a revisar
	 * @return {@code true} si está vacío
	 */
	private static boolean vacio(String texto) {
		return texto == null || texto.isBlank();
	}

	/**
	 * Quita tildes, pasa a minúsculas y quita espacios a los lados, para
	 * comparar nombres de ciudades.
	 *
	 * @param texto texto a normalizar
	 * @return texto normalizado
	 */
	private static String normalizar(String texto) {
		return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
				.trim();
	}
}
