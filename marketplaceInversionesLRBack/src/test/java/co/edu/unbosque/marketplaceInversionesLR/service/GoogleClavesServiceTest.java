package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pruebas unitarias de la descarga de las claves públicas de Google. La
 * petición HTTP se simula.
 */
@ExtendWith(MockitoExtension.class)
class GoogleClavesServiceTest {

	/** Convierte los JWK de prueba. */
	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Clave RSA de prueba publicada como JWK. */
	private static final RSAPublicKey CLAVE = generarClave();

	@Mock
	private ClienteHttp clienteHttp;

	private GoogleClavesService servicio;

	@BeforeEach
	void preparar() {
		servicio = new GoogleClavesService(clienteHttp);
	}

	@Test
	@DisplayName("Descarga las claves RSA, ignora las de otro tipo y las guarda mientras sean vigentes")
	void descargaYGuarda() {
		responder(jwks(), "public, max-age=3600");

		PublicKey clave = servicio.buscar("kid-1");

		assertThat(clave).isEqualTo(CLAVE);
		assertThat(servicio.buscar("kid-1")).isEqualTo(CLAVE);
		assertThat(servicio.buscar("kid-ec")).isNull();
		// Una clave desconocida no vuelve a descargar antes de un minuto
		assertThat(servicio.buscar("kid-nuevo")).isNull();
		verify(clienteHttp, times(1)).getJson(GoogleClavesService.URL_CLAVES);
	}

	@Test
	@DisplayName("Sin max-age las claves duran una hora; sin cuerpo no hay claves")
	void sinCacheControlNiCuerpo() {
		when(clienteHttp.getJson(GoogleClavesService.URL_CLAVES)).thenReturn(ResponseEntity.ok().body(null));

		assertThat(servicio.buscar("kid-1")).isNull();
	}

	@Test
	@DisplayName("Si la descarga falla no se cae: responde sin clave")
	void descargaFallida() {
		when(clienteHttp.getJson(GoogleClavesService.URL_CLAVES)).thenThrow(new RestClientException("sin red"));

		assertThat(servicio.buscar("kid-1")).isNull();
	}

	@Test
	@DisplayName("Con max-age cero las claves vencen y se vuelven a descargar")
	void clavesVencidas() {
		responder(jwks(), "max-age=0");

		assertThat(servicio.buscar("kid-1")).isEqualTo(CLAVE);
		assertThat(servicio.buscar("kid-1")).isEqualTo(CLAVE);
		verify(clienteHttp, times(2)).getJson(GoogleClavesService.URL_CLAVES);
	}

	@Test
	@DisplayName("Un JWK con datos inválidos se ignora sin romper el servicio")
	void jwkInvalido() {
		ObjectNode cuerpo = JSON.createObjectNode();
		cuerpo.putArray("keys").addObject().put("kty", "RSA").put("kid", "malo").put("n", "%%%").put("e", "AQAB");
		responder(cuerpo, null);

		assertThat(servicio.buscar("malo")).isNull();
	}

	/**
	 * Hace que la petición devuelva el cuerpo y el Cache-Control indicados.
	 *
	 * @param cuerpo       JSON de la respuesta
	 * @param cacheControl valor de la cabecera; puede ser null
	 */
	private void responder(JsonNode cuerpo, String cacheControl) {
		HttpHeaders encabezados = new HttpHeaders();
		if (cacheControl != null) {
			encabezados.setCacheControl(cacheControl);
		}
		when(clienteHttp.getJson(GoogleClavesService.URL_CLAVES))
				.thenReturn(ResponseEntity.ok().headers(encabezados).body(cuerpo));
	}

	/**
	 * Conjunto de claves con una RSA válida y una de otro tipo.
	 *
	 * @return JSON con el formato de Google
	 */
	private static JsonNode jwks() {
		ObjectNode cuerpo = JSON.createObjectNode();
		ArrayNode claves = cuerpo.putArray("keys");
		claves.addObject().put("kty", "RSA").put("kid", "kid-1").put("n", base64(CLAVE.getModulus()))
				.put("e", base64(CLAVE.getPublicExponent()));
		claves.addObject().put("kty", "EC").put("kid", "kid-ec");
		return cuerpo;
	}

	/**
	 * Codifica un número en Base64 URL sin relleno, como en los JWK.
	 *
	 * @param numero número a codificar
	 * @return texto codificado
	 */
	private static String base64(BigInteger numero) {
		byte[] bytes = numero.toByteArray();
		if (bytes[0] == 0) {
			byte[] sinSigno = new byte[bytes.length - 1];
			System.arraycopy(bytes, 1, sinSigno, 0, sinSigno.length);
			bytes = sinSigno;
		}
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Genera una clave pública RSA de prueba.
	 *
	 * @return clave pública
	 */
	private static RSAPublicKey generarClave() {
		try {
			KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
			generador.initialize(2048);
			return (RSAPublicKey) generador.generateKeyPair().getPublic();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
