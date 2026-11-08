package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.exception.AlmacenamientoNoDisponibleException;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenGuardada;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.MetadatosImagen;

/**
 * Pruebas unitarias del cliente de Cloudinary sin llamar a la API real.
 */
class CloudinaryServiceTest {

	/** Servicio sin cuenta configurada. */
	private final CloudinaryService sinCuenta = new CloudinaryService("");

	@Test
	@DisplayName("Sin CLOUDINARY_URL arranca, pero subir responde que no está configurado")
	void sinConfigurar() {
		MetadatosImagen metadatos = new MetadatosImagen("pruebas-alojamientos", 1L, true, 1L);

		assertThat(sinCuenta.estaConfigurado()).isFalse();
		assertThat(new CloudinaryService(null).estaConfigurado()).isFalse();
		assertThatThrownBy(() -> sinCuenta.subir(new byte[] { 1 }, "carpeta", "nombre", metadatos))
				.isInstanceOf(AlmacenamientoNoDisponibleException.class)
				.hasMessage("El almacenamiento de imágenes no está configurado.");
		assertThat(sinCuenta.listar("pruebas-alojamientos")).isEmpty();
		assertThat(sinCuenta.actualizarMetadatos("id", metadatos)).isFalse();
		sinCuenta.borrar("id");
	}

	@Test
	@DisplayName("Con una URL válida queda configurado; con una inválida no arranca")
	void configuracion() {
		assertThat(new CloudinaryService("cloudinary://clave:secreto@nube").estaConfigurado()).isTrue();
		assertThatThrownBy(() -> new CloudinaryService("no-es-una-url"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("CLOUDINARY_URL no es válida");
	}

	@Test
	@DisplayName("Arma el contexto de la imagen con alojamiento, portada y orden")
	void contexto() {
		assertThat(new MetadatosImagen("e", 7L, true, 3L).contexto()).isEqualTo("idAlojamiento=7|principal=true|orden=3");
	}

	@Test
	@DisplayName("Lee las imágenes de la respuesta de la API, con la URL de entrega optimizada")
	void leeRecursos() {
		Map<String, Object> conCustom = Map.of("public_id", "a", "secure_url", "https://res/upload/v1/a.jpg",
				"context", Map.of("custom", Map.of("idAlojamiento", "7", "principal", "true", "orden", "2")));
		Map<String, Object> plano = Map.of("public_id", "b", "secure_url", "https://res/upload/v1/b.jpg", "context",
				Map.of("idAlojamiento", "8", "principal", "false", "orden", "5"));
		Map<String, Object> sinDatos = Map.of("public_id", "c", "secure_url", "https://res/upload/v1/c.jpg");

		List<ImagenGuardada> imagenes = CloudinaryService
				.leerRecursos(Map.of("resources", List.of(conCustom, plano, sinDatos, "basura")));

		assertThat(imagenes).containsExactly(
				new ImagenGuardada("a", "https://res/upload/f_auto,q_auto/v1/a.jpg", 7L, true, 2L),
				new ImagenGuardada("b", "https://res/upload/f_auto,q_auto/v1/b.jpg", 8L, false, 5L));
		assertThat(CloudinaryService.leerRecursos(Map.of())).isEmpty();
	}
}
