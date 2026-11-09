package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.SegundoFactorDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.SegundoFactorService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias del controlador del segundo factor (HU-30), con el
 * servicio simulado. El flujo completo está en {@link SegundoFactorTest}.
 */
@ExtendWith(MockitoExtension.class)
class SegundoFactorControllerTest {

	/** Usuario del token. */
	private static final Long ID_USUARIO = 7L;

	@Mock
	private SegundoFactorService servicio;

	private SegundoFactorController controlador;

	/** Autenticación del usuario del token. */
	private Authentication autenticacion;

	@BeforeEach
	void preparar() {
		controlador = new SegundoFactorController(servicio);
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(ID_USUARIO);
		autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}

	@Test
	@DisplayName("CA-178 Activar usa el usuario del token y devuelve el secreto y la URI")
	void activa_CA178() {
		SegundoFactorDTO secreto = new SegundoFactorDTO();
		secreto.setSecreto("ABC");
		secreto.setUri("otpauth://totp/x");
		when(servicio.activar(ID_USUARIO)).thenReturn(Resultado.exito(secreto));

		ResponseEntity<Object> respuesta = controlador.activar(autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(secreto);
	}

	@Test
	@DisplayName("CA-178 Confirmar con un código válido responde que quedó habilitado; si no, el error del servicio")
	void confirma_CA178() {
		when(servicio.confirmar(ID_USUARIO, "123456")).thenReturn(Resultado.exito(null));
		when(servicio.confirmar(ID_USUARIO, "000000"))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, SegundoFactorService.CODIGO_INVALIDO));

		ResponseEntity<Object> ok = controlador.confirmar(codigo("123456"), autenticacion);
		ResponseEntity<Object> malo = controlador.confirmar(codigo("000000"), autenticacion);

		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).isEqualTo(Map.of("message", SegundoFactorController.HABILITADO));
		assertThat(malo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("CA-177 Desactivar con un código válido responde que quedó deshabilitado; si no, el error del servicio")
	void desactiva_CA177() {
		when(servicio.desactivar(ID_USUARIO, "123456")).thenReturn(Resultado.exito(null));
		when(servicio.desactivar(ID_USUARIO, "000000"))
				.thenReturn(Resultado.error(Resultado.ESTADO_INVALIDO, SegundoFactorService.NO_HABILITADO));

		ResponseEntity<Object> ok = controlador.desactivar(codigo("123456"), autenticacion);
		ResponseEntity<Object> malo = controlador.desactivar(codigo("000000"), autenticacion);

		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).isEqualTo(Map.of("message", SegundoFactorController.DESHABILITADO));
		assertThat(malo.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	private static SegundoFactorDTO codigo(String codigo) {
		SegundoFactorDTO datos = new SegundoFactorDTO();
		datos.setCodigo(codigo);
		return datos;
	}
}
