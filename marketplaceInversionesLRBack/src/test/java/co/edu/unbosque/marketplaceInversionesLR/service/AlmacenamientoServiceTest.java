package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenGuardada;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.MetadatosImagen;

/**
 * Pruebas unitarias de la validación y el guardado de imágenes. Cloudinary se
 * simula.
 */
@ExtendWith(MockitoExtension.class)
class AlmacenamientoServiceTest {

	/** Primeros bytes de un JPEG. */
	static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 };

	/** Primeros bytes de un PNG. */
	static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0 };

	/** Primeros bytes de un WEBP. */
	static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' };

	@Mock
	private CloudinaryService cloudinaryService;

	private AlmacenamientoService servicio;

	@BeforeEach
	void preparar() {
		servicio = new AlmacenamientoService(cloudinaryService, "pruebas");
	}

	@AfterEach
	void limpiarTransaccion() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	@DisplayName("Acepta JPG, PNG y WEBP reconociéndolos por sus primeros bytes")
	void aceptaImagenes() {
		assertThat(servicio.errorImagen(archivo("a.jpg", JPEG))).isNull();
		assertThat(servicio.errorImagen(archivo("a.png", PNG))).isNull();
		assertThat(servicio.errorImagen(archivo("a.webp", WEBP))).isNull();
	}

	@Test
	@DisplayName("Rechaza archivos vacíos, de más de 5 MB o que no son imágenes aunque digan serlo")
	void rechazaArchivosInvalidos() {
		assertThat(servicio.errorImagen(null)).isEqualTo("El archivo está vacío.");
		assertThat(servicio.errorImagen(archivo("vacio.jpg", new byte[0]))).isEqualTo("El archivo está vacío.");
		assertThat(servicio.errorImagen(archivo("grande.jpg", new byte[(int) AlmacenamientoService.TAMANIO_MAXIMO + 1])))
				.isEqualTo("Cada imagen puede pesar máximo 5 MB.");
		assertThat(servicio.errorImagen(archivo("falsa.jpg", "%PDF-1.4".getBytes())))
				.isEqualTo("Solo se aceptan imágenes JPG, PNG o WEBP.");
		assertThat(servicio.errorImagen(archivo("riff.webp", new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' ' })))
				.isEqualTo("Solo se aceptan imágenes JPG, PNG o WEBP.");
	}

	@Test
	@DisplayName("Si no se puede leer el archivo, no se acepta")
	void archivoIlegible() throws IOException {
		MultipartFile ilegible = mock(MultipartFile.class);
		when(ilegible.isEmpty()).thenReturn(false);
		when(ilegible.getSize()).thenReturn(10L);
		when(ilegible.getInputStream()).thenThrow(new IOException("falla"));

		assertThat(servicio.errorImagen(ilegible)).isEqualTo("Solo se aceptan imágenes JPG, PNG o WEBP.");
	}

	@Test
	@DisplayName("Sube la imagen a la carpeta del alojamiento con sus datos (etiqueta, portada y orden)")
	void guardaLaImagen() {
		when(cloudinaryService.subir(any(), eq("pruebas/alojamientos/7"), any(), any()))
				.thenReturn(new ImagenSubida("pruebas/alojamientos/7/x", "https://img/x"));

		ImagenSubida subida = servicio.guardarImagen(archivo("a.jpg", JPEG), 7L, true, 3L);

		assertThat(subida.url()).isEqualTo("https://img/x");
		ArgumentCaptor<MetadatosImagen> metadatos = ArgumentCaptor.forClass(MetadatosImagen.class);
		verify(cloudinaryService).subir(eq(JPEG), eq("pruebas/alojamientos/7"), any(), metadatos.capture());
		assertThat(metadatos.getValue()).isEqualTo(new MetadatosImagen("pruebas-alojamientos", 7L, true, 3L));
		// Sin transacción no se programa ningún borrado
		verify(cloudinaryService, never()).borrar(any());
	}

	@Test
	@DisplayName("Si la transacción se revierte, la imagen subida se borra de Cloudinary")
	void borraSiSeRevierte() {
		when(cloudinaryService.subir(any(), any(), any(), any())).thenReturn(new ImagenSubida("id-1", "https://img/1"));
		TransactionSynchronizationManager.initSynchronization();

		servicio.guardarImagen(archivo("a.jpg", JPEG), 7L, false, 1L);
		List<TransactionSynchronization> sincronizaciones = TransactionSynchronizationManager.getSynchronizations();
		sincronizaciones.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
		verify(cloudinaryService, never()).borrar(any());
		sincronizaciones.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

		verify(cloudinaryService).borrar("id-1");
	}

	@Test
	@DisplayName("No guarda archivos que no son imágenes ni ilegibles")
	void noGuardaArchivosInvalidos() throws IOException {
		MockMultipartFile pdf = archivo("a.pdf", "%PDF".getBytes());
		assertThatThrownBy(() -> servicio.guardarImagen(pdf, 7L, true, 1L)).isInstanceOf(IllegalArgumentException.class);

		MultipartFile ilegible = mock(MultipartFile.class);
		when(ilegible.getBytes()).thenThrow(new IOException("falla"));
		assertThatThrownBy(() -> servicio.guardarImagen(ilegible, 7L, true, 1L))
				.isInstanceOf(UncheckedIOException.class);
		verify(cloudinaryService, never()).subir(any(), any(), any(), any());
	}

	@Test
	@DisplayName("Lista y actualiza las imágenes con la etiqueta del ambiente")
	void listaYActualiza() {
		ImagenGuardada guardada = new ImagenGuardada("id-1", "https://img/1", 7L, true, 1L);
		when(cloudinaryService.listar("pruebas-alojamientos")).thenReturn(List.of(guardada));
		assertThat(servicio.listarImagenes()).containsExactly(guardada);

		Imagen imagen = new Imagen();
		imagen.setRutaArchivo("id-1");
		imagen.setIdAlojamiento(7L);
		imagen.setPrincipal(null);
		imagen.setOrden(4L);
		servicio.actualizarImagen(imagen);
		verify(cloudinaryService).actualizarMetadatos("id-1", new MetadatosImagen("pruebas-alojamientos", 7L, false, 4L));
	}

	@Test
	@DisplayName("Eliminar borra de una vez sin transacción y al confirmarse con transacción; una ruta vacía no hace nada")
	void eliminar() {
		servicio.eliminar(null);
		servicio.eliminar(" ");
		verify(cloudinaryService, never()).borrar(any());

		servicio.eliminar("id-1");
		verify(cloudinaryService).borrar("id-1");

		TransactionSynchronizationManager.initSynchronization();
		servicio.eliminar("id-2");
		verify(cloudinaryService, never()).borrar("id-2");
		TransactionSynchronizationManager.getSynchronizations()
				.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
		verify(cloudinaryService).borrar("id-2");
	}

	/**
	 * Archivo de prueba.
	 *
	 * @param nombre nombre del archivo
	 * @param datos  contenido
	 * @return archivo
	 */
	private static MockMultipartFile archivo(String nombre, byte[] datos) {
		return new MockMultipartFile("archivos", nombre, "image/jpeg", datos);
	}
}
