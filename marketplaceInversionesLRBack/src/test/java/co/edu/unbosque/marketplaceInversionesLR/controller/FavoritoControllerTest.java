package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.FavoritoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.FavoritoService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias del controlador de favoritos (HU-29), con el servicio
 * simulado. El flujo completo está en {@link FavoritosTest}.
 */
@ExtendWith(MockitoExtension.class)
class FavoritoControllerTest {

	/** Usuario del token. */
	private static final Long ID_USUARIO = 7L;

	private static final Long ID_ALOJAMIENTO = 3L;

	@Mock
	private FavoritoService servicio;

	private FavoritoController controlador;

	/** Autenticación del usuario del token. */
	private Authentication autenticacion;

	@BeforeEach
	void preparar() {
		controlador = new FavoritoController(servicio);
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(ID_USUARIO);
		autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, List.of());
	}

	@Test
	@DisplayName("CA-170 Consulta los favoritos del usuario del token, con los más recientes primero")
	void lista_CA170() {
		PaginaDTO<FavoritoDTO> pagina = new PaginaDTO<>();
		when(servicio.listar(eq(ID_USUARIO), any())).thenReturn(pagina);

		ResponseEntity<Object> respuesta = controlador.listar(autenticacion, 1, 5);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(pagina);
		ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
		verify(servicio).listar(eq(ID_USUARIO), paginacion.capture());
		assertThat(paginacion.getValue().getPageNumber()).isEqualTo(1);
		assertThat(paginacion.getValue().getPageSize()).isEqualTo(5);
		assertThat(paginacion.getValue().getSort()).isEqualTo(Sort.by("fechaAgregado").descending());
	}

	@Test
	@DisplayName("CA-171 Eliminar responde el mensaje de confirmación")
	void elimina_CA171() {
		when(servicio.eliminar(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Resultado.exito(null));

		ResponseEntity<Object> respuesta = controlador.eliminar(ID_ALOJAMIENTO, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", FavoritoController.QUITADO));
	}

	@Test
	@DisplayName("CA-171 Eliminar lo que no está en favoritos responde 404")
	void eliminaLoQueNoEsta_CA171() {
		when(servicio.eliminar(ID_USUARIO, ID_ALOJAMIENTO))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, FavoritoService.NO_ES_FAVORITO));

		ResponseEntity<Object> respuesta = controlador.eliminar(ID_ALOJAMIENTO, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", FavoritoService.NO_ES_FAVORITO));
	}

	@Test
	@DisplayName("CA-173 Agregar uno nuevo responde 201 con el favorito")
	void agregaNuevo_CA173() {
		FavoritoDTO favorito = new FavoritoDTO();
		when(servicio.esFavorito(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(false);
		when(servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Resultado.exito(favorito));

		ResponseEntity<Object> respuesta = controlador.agregar(ID_ALOJAMIENTO, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(respuesta.getBody()).isSameAs(favorito);
	}

	@Test
	@DisplayName("CA-173 Agregar uno que ya estaba responde 200 con el favorito existente")
	void agregaRepetido_CA173() {
		FavoritoDTO favorito = new FavoritoDTO();
		when(servicio.esFavorito(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(true);
		when(servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Resultado.exito(favorito));

		ResponseEntity<Object> respuesta = controlador.agregar(ID_ALOJAMIENTO, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).isSameAs(favorito);
	}

	@Test
	@DisplayName("CA-173 Agregar un alojamiento que no existe responde 404")
	void agregaInexistente_CA173() {
		when(servicio.esFavorito(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(false);
		when(servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO))
				.thenReturn(Resultado.error(Resultado.NO_ENCONTRADO, FavoritoService.NO_EXISTE));

		ResponseEntity<Object> respuesta = controlador.agregar(ID_ALOJAMIENTO, autenticacion);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(respuesta.getBody()).isEqualTo(Map.of("message", FavoritoService.NO_EXISTE));
	}
}
