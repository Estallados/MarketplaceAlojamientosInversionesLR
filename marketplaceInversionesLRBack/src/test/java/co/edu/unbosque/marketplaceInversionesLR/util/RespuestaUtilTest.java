package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;

/**
 * Pruebas unitarias de la traducción de {@link Resultado} a respuestas HTTP y
 * de la lectura del usuario autenticado.
 */
class RespuestaUtilTest {

	@Test
	@DisplayName("Un resultado exitoso responde 200 (u otro estado indicado) con el dato")
	void exito() {
		ResponseEntity<Object> ok = RespuestaUtil.desde(Resultado.exito("dato"));
		ResponseEntity<Object> creado = RespuestaUtil.desde(Resultado.exito("dato"), HttpStatus.CREATED);

		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).isEqualTo("dato");
		assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("Un error responde {message} con su estado, y agrega el dato cuando lo trae")
	void error() {
		ResponseEntity<Object> sinDato = RespuestaUtil
				.desde(Resultado.error(Resultado.DATOS_INVALIDOS, "Datos inválidos."));
		ResponseEntity<Object> conDato = RespuestaUtil
				.desde(Resultado.error(Resultado.ESTADO_INVALIDO, "Estado inválido.", "detalle"));

		assertThat(sinDato.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(sinDato.getBody()).isEqualTo(Map.of("message", "Datos inválidos."));
		assertThat(conDato.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conDato.getBody()).isEqualTo(Map.of("message", "Estado inválido.", "dato", "detalle"));
	}

	@Test
	@DisplayName("Cada código de Resultado tiene su estado HTTP")
	void estados() {
		assertThat(RespuestaUtil.estadoDe(Resultado.NO_ENCONTRADO)).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(RespuestaUtil.estadoDe(Resultado.DATOS_INVALIDOS)).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(RespuestaUtil.estadoDe(Resultado.SIN_PERMISO)).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(RespuestaUtil.estadoDe(Resultado.ESTADO_INVALIDO)).isEqualTo(HttpStatus.CONFLICT);
		assertThat(RespuestaUtil.estadoDe(Resultado.NO_DISPONIBLE)).isEqualTo(HttpStatus.CONFLICT);
		assertThat(RespuestaUtil.estadoDe(Resultado.PAGO_RECHAZADO)).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
		assertThat(RespuestaUtil.estadoDe(Resultado.PAGO_ERROR)).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(RespuestaUtil.estadoDe(99)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	@DisplayName("Lee el id del usuario autenticado; sin sesión o con otro principal devuelve null")
	void idUsuario() {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(7L);

		assertThat(RespuestaUtil.idUsuario(new UsernamePasswordAuthenticationToken(usuario, null, List.of())))
				.isEqualTo(7L);
		assertThat(RespuestaUtil.idUsuario(new UsernamePasswordAuthenticationToken("anonimo", null, List.of())))
				.isNull();
		assertThat(RespuestaUtil.idUsuario(null)).isNull();
	}

	@Test
	@DisplayName("La paginación corrige lo que manda el cliente y conserva el orden")
	void pagina() {
		Sort orden = Sort.by("fechaAgregado").descending();

		Pageable normal = RespuestaUtil.pagina(2, 10, orden);
		Pageable negativa = RespuestaUtil.pagina(-1, 0, orden);
		Pageable grande = RespuestaUtil.pagina(Integer.MAX_VALUE, 5000, orden);

		assertThat(normal.getPageNumber()).isEqualTo(2);
		assertThat(normal.getPageSize()).isEqualTo(10);
		assertThat(normal.getSort()).isEqualTo(orden);
		assertThat(negativa.getPageNumber()).isZero();
		assertThat(negativa.getPageSize()).isEqualTo(1);
		assertThat(grande.getPageSize()).isEqualTo(RespuestaUtil.TAMANIO_MAXIMO);
		assertThat(grande.getOffset()).isLessThanOrEqualTo(Integer.MAX_VALUE);
	}
}
