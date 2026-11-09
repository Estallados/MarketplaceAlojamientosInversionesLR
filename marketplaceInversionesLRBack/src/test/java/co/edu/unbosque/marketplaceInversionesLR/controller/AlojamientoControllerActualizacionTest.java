package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas de las respuestas HTTP de HU-16, con el servicio de publicaciones
 * simulado.
 */
class AlojamientoControllerActualizacionTest {

	/** Mensaje del rechazo por permisos. */
	private static final String SIN_PERMISO = "Solo el anfitrión propietario o un administrador puede modificar este alojamiento.";

	/** Servicio de publicaciones simulado. */
	private final PublicacionService publicacionService = mock(PublicacionService.class);

	/** Controlador bajo prueba. */
	private final AlojamientoController controlador = new AlojamientoController(publicacionService,
			JsonMapper.builder().build(), mock(AuditoriaService.class));

	/** Sesión del anfitrión 21. */
	private final Authentication anfitrion = sesion(21L);

	/** Sesión del administrador 1. */
	private final Authentication administrador = new UsernamePasswordAuthenticationToken(usuario(1L), null,
			List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

	@Test
	@DisplayName("CA-100 Editar responde 200 con la publicación actualizada")
	void editarResponde200_CA100() {
		AlojamientoDTO actualizado = new AlojamientoDTO();
		actualizado.setTitulo("Nuevo");
		AlojamientoDTO datos = new AlojamientoDTO();
		when(publicacionService.actualizar(40L, datos, 21L, false)).thenReturn(Resultado.exito(actualizado));

		ResponseEntity<Object> respuesta = controlador.actualizar(40L, datos, anfitrion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(actualizado);
	}

	@Test
	@DisplayName("CA-113 Editar le indica al servicio que quien solicita es administrador")
	void editarComoAdministrador_CA113() {
		AlojamientoDTO datos = new AlojamientoDTO();
		when(publicacionService.actualizar(40L, datos, 1L, true)).thenReturn(Resultado.exito(new AlojamientoDTO()));

		assertThat(controlador.actualizar(40L, datos, administrador).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	@DisplayName("CA-101 Editar sin ser propietario ni administrador responde 403")
	void editarSinPermiso_CA101() {
		when(publicacionService.actualizar(any(), any(), any(), any(Boolean.class)))
				.thenReturn(Resultado.error(Resultado.SIN_PERMISO, SIN_PERMISO));

		ResponseEntity<Object> respuesta = controlador.actualizar(40L, new AlojamientoDTO(), anfitrion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(mensaje(respuesta)).isEqualTo(SIN_PERMISO);
	}

	@Test
	@DisplayName("Las imágenes responden 201 al subir, 200 al marcar principal y 409 al borrar la última")
	void imagenes() {
		when(publicacionService.subirImagenes(40L, List.of(), 21L, false)).thenReturn(Resultado.exito(List.of()));
		when(publicacionService.marcarPrincipal(40L, 2L, 21L, false)).thenReturn(Resultado.exito(List.of()));
		when(publicacionService.eliminarImagen(40L, 2L, 21L, false))
				.thenReturn(Resultado.error(Resultado.ESTADO_INVALIDO, "Debe quedar una."));
		when(publicacionService.eliminarImagen(40L, 3L, 21L, false)).thenReturn(Resultado.exito(List.of()));

		assertThat(controlador.subirImagenes(40L, List.of(), anfitrion).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(controlador.marcarPrincipal(40L, 2L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(controlador.eliminarImagen(40L, 2L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(controlador.eliminarImagen(40L, 3L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	@DisplayName("CA-185 Agregar un servicio responde 201 y modificarlo 200")
	void agregarYModificarServicio_CA185() {
		ServicioAdicionalDTO datos = new ServicioAdicionalDTO();
		when(publicacionService.agregarServicio(40L, datos, 21L, false)).thenReturn(Resultado.exito(datos));
		when(publicacionService.actualizarServicio(40L, 7L, datos, 21L, false)).thenReturn(Resultado.exito(datos));

		assertThat(controlador.agregarServicio(40L, datos, anfitrion).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(controlador.actualizarServicio(40L, 7L, datos, anfitrion).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	@DisplayName("CA-185 Eliminar un servicio responde 200 con el mensaje de confirmación")
	void eliminarServicio_CA185() {
		when(publicacionService.eliminarServicio(40L, 7L, 21L, false)).thenReturn(Resultado.exito(null));

		ResponseEntity<Object> respuesta = controlador.eliminarServicio(40L, 7L, anfitrion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(mensaje(respuesta)).isEqualTo("Servicio adicional eliminado.");
	}

	@Test
	@DisplayName("CA-114 Eliminar un servicio ajeno responde 403 y uno inexistente 404")
	void eliminarServicioRechazado_CA114() {
		when(publicacionService.eliminarServicio(40L, 7L, 21L, false))
				.thenReturn(Resultado.error(Resultado.SIN_PERMISO, SIN_PERMISO));
		when(publicacionService.eliminarServicio(40L, 8L, 21L, false))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, "El servicio adicional no existe."));

		assertThat(controlador.eliminarServicio(40L, 7L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(controlador.eliminarServicio(40L, 8L, anfitrion).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	/**
	 * Mensaje del cuerpo de una respuesta.
	 *
	 * @param respuesta respuesta del controlador
	 * @return mensaje
	 */
	private static Object mensaje(ResponseEntity<Object> respuesta) {
		return ((Map<?, ?>) respuesta.getBody()).get("message");
	}

	/**
	 * Sesión autenticada de un usuario sin rol de administrador.
	 *
	 * @param idUsuario id del usuario
	 * @return autenticación con el usuario como principal
	 */
	private static Authentication sesion(Long idUsuario) {
		return new UsernamePasswordAuthenticationToken(usuario(idUsuario), null, List.of());
	}

	/**
	 * Usuario de prueba.
	 *
	 * @param idUsuario id del usuario
	 * @return usuario
	 */
	private static Usuario usuario(Long idUsuario) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(idUsuario);
		return usuario;
	}
}
