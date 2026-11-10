package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.FavoritoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Favorito;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.FavoritoRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de las reglas de favoritos (HU-29), con los repositorios
 * simulados y el {@link ModelMapper} real.
 */
@ExtendWith(MockitoExtension.class)
class FavoritoServiceTest {

	private static final Long ID_USUARIO = 7L;

	private static final Long ID_ALOJAMIENTO = 3L;

	@Mock
	private FavoritoRepository favoritoRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private PublicacionService publicacionService;

	private FavoritoService servicio;

	/** Alojamiento publicado que se guarda en favoritos. */
	private Alojamiento alojamiento;

	/** Datos del alojamiento que arma el servicio de publicaciones. */
	private AlojamientoDTO alojamientoDTO;

	@BeforeEach
	void preparar() {
		servicio = new FavoritoService(favoritoRepo, alojamientoRepo, publicacionService, new ModelMapper());
		alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID_ALOJAMIENTO);
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamientoDTO = new AlojamientoDTO();
	}

	@Test
	@DisplayName("CA-170 Lista solo los favoritos del usuario, con los datos del alojamiento")
	void listaLosDelUsuario_CA170() {
		Pageable pagina = PageRequest.of(0, 20);
		when(favoritoRepo.findByIdUsuario(ID_USUARIO, pagina)).thenReturn(new PageImpl<>(List.of(favorito(1L))));
		alojamientoEncontrado();

		PaginaDTO<FavoritoDTO> resultado = servicio.listar(ID_USUARIO, pagina);

		assertThat(resultado.getTotalElementos()).isEqualTo(1);
		FavoritoDTO dto = resultado.getContenido().get(0);
		assertThat(dto.getIdFavorito()).isEqualTo(1L);
		assertThat(dto.getIdUsuario()).isEqualTo(ID_USUARIO);
		assertThat(dto.getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(dto.getAlojamiento()).isSameAs(alojamientoDTO);
	}

	@Test
	@DisplayName("CA-170 Sin favoritos la página viene vacía")
	void listaVacia_CA170() {
		Pageable pagina = PageRequest.of(0, 20);
		when(favoritoRepo.findByIdUsuario(ID_USUARIO, pagina)).thenReturn(new PageImpl<>(List.of()));

		PaginaDTO<FavoritoDTO> resultado = servicio.listar(ID_USUARIO, pagina);

		assertThat(resultado.getContenido()).isEmpty();
		assertThat(resultado.getTotalElementos()).isZero();
	}

	@Test
	@DisplayName("CA-171 Eliminar borra el favorito del usuario")
	void elimina_CA171() {
		Favorito favorito = favorito(1L);
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO))
				.thenReturn(Optional.of(favorito));

		assertThat(servicio.eliminar(ID_USUARIO, ID_ALOJAMIENTO).esExito()).isTrue();

		verify(favoritoRepo).delete(favorito);
	}

	@Test
	@DisplayName("CA-171 Eliminar lo que no está en favoritos responde NO_ENCONTRADO y no borra nada")
	void eliminaLoQueNoEsta_CA171() {
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Optional.empty());

		Resultado<Void> resultado = servicio.eliminar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo(FavoritoService.NO_ES_FAVORITO);
		verify(favoritoRepo, never()).delete(any());
	}

	@Test
	@DisplayName("CA-173 Agregar registra el favorito con el usuario, el alojamiento y la fecha")
	void agrega_CA173() {
		alojamientoEncontrado();
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Optional.empty());
		when(favoritoRepo.saveAndFlush(any())).thenReturn(favorito(1L));

		Resultado<FavoritoDTO> resultado = servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getIdFavorito()).isEqualTo(1L);
		assertThat(resultado.getDato().getAlojamiento()).isSameAs(alojamientoDTO);
		ArgumentCaptor<Favorito> guardado = ArgumentCaptor.forClass(Favorito.class);
		verify(favoritoRepo).saveAndFlush(guardado.capture());
		assertThat(guardado.getValue().getIdUsuario()).isEqualTo(ID_USUARIO);
		assertThat(guardado.getValue().getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(guardado.getValue().getFechaAgregado()).isNotNull();
	}

	@Test
	@DisplayName("CA-173 Si ya estaba, devuelve el mismo favorito y no guarda otro")
	void noDuplica_CA173() {
		alojamientoEncontrado();
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO))
				.thenReturn(Optional.of(favorito(1L)));

		Resultado<FavoritoDTO> resultado = servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.getDato().getIdFavorito()).isEqualTo(1L);
		verify(favoritoRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("CA-173 Si dos solicitudes chocan, responde con el favorito que quedó guardado")
	void solicitudesSimultaneas_CA173() {
		alojamientoEncontrado();
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Optional.empty(),
				Optional.of(favorito(9L)));
		when(favoritoRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_favorito"));

		Resultado<FavoritoDTO> resultado = servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getIdFavorito()).isEqualTo(9L);
	}

	@Test
	@DisplayName("CA-173 Si el choque no dejó ningún favorito, responde NO_ENCONTRADO")
	void choqueSinFavorito_CA173() {
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO)).thenReturn(Optional.empty());
		when(favoritoRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk_favorito"));

		Resultado<FavoritoDTO> resultado = servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo(FavoritoService.NO_EXISTE);
	}

	@Test
	@DisplayName("CA-173 Un alojamiento que no existe no se puede agregar")
	void alojamientoInexistente_CA173() {
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.empty());

		Resultado<FavoritoDTO> resultado = servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		verify(favoritoRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("CA-173 Un alojamiento sin publicar no se puede agregar")
	void alojamientoSinPublicar_CA173() {
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));

		Resultado<FavoritoDTO> resultado = servicio.agregar(ID_USUARIO, ID_ALOJAMIENTO);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		verify(favoritoRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("esFavorito indica si el alojamiento ya está en la lista")
	void esFavorito() {
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, ID_ALOJAMIENTO))
				.thenReturn(Optional.of(favorito(1L)));
		when(favoritoRepo.findByIdUsuarioAndIdAlojamiento(ID_USUARIO, 99L)).thenReturn(Optional.empty());

		assertThat(servicio.esFavorito(ID_USUARIO, ID_ALOJAMIENTO)).isTrue();
		assertThat(servicio.esFavorito(ID_USUARIO, 99L)).isFalse();
	}

	/** Simula que el alojamiento existe y que se arman sus datos. */
	private void alojamientoEncontrado() {
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
		when(publicacionService.convertirDTO(alojamiento)).thenReturn(alojamientoDTO);
	}

	private Favorito favorito(Long id) {
		Favorito favorito = new Favorito();
		favorito.setIdFavorito(id);
		favorito.setIdUsuario(ID_USUARIO);
		favorito.setIdAlojamiento(ID_ALOJAMIENTO);
		favorito.setFechaAgregado(LocalDateTime.of(2026, 11, 9, 10, 30));
		return favorito;
	}
}
