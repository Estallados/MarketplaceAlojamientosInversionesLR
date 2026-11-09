package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de las respuestas HTTP de HU-17, con el servicio de publicaciones
 * simulado.
 */
class AlojamientoControllerEstadoTest {

	/** Servicio de publicaciones simulado. */
	private final PublicacionService publicacionService = mock(PublicacionService.class);

	/** Controlador bajo prueba. */
	private final AlojamientoController controlador = new AlojamientoController(publicacionService,
			JsonMapper.builder().build(), mock(AuditoriaService.class));

	/** Sesión del anfitrión 21. */
	private final Authentication anfitrion = sesion();

	@Test
	@DisplayName("CA-157 Enviar a revisión responde 200 con la publicación PENDIENTE_REVISION")
	void enviarRevision_CA157() {
		AlojamientoDTO enviado = conEstado(EstadoAlojamiento.PENDIENTE_REVISION);
		when(publicacionService.enviarRevision(40L, 21L)).thenReturn(Resultado.exito(enviado));

		ResponseEntity<Object> respuesta = controlador.enviarRevision(40L, anfitrion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(enviado);
	}

	@Test
	@DisplayName("CA-108 Pausar responde 200 con la publicación PAUSADA")
	void pausar_CA108() {
		AlojamientoDTO pausada = conEstado(EstadoAlojamiento.PAUSADA);
		when(publicacionService.pausar(40L, 21L)).thenReturn(Resultado.exito(pausada));

		ResponseEntity<Object> respuesta = controlador.pausar(40L, anfitrion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(pausada);
	}

	@Test
	@DisplayName("CA-104 Una transición no permitida responde 409")
	void transicionNoPermitida_CA104() {
		when(publicacionService.reanudar(40L, 21L))
				.thenReturn(Resultado.error(Resultado.ESTADO_INVALIDO, "No se puede pasar de BLOQUEADA a ACTIVA."));

		assertThat(controlador.reanudar(40L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	@DisplayName("El historial responde 200 y 404 si no se puede ver")
	void historial() {
		when(publicacionService.historial(40L, 21L, false)).thenReturn(Resultado.exito(List.of()));
		when(publicacionService.historial(41L, 21L, false))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, "El alojamiento no existe."));

		assertThat(controlador.historial(40L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(controlador.historial(41L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * Publicación con el estado indicado.
	 *
	 * @param estado estado
	 * @return publicación
	 */
	private static AlojamientoDTO conEstado(EstadoAlojamiento estado) {
		AlojamientoDTO dto = new AlojamientoDTO();
		dto.setEstado(estado);
		return dto;
	}

	/**
	 * Sesión del anfitrión de prueba.
	 *
	 * @return autenticación con el usuario como principal
	 */
	private static Authentication sesion() {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(21L);
		return new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}
}
