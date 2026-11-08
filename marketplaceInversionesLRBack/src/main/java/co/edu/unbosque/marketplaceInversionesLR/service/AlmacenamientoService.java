package co.edu.unbosque.marketplaceInversionesLR.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenGuardada;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.MetadatosImagen;

/**
 * Valida las imágenes que suben los usuarios y las guarda en Cloudinary (ver
 * {@link CloudinaryService}).
 * <p>
 * El tipo de imagen se reconoce por los primeros bytes del archivo y no por la
 * extensión ni por el Content-Type que manda el cliente, porque esos se pueden
 * falsificar. Solo se aceptan JPEG, PNG y WEBP de hasta 5 MB. El nombre del
 * archivo es un UUID, así que el nombre original nunca se usa como ruta.
 * </p>
 * <p>
 * Cloudinary no participa de la transacción de la base: si la transacción se
 * revierte, las imágenes que alcanzaron a subirse se borran, y una imagen
 * eliminada solo se borra de Cloudinary cuando la transacción se confirma.
 * </p>
 */
@Service
public class AlmacenamientoService {

	/** Tamaño máximo de una imagen (5 MB). */
	public static final long TAMANIO_MAXIMO = 5L * 1024 * 1024;

	/** Bytes que se leen del inicio del archivo para reconocer el tipo. */
	private static final int BYTES_CABECERA = 12;

