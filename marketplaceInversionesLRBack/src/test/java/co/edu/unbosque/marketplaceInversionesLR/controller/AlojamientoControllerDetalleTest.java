package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de las respuestas HTTP de HU-18, con el servicio de publicaciones
 * simulado. El flujo completo está en {@link DetalleAlojamientoTest}.
 */
class AlojamientoControllerDetalleTest {

	/** Publicación de prueba. */
	private static final Long ID = 40L;

	/** Mensaje cuando no existe o no se puede ver. */
	private static final String NO_EXISTE = "El alojamiento no existe.";

	/** Servicio de publicaciones simulado. */
	private final PublicacionService publicacionService = mock(PublicacionService.class);

	/** Controlador bajo prueba. */
	private final AlojamientoController controlador = new AlojamientoController(publicacionService,
			JsonMapper.builder().build(), mock(AuditoriaService.class));

	@Test
	@DisplayName("CA-103 Mis publicaciones usa el anfitrión del token y ordena de la más reciente a la más antigua")
	void misPublicaciones_CA103() {
		PaginaDTO<AlojamientoDTO> pagina = new PaginaDTO<>();
		when(publicacionService.mios(21L, PageRequest.of(1, 5, Sort.by("fechaCreacion").descending())))
				.thenReturn(pagina);

		ResponseEntity<Object> respuesta = controlador.mios(sesion(21L, "ROLE_ANFITRION"), 1, 5);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(pagina);
	}

	@Test
	@DisplayName("CA-093 Sin sesión responde 200 con el detalle de la publicación")
	void detalleSinSesion_CA093() {
		AlojamientoDTO detalle = new AlojamientoDTO();
		when(publicacionService.obtener(ID, null, false)).thenReturn(Resultado.exito(detalle));

		ResponseEntity<Object> respuesta = controlador.detalle(ID, null);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(detalle);
	}

	@Test
	@DisplayName("CA-164 La respuesta incluye la latitud y la longitud para el mapa")
	void detalleConCoordenadas_CA164() {
		AlojamientoDTO detalle = new AlojamientoDTO();
		detalle.setLatitud(6.23);
		detalle.setLongitud(-75.15);
		when(publicacionService.obtener(ID, null, false)).thenReturn(Resultado.exito(detalle));

		AlojamientoDTO cuerpo = (AlojamientoDTO) controlador.detalle(ID, null).getBody();

		assertThat(cuerpo.getLatitud()).isEqualTo(6.23);
		assertThat(cuerpo.getLongitud()).isEqualTo(-75.15);
	}

	@Test
	@DisplayName("Con sesión envía al servicio el usuario del token y si es administrador")
	void detalleConSesion() {
		AlojamientoDTO detalle = new AlojamientoDTO();
		when(publicacionService.obtener(ID, 21L, false)).thenReturn(Resultado.exito(detalle));
		when(publicacionService.obtener(ID, 22L, true)).thenReturn(Resultado.exito(detalle));

		assertThat(controlador.detalle(ID, sesion(21L, "ROLE_ANFITRION")).getBody()).isSameAs(detalle);
		assertThat(controlador.detalle(ID, sesion(22L, "ROLE_ADMIN")).getBody()).isSameAs(detalle);
	}

	@Test
	@DisplayName("Si no existe o no se puede ver responde 404")
	void detalleNoEncontrado() {
		when(publicacionService.obtener(ID, null, false))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE));

		ResponseEntity<Object> respuesta = controlador.detalle(ID, null);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", NO_EXISTE));
	}

	/**
	 * Crea la sesión de un usuario.
	 *
	 * @param idUsuario usuario
	 * @param rol       autoridad del usuario
	 * @return autenticación
	 */
	private static Authentication sesion(Long idUsuario, String rol) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(idUsuario);
		return new UsernamePasswordAuthenticationToken(usuario, null, List.of(new SimpleGrantedAuthority(rol)));
	}
}
