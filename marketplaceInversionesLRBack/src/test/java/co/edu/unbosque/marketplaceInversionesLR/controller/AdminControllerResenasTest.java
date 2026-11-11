package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

import co.edu.unbosque.marketplaceInversionesLR.dto.MotivoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.ResenaService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de la moderación de reseñas en {@link AdminController}
 * (HU-14): el administrador sale del token, el listado va de la más reciente a
 * la más antigua y cada resultado se convierte en la respuesta del contrato.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerResenasTest {

	/** Administrador autenticado. */
	private static final Long ADMIN = 100L;

	@Mock
	private ResenaService resenaService;

	private AdminController controlador;

	/** Sesión del administrador. */
	private Authentication sesion;

	@BeforeEach
	void preparar() {
		controlador = new AdminController(null, null, null, null, null, resenaService);
		Usuario admin = new Usuario();
		admin.setIdUsuario(ADMIN);
		sesion = new UsernamePasswordAuthenticationToken(admin, null, List.of());
	}

	@Test
	@DisplayName("El listado filtra por estado, las más recientes primero y con el tamaño limitado")
	void listar() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		PaginaDTO<ResenaDTO> resultado = new PaginaDTO<>();
		when(resenaService.listarTodas(eq(EstadoResena.OCULTA), pagina.capture())).thenReturn(resultado);

		assertThat(controlador.resenas(EstadoResena.OCULTA, -1, 500).getBody()).isSameAs(resultado);
		assertThat(pagina.getValue().getPageNumber()).isZero();
		assertThat(pagina.getValue().getPageSize()).isEqualTo(100);
		assertThat(pagina.getValue().getSort().getOrderFor("fecha").isDescending()).isTrue();
		assertThat(pagina.getValue().getSort().getOrderFor("idResena").isDescending()).isTrue();
	}

	@Test
	@DisplayName("CA-066 Ocultar pasa el motivo y el administrador del token; sin cuerpo responde 400")
	void ocultar_CA066() {
		ResenaDTO oculta = new ResenaDTO();
		MotivoDTO datos = new MotivoDTO();
		datos.setMotivo("Spam");
		when(resenaService.moderar(9L, true, "Spam", ADMIN)).thenReturn(Resultado.exito(oculta));
		when(resenaService.moderar(9L, true, null, ADMIN))
				.thenReturn(Resultado.error(Resultado.DATOS_INVALIDOS, ResenaService.MOTIVO_OBLIGATORIO));

		assertThat(controlador.ocultarResena(9L, datos, sesion).getBody()).isSameAs(oculta);
		ResponseEntity<Object> sinCuerpo = controlador.ocultarResena(9L, null, sesion);
		assertThat(sinCuerpo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(sinCuerpo.getBody()).isEqualTo(Map.of("message", ResenaService.MOTIVO_OBLIGATORIO));
	}

	@Test
	@DisplayName("Mostrar responde 200, o 404 si la reseña no existe")
	void mostrar() {
		ResenaDTO visible = new ResenaDTO();
		when(resenaService.moderar(eq(9L), eq(false), isNull(), eq(ADMIN))).thenReturn(Resultado.exito(visible));
		when(resenaService.moderar(eq(404L), eq(false), isNull(), eq(ADMIN)))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, ResenaService.NO_EXISTE));

		assertThat(controlador.mostrarResena(9L, sesion).getBody()).isSameAs(visible);
		assertThat(controlador.mostrarResena(404L, sesion).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