	/** Firma de un archivo JPEG. */
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };

	/** Firma de un archivo PNG. */
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' };

	/** Firma del contenedor RIFF, con el que empiezan los WEBP. */
	private static final byte[] RIFF = { 'R', 'I', 'F', 'F' };

	/** Marca WEBP dentro del contenedor RIFF. */
	private static final byte[] WEBP = { 'W', 'E', 'B', 'P' };

	/** Posición donde está la marca WEBP. */
	private static final int INICIO_WEBP = 8;

	/** Cliente de Cloudinary. */
	private final CloudinaryService cloudinaryService;
	/** Carpeta raíz en Cloudinary; permite separar ambientes (dev, prod). */
	private final String carpetaRaiz;

	/**
	 * Crea el servicio.
	 *
	 * @param cloudinaryService cliente de Cloudinary
	 * @param carpetaRaiz carpeta raíz en Cloudinary
	 *                    ({@code CLOUDINARY_CARPETA})
	 */
	public AlmacenamientoService(CloudinaryService cloudinaryService,
			@Value("${app.cloudinary.carpeta:marketplace}") String carpetaRaiz) {
		this.cloudinaryService = cloudinaryService;
		this.carpetaRaiz = carpetaRaiz;
	}

	/**
	 * Revisa que el archivo sea una imagen aceptada: que no esté vacío, que no
	 * pase de 5 MB y que sus primeros bytes sean de un JPEG, PNG o WEBP.
	 *
	 * @param archivo archivo recibido
	 * @return el mensaje para el usuario si no sirve, o {@code null} si es
	 *         válida
	 */
	public String errorImagen(MultipartFile archivo) {
		if (archivo == null || archivo.isEmpty()) {
			return "El archivo está vacío.";
		}
		if (archivo.getSize() > TAMANIO_MAXIMO) {
			return "Cada imagen puede pesar máximo 5 MB.";
		}
		return esImagen(cabecera(archivo)) ? null : "Solo se aceptan imágenes JPG, PNG o WEBP.";
	}

	/**
	 * Guarda una imagen ya validada con {@link #errorImagen(MultipartFile)}.
	 *
	 * @param archivo       Imagen recibida.
	 * @param idAlojamiento Alojamiento dueño de la imagen.
	 * @param principal     Si es la portada del alojamiento.
	 * @param orden         Posición de la imagen dentro del alojamiento.
	 * @return el id en Cloudinary (va en {@code rutaArchivo}) y la URL pública.
	 * @throws co.edu.unbosque.marketplaceInversionesLR.exception.AlmacenamientoNoDisponibleException
	 *         si Cloudinary no la recibe.
	 */
	public ImagenSubida guardarImagen(MultipartFile archivo, Long idAlojamiento, boolean principal, long orden) {
		byte[] datos;
		try {
			datos = archivo.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("No se pudo leer la imagen", e);
		}
		if (!esImagen(datos)) {
			throw new IllegalArgumentException("El archivo no es una imagen aceptada");
		}
		ImagenSubida subida = cloudinaryService.subir(datos, carpetaDe(idAlojamiento), UUID.randomUUID().toString(),
				new MetadatosImagen(etiqueta(), idAlojamiento, principal, orden));
		alTerminarTransaccion(TransactionSynchronization.STATUS_ROLLED_BACK, subida.publicId());
		return subida;
	}

	/**
	 * Imágenes de alojamientos guardadas en Cloudinary para este ambiente.
	 *
	 * @return imágenes con sus datos
	 */
	public List<ImagenGuardada> listarImagenes() {
		return cloudinaryService.listar(etiqueta());
	}

	/**
	 * Actualiza en Cloudinary los datos de una imagen (por ejemplo, si es la
	 * portada). Si falla, solo queda en el log.
	 *
	 * @param imagen imagen con los datos nuevos
	 */
	public void actualizarImagen(Imagen imagen) {
		cloudinaryService.actualizarMetadatos(imagen.getRutaArchivo(), new MetadatosImagen(etiqueta(),
				imagen.getIdAlojamiento(), Boolean.TRUE.equals(imagen.getPrincipal()), imagen.getOrden()));
	}

	/**
	 * Carpeta de Cloudinary de un alojamiento.
	 *
	 * @param idAlojamiento alojamiento
	 * @return ruta de la carpeta
	 */
	private String carpetaDe(Long idAlojamiento) {
		return carpetaRaiz + "/alojamientos/" + idAlojamiento;
	}

	/**
	 * Etiqueta común de las imágenes de este ambiente; depende de la carpeta
	 * raíz para separar ambientes.
	 *
	 * @return etiqueta
	 */
	private String etiqueta() {
		return carpetaRaiz.replace('/', '-') + "-alojamientos";
	}

	/**
	 * Borra una imagen guardada cuando se confirme la transacción. Una ruta
	 * {@code null} (URL externa) no hace nada.
	 *
	 * @param ruta id de la imagen en Cloudinary
	 */
	public void eliminar(String ruta) {
		if (ruta != null && !ruta.isBlank()) {
			alTerminarTransaccion(TransactionSynchronization.STATUS_COMMITTED, ruta);
		}
	}

	/**
	 * Borra la imagen de Cloudinary si la transacción termina con el estado
	 * indicado; sin transacción activa, la borra de una vez.
	 *
	 * @param estado estado de la transacción con el que se borra (confirmada o
	 *               revertida)
	 * @param publicId id de la imagen en Cloudinary
	 */
	private void alTerminarTransaccion(int estado, String publicId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			if (estado == TransactionSynchronization.STATUS_COMMITTED) {
				cloudinaryService.borrar(publicId);
			}
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int estadoFinal) {
				if (estadoFinal == estado) {
					cloudinaryService.borrar(publicId);
				}
			}
		});
	}

	/**
	 * Lee los primeros bytes del archivo.
	 *
	 * @param archivo archivo recibido
	 * @return los primeros bytes, o un arreglo vacío si no se puede leer
	 */
	private static byte[] cabecera(MultipartFile archivo) {
		try (InputStream entrada = archivo.getInputStream()) {
			return entrada.readNBytes(BYTES_CABECERA);
		} catch (IOException e) {
			return new byte[0];
		}
	}

	/**
	 * Indica si los primeros bytes son de un JPEG, PNG o WEBP.
	 *
	 * @param datos primeros bytes del archivo
	 * @return {@code true} si es una imagen aceptada
	 */
	private static boolean esImagen(byte[] datos) {
		return empiezaCon(datos, JPEG, 0) || empiezaCon(datos, PNG, 0)
				|| (empiezaCon(datos, RIFF, 0) && empiezaCon(datos, WEBP, INICIO_WEBP));
	}

	/**
	 * Indica si los datos tienen la firma en la posición indicada.
	 *
	 * @param datos bytes del archivo
	 * @param firma firma a buscar
	 * @param desde posición donde debe empezar la firma
	 * @return {@code true} si coincide
	 */
	private static boolean empiezaCon(byte[] datos, byte[] firma, int desde) {
		return datos.length >= desde + firma.length
				&& Arrays.equals(datos, desde, desde + firma.length, firma, 0, firma.length);
	}
}
