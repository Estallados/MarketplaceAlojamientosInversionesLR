package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaEntradaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ResumenResenasDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.ResenaService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de {@link ResenaController} (HU-03): el autor sale del
 * token, las listas van de la más reciente a la más antigua y cada resultado
 * se convierte en la respuesta del contrato.
 */
@ExtendWith(MockitoExtension.class)
class ResenaControllerTest {

	/** Usuario autenticado. */
	private static final Long USUARIO = 3L;

	@Mock
	private ResenaService resenaService;

	private ResenaController controlador;

	/** Sesión del usuario. */
	private Authentication sesion;

	@BeforeEach
	void preparar() {
		controlador = new ResenaController(resenaService);
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(USUARIO);
		sesion = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}

	@Test
	@DisplayName("CA-018 Crear usa el usuario del token y responde 201; un rechazo responde 409 con el mensaje")
	void crear_CA018() {
		ResenaEntradaDTO datos = new ResenaEntradaDTO();
		ResenaDTO creada = new ResenaDTO();
		when(resenaService.crear(datos, USUARIO)).thenReturn(Resultado.exito(creada));

		ResponseEntity<Object> respuesta = controlador.crear(datos, sesion);
		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(respuesta.getBody()).isSameAs(creada);

		ResenaEntradaDTO repetida = new ResenaEntradaDTO();
		when(resenaService.crear(repetida, USUARIO))
				.thenReturn(Resultado.error(Resultado.ESTADO_INVALIDO, ResenaService.YA_RESENADA));
		ResponseEntity<Object> conflicto = controlador.crear(repetida, sesion);
		assertThat(conflicto.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflicto.getBody()).isEqualTo(Map.of("message", ResenaService.YA_RESENADA));
	}

	@Test
	@DisplayName("CA-019 La consulta pública pide las del alojamiento, las más recientes primero; 404 si no existe")
	void deAlojamiento_CA019() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		ResumenResenasDTO resumen = new ResumenResenasDTO();
		when(resenaService.resumen(eq(7L), pagina.capture())).thenReturn(Resultado.exito(resumen));
		when(resenaService.resumen(eq(404L), any()))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, CalendarioService.NO_EXISTE));

		assertThat(controlador.deAlojamiento(7L, -1, 500).getBody()).isSameAs(resumen);
		assertThat(pagina.getValue().getPageNumber()).isZero();
		assertThat(pagina.getValue().getPageSize()).isEqualTo(100);
		assertThat(pagina.getValue().getSort().getOrderFor("fecha").isDescending()).isTrue();
		assertThat(pagina.getValue().getSort().getOrderFor("idResena").isDescending()).isTrue();
		assertThat(controlador.deAlojamiento(404L, 0, 20).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("Mis reseñas y las del anfitrión usan el usuario del token")
	void misResenasYAnfitrion() {
		PaginaDTO<ResenaDTO> mias = new PaginaDTO<>();
		when(resenaService.listarMias(eq(USUARIO), any())).thenReturn(mias);
		ResumenResenasDTO resumen = new ResumenResenasDTO();
		when(resenaService.resumenDeAnfitrion(eq(USUARIO), isNull(), any())).thenReturn(Resultado.exito(resumen));

		assertThat(controlador.mias(sesion, 0, 20).getBody()).isSameAs(mias);
		assertThat(controlador.deAnfitrion(sesion, null, 0, 20).getBody()).isSameAs(resumen);
	}
}
