package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.exception.AlmacenamientoNoDisponibleException;
import co.edu.unbosque.marketplaceInversionesLR.service.AlmacenamientoService;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenGuardada;

/**
 * Pruebas unitarias del almacén en memoria de las imágenes. Cloudinary se
 * simula.
 */
@ExtendWith(MockitoExtension.class)
class ImagenRepositoryTest {

	@Mock
	private AlmacenamientoService almacenamientoService;

	private ImagenRepository repositorio;

	@BeforeEach
	void preparar() {
		repositorio = new ImagenRepository(almacenamientoService);
	}

	@AfterEach
	void limpiarTransaccion() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("Al arrancar carga las imágenes de Cloudinary, ordenadas por su orden")
	void cargaAlArrancar() {
		when(almacenamientoService.listarImagenes()).thenReturn(List.of(
				new ImagenGuardada("b", "https://img/b", 7L, false, 2L),
				new ImagenGuardada("a", "https://img/a", 7L, true, 1L)));

		repositorio.alArrancar();
		repositorio.reintentarCarga();

		assertThat(repositorio.findByIdAlojamiento(7L)).extracting(Imagen::getUrl).containsExactly("https://img/a",
				"https://img/b");
		assertThat(repositorio.findByIdAlojamiento(8L)).isEmpty();
		// Ya cargó: no vuelve a consultar
		verify(almacenamientoService, times(1)).listarImagenes();
	}

	@Test
	@DisplayName("Si Cloudinary falla al arrancar, reintenta más tarde")
	void reintentaLaCarga() {
		when(almacenamientoService.listarImagenes())
				.thenThrow(new AlmacenamientoNoDisponibleException("caído"))
				.thenReturn(List.of(new ImagenGuardada("a", "https://img/a", 7L, true, 1L)));

		repositorio.alArrancar();
		assertThat(repositorio.findByIdAlojamiento(7L)).isEmpty();
		repositorio.reintentarCarga();

		assertThat(repositorio.findByIdAlojamiento(7L)).hasSize(1);
	}

	@Test
	@DisplayName("Guarda una imagen nueva con un id estable y entrega copias")
	void guardaYBusca() {
		Imagen imagen = nueva("pruebas/alojamientos/7/x", true);

		repositorio.save(imagen);

		assertThat(imagen.getIdImagen()).isNotNull();
		Imagen encontrada = repositorio.findByIdImagenAndIdAlojamiento(imagen.getIdImagen(), 7L).orElseThrow();
		assertThat(encontrada.getUrl()).isEqualTo("https://img/x");
		encontrada.setUrl("cambiada");
		assertThat(repositorio.findByIdAlojamiento(7L).get(0).getUrl()).isEqualTo("https://img/x");
		assertThat(repositorio.findByIdImagenAndIdAlojamiento(imagen.getIdImagen(), 8L)).isEmpty();
		// Una imagen nueva ya subió sus datos con el archivo
		verify(almacenamientoService, never()).actualizarImagen(any());
	}

	@Test
	@DisplayName("Si cambia la portada de una imagen existente, la actualiza en Cloudinary")
	void actualizaLaPortada() {
		Imagen imagen = repositorio.save(nueva("id-1", true));
		Imagen sinPortada = new Imagen(imagen);
		sinPortada.setPrincipal(false);

		repositorio.save(sinPortada);

		verify(almacenamientoService).actualizarImagen(any(Imagen.class));
		assertThat(repositorio.findByIdAlojamiento(7L).get(0).getPrincipal()).isFalse();
	}

	@Test
	@DisplayName("Si la transacción se revierte, la memoria vuelve a como estaba")
	void revierteAlFallar() {
		Imagen existente = repositorio.save(nueva("id-1", true));
		TransactionSynchronizationManager.initSynchronization();

		repositorio.save(nueva("id-2", false));
		Imagen cambiada = new Imagen(existente);
		cambiada.setUrl("otra");
		repositorio.save(cambiada);
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		assertThat(repositorio.findByIdAlojamiento(7L)).singleElement().satisfies(i -> {
			assertThat(i.getRutaArchivo()).isEqualTo("id-1");
			assertThat(i.getUrl()).isEqualTo("https://img/x");
		});
	}

	@Test
	@DisplayName("Si la transacción se revierte, una imagen borrada vuelve a estar")
	void revierteElBorrado() {
		Imagen existente = repositorio.save(nueva("id-1", true));
		TransactionSynchronizationManager.initSynchronization();

		repositorio.delete(existente);
		assertThat(repositorio.findByIdAlojamiento(7L)).isEmpty();
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		assertThat(repositorio.findByIdAlojamiento(7L)).hasSize(1);
	}

	@Test
	@DisplayName("Al confirmar la transacción se aplican los cambios; borrar quita la imagen")
	void confirmaYBorra() {
		TransactionSynchronizationManager.initSynchronization();
		Imagen imagen = repositorio.save(nueva("id-1", true));
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
		TransactionSynchronizationManager.clearSynchronization();

		repositorio.delete(imagen);

		assertThat(repositorio.findByIdAlojamiento(7L)).isEmpty();
	}

	@Test
	@DisplayName("El orden de cada imagen nueva siempre es mayor que el anterior")
	void ordenCreciente() {
		long primero = repositorio.siguienteOrden();

		assertThat(repositorio.siguienteOrden()).isGreaterThan(primero);
	}

	@Test
	@DisplayName("El id de una imagen es estable y cabe en un número de JavaScript")
	void idEstable() {
		assertThat(ImagenRepository.idDe("abc")).isEqualTo(ImagenRepository.idDe("abc"))
				.isNotEqualTo(ImagenRepository.idDe("abd")).isLessThan(1L << 52).isNotNegative();
	}

	/**
	 * Imagen nueva del alojamiento 7.
	 *
	 * @param ruta      id en Cloudinary
	 * @param principal si es la portada
	 * @return imagen sin id
	 */
	private static Imagen nueva(String ruta, boolean principal) {
		Imagen imagen = new Imagen();
		imagen.setIdAlojamiento(7L);
		imagen.setRutaArchivo(ruta);
		imagen.setUrl("https://img/x");
		imagen.setPrincipal(principal);
		imagen.setOrden(1L);
		return imagen;
	}
}
