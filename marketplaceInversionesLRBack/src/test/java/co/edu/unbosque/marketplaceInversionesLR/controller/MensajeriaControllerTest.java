package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConversacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MensajeDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.MensajeriaService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de {@link MensajeriaController} (HU-28): el usuario sale
 * del token y cada resultado se convierte en la respuesta del contrato.
 */
@ExtendWith(MockitoExtension.class)
class MensajeriaControllerTest {

	/** Usuario autenticado. */
	private static final Long USUARIO = 5L;

	/** Alojamiento. */
	private static final Long ALOJAMIENTO = 40L;

	@Mock
	private MensajeriaService mensajeriaService;

	private MensajeriaController controlador;

	/** Sesión del usuario. */
	private Authentication sesion;

	@BeforeEach
	void preparar() {
		controlador = new MensajeriaController(mensajeriaService);
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(USUARIO);
		sesion = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}

	@Test
	@DisplayName("CA-169 Iniciar responde 201 si la conversación es nueva y 200 si ya existía")
	void iniciar_CA169() {
		ConversacionDTO datos = new ConversacionDTO();
		datos.setIdAlojamiento(ALOJAMIENTO);
		ConversacionDTO creada = new ConversacionDTO();
		when(mensajeriaService.iniciar(datos, USUARIO)).thenReturn(Resultado.exito(creada));
		when(mensajeriaService.tieneConversacion(USUARIO, ALOJAMIENTO)).thenReturn(false, true);

		assertThat(controlador.iniciar(datos, sesion).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(controlador.iniciar(datos, sesion).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	@DisplayName("CA-169 Iniciar con su propio alojamiento responde 403")
	void iniciarPropio_CA169() {
		ConversacionDTO datos = new ConversacionDTO();
		datos.setIdAlojamiento(ALOJAMIENTO);
		when(mensajeriaService.iniciar(datos, USUARIO))
				.thenReturn(Resultado.error(Resultado.SIN_PERMISO, MensajeriaService.PROPIO_ALOJAMIENTO));

		assertThat(controlador.iniciar(datos, sesion).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("CA-165 Enviar un mensaje responde 201 y uno vacío 400")
	void enviar_CA165() {
		MensajeDTO datos = new MensajeDTO();
		datos.setContenido("Hola");
		when(mensajeriaService.enviar(3L, "Hola", USUARIO)).thenReturn(Resultado.exito(new MensajeDTO()));
		when(mensajeriaService.enviar(3L, "", USUARIO))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, MensajeriaService.MENSAJE_INVALIDO));

		assertThat(controlador.enviar(3L, datos, sesion).getStatusCode()).isEqualTo(HttpStatus.CREATED);
		datos.setContenido("");
		assertThat(controlador.enviar(3L, datos, sesion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("CA-167 Leer mensajes de una conversación ajena responde 403; los propios en orden de envío")
	void mensajes_CA167() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		when(mensajeriaService.mensajes(eq(3L), eq(USUARIO), pagina.capture()))
				.thenReturn(Resultado.exito(new PaginaDTO<>()))
				.thenReturn(Resultado.error(Resultado.SIN_PERMISO, MensajeriaService.NO_PARTICIPA));

		assertThat(controlador.mensajes(3L, sesion, 0, 50).getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(controlador.mensajes(3L, sesion, 0, 50).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(pagina.getValue().getSort().getOrderFor("fechaEnvio").isAscending()).isTrue();
		assertThat(pagina.getValue().getSort().getOrderFor("idMensaje")).isNotNull();
	}

	@Test
	@DisplayName("CA-167 Mis conversaciones pide las del usuario del token, las más recientes primero")
	void listar_CA167() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		PaginaDTO<ConversacionDTO> resultado = new PaginaDTO<>();
		when(mensajeriaService.listar(eq(USUARIO), pagina.capture())).thenReturn(resultado);

		assertThat(controlador.listar(sesion, 0, 20).getBody()).isSameAs(resultado);
		assertThat(pagina.getValue().getSort().getOrderFor("fechaCreacion").isDescending()).isTrue();
	}
}
