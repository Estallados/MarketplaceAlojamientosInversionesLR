package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.NotificacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de {@link NotificacionController} (HU-02): el usuario sale
 * del token y cada resultado se convierte en la respuesta del contrato.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionControllerTest {

	/** Usuario autenticado. */
	private static final Long USUARIO = 12L;

	@Mock
	private NotificacionService notificacionService;

	private NotificacionController controlador;

	/** Sesión del usuario. */
	private Authentication sesion;

	@BeforeEach
	void preparar() {
		controlador = new NotificacionController(notificacionService);
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(USUARIO);
		sesion = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}

	@Test
	@DisplayName("CA-013 Listar pide las del usuario del token, las más recientes primero y con el tamaño limitado")
	void listar_CA013() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		PaginaDTO<NotificacionDTO> resultado = new PaginaDTO<>();
		when(notificacionService.listar(eq(USUARIO), pagina.capture())).thenReturn(resultado);

		assertThat(controlador.listar(sesion, -2, 500).getBody()).isSameAs(resultado);

		assertThat(pagina.getValue().getPageNumber()).isZero();
		assertThat(pagina.getValue().getPageSize()).isEqualTo(100);
		assertThat(pagina.getValue().getSort().getOrderFor("fecha").isDescending()).isTrue();
		assertThat(pagina.getValue().getSort().getOrderFor("idNotificacion").isDescending()).isTrue();
	}

	@Test
	@DisplayName("El contador y el marcado masivo responden {cantidad} y {actualizadas}")
	void contadores() {
		when(notificacionService.contarNoLeidas(USUARIO)).thenReturn(3L);
		when(notificacionService.marcarTodasLeidas(USUARIO)).thenReturn(2);

		assertThat(controlador.noLeidas(sesion).getBody()).isEqualTo(Map.of("cantidad", 3L));
		assertThat(controlador.marcarTodasLeidas(sesion).getBody()).isEqualTo(Map.of("actualizadas", 2));
	}

	@Test
	@DisplayName("CA-013 Marcar una responde 200 con la notificación o 404 si no es suya")
	void marcarUna_CA013() {
		NotificacionDTO dto = new NotificacionDTO();
		when(notificacionService.marcarLeida(21L, USUARIO)).thenReturn(Resultado.exito(dto));
		when(notificacionService.marcarLeida(eq(99L), any())).thenReturn(
				Resultado.error(Resultado.NO_ENCONTRADO, NotificacionService.NO_EXISTE));

		assertThat(controlador.marcarLeida(21L, sesion).getBody()).isSameAs(dto);
		ResponseEntity<Object> ajena = controlador.marcarLeida(99L, sesion);
		assertThat(ajena.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(ajena.getBody()).isEqualTo(Map.of("message", NotificacionService.NO_EXISTE));
	}
}
