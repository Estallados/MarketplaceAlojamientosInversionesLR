package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de la auditoría al crear publicaciones (HU-26), con el servicio de
 * publicaciones simulado.
 */
class AlojamientoControllerAuditoriaTest {

	/** Servicio de publicaciones simulado. */
	private final PublicacionService publicacionService = mock(PublicacionService.class);

	/** Auditoría simulada. */
	private final AuditoriaService auditoriaService = mock(AuditoriaService.class);

	/** Controlador bajo prueba. */
	private final AlojamientoController controlador = new AlojamientoController(publicacionService,
			JsonMapper.builder().build(), auditoriaService);

	/** Sesión del anfitrión 21. */
	private final Authentication anfitrion = sesion(21L);

	@Test
	@DisplayName("CA-136 Una publicación creada queda en la auditoría como EXITOSO con su id")
	void publicacionCreadaAuditada_CA136() {
		AlojamientoDTO creado = new AlojamientoDTO();
		creado.setIdAlojamiento(40L);
		when(publicacionService.crear(any(), any(), eq(21L))).thenReturn(Resultado.exito(creado));

		assertThat(controlador.crear("{\"titulo\": \"Casa\"}", List.of(), anfitrion).getStatusCode())
				.isEqualTo(HttpStatus.CREATED);
		verify(auditoriaService).registrar(21L, AuditoriaService.ALOJAMIENTO_CREADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, 40L, ResultadoAuditoria.EXITOSO, null);
	}

	@Test
	@DisplayName("CA-137 Una publicación rechazada queda a nombre del anfitrión con el motivo")
	void publicacionRechazadaAuditada_CA137() {
		when(publicacionService.crear(any(), any(), eq(21L)))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, "Debes enviar al menos una imagen."));

		assertThat(controlador.crear("{\"titulo\": \"Casa\"}", null, anfitrion).getStatusCode())
				.isEqualTo(HttpStatus.BAD_REQUEST);
		verify(auditoriaService).registrar(21L, AuditoriaService.ALOJAMIENTO_CREADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, null, ResultadoAuditoria.RECHAZADO,
				"Debes enviar al menos una imagen.");
	}

	@Test
	@DisplayName("Un JSON inválido en 'datos' responde 400 y queda en la auditoría como RECHAZADO")
	void jsonInvalidoAuditado() {
		assertThat(controlador.crear("no es json", null, anfitrion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		verify(publicacionService, never()).crear(any(), any(), any());
		verify(auditoriaService).registrar(eq(21L), eq(AuditoriaService.ALOJAMIENTO_CREADO),
				eq(AuditoriaService.ENTIDAD_ALOJAMIENTO), isNull(), eq(ResultadoAuditoria.RECHAZADO), anyString());
	}

	/**
	 * Sesión autenticada de un usuario.
	 *
	 * @param idUsuario id del usuario
	 * @return autenticación con el usuario como principal
	 */
	private static Authentication sesion(Long idUsuario) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(idUsuario);
		return new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}
}
