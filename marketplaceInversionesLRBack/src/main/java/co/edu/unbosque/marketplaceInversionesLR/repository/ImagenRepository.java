package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.exception.AlmacenamientoNoDisponibleException;
import co.edu.unbosque.marketplaceInversionesLR.service.AlmacenamientoService;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenGuardada;

/**
 * Almacén de las imágenes de los alojamientos, sin base de datos.
 * <p>
 * Cloudinary es la fuente de los datos: cada archivo guarda su alojamiento, si
 * es la portada y su orden. Al arrancar se cargan en memoria y las lecturas
 * salen de ahí, así un listado no hace una llamada a Cloudinary por cada
 * alojamiento. Solo sirve para una instancia de la aplicación: otra no vería
 * los cambios hasta reiniciar.
 * </p>
 */
@Repository
public class ImagenRepository {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(ImagenRepository.class);

	/**
	 * Cada cuánto se reintenta la carga si Cloudinary falló al arrancar (1
	 * minuto).
	 */
	private static final long REINTENTO_CARGA_MS = 60_000;
	/** Bits que se descartan del id: deja 52, que JavaScript representa sin perder precisión. */
	private static final int BITS_DESCARTADOS_ID = 12;

	/** Servicio que habla con Cloudinary. */
	private final AlmacenamientoService almacenamientoService;

	/** Imágenes en memoria, agrupadas por alojamiento y luego por id. */
	private final Map<Long, Map<Long, Imagen>> porAlojamiento = new ConcurrentHashMap<>();

	/**
	 * Último número de orden entregado; garantiza que cada imagen nueva quede
	 * después.
	 */
	private final AtomicLong ultimoOrden = new AtomicLong();

	/** Indica si ya se cargaron las imágenes desde Cloudinary. */
	private volatile boolean cargado;

	/**
	 * Crea el almacén.
	 *
	 * @param almacenamientoService servicio de almacenamiento en Cloudinary
	 */
	public ImagenRepository(AlmacenamientoService almacenamientoService) {
		this.almacenamientoService = almacenamientoService;
	}

	/**
	 * Carga las imágenes cuando la aplicación termina de arrancar.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void alArrancar() {
		cargar();
	}

	/**
	 * Reintenta la carga cada minuto mientras no se haya podido hacer.
	 */
	@Scheduled(initialDelay = REINTENTO_CARGA_MS, fixedDelay = REINTENTO_CARGA_MS)
	public void reintentarCarga() {
		if (!cargado) {
			cargar();
		}
	}

	/**
	 * Lee de Cloudinary todas las imágenes y las pone en memoria. Si
	 * Cloudinary no responde, lo deja en el log y se reintenta más tarde.
	 */
	private void cargar() {
		try {
			List<ImagenGuardada> guardadas = almacenamientoService.listarImagenes();
			for (ImagenGuardada guardada : guardadas) {
				Imagen imagen = new Imagen();
				imagen.setIdImagen(idDe(guardada.publicId()));
				imagen.setIdAlojamiento(guardada.idAlojamiento());
				imagen.setRutaArchivo(guardada.publicId());
				imagen.setUrl(guardada.url());
				imagen.setPrincipal(guardada.principal());
				imagen.setOrden(guardada.orden());
				// Lo que ya cambió en esta ejecución es más reciente que lo leído
				imagenes(imagen.getIdAlojamiento()).putIfAbsent(imagen.getIdImagen(), imagen);
			}
			cargado = true;
			LOG.info("Imágenes cargadas desde Cloudinary: {}", guardadas.size());
		} catch (AlmacenamientoNoDisponibleException e) {
			LOG.error("No se pudieron cargar las imágenes desde Cloudinary; se reintentará", e);
		}
	}

	/**
	 * Imágenes de un alojamiento, en orden de carga.
	 *
	 * @param idAlojamiento Alojamiento dueño de las imágenes.
	 * @return Copias de las imágenes; cambiarlas no tiene efecto hasta {@link #save}.
	 */
	public List<Imagen> findByIdAlojamiento(Long idAlojamiento) {
		return imagenes(idAlojamiento).values().stream().sorted(Comparator.comparingLong(Imagen::getOrden))
				.map(Imagen::new).toList();
	}

	/**
	 * Busca una imagen verificando que sea del alojamiento indicado, para no
	 * tocar imágenes de otro.
	 *
	 * @param idImagen imagen a buscar
	 * @param idAlojamiento alojamiento al que debe pertenecer
	 * @return copia de la imagen, si existe
	 */
	public Optional<Imagen> findByIdImagenAndIdAlojamiento(Long idImagen, Long idAlojamiento) {
		return Optional.ofNullable(imagenes(idAlojamiento).get(idImagen)).map(Imagen::new);
	}

	/**
	 * Posición para la siguiente imagen que se suba. Usa la hora actual y
	 * siempre es mayor que la anterior.
	 *
	 * @return número de orden
	 */
	public long siguienteOrden() {
		return ultimoOrden.updateAndGet(ultimo -> Math.max(ultimo + 1, System.currentTimeMillis()));
	}

	/**
	 * Guarda una imagen. Una nueva ya subió sus datos a Cloudinary con el archivo;
	 * si cambia la portada de una existente, Cloudinary se actualiza al confirmarse
	 * la transacción. Si la transacción falla, la memoria vuelve a como estaba.
	 *
	 * @param imagen Imagen a guardar.
	 * @return La misma imagen, con su id asignado.
	 */
	public Imagen save(Imagen imagen) {
		if (imagen.getIdImagen() == null) {
			imagen.setIdImagen(idDe(imagen.getRutaArchivo()));
		}
		Map<Long, Imagen> imagenes = imagenes(imagen.getIdAlojamiento());
		Long id = imagen.getIdImagen();
		Imagen anterior = imagenes.put(id, new Imagen(imagen));

		boolean cambioPortada = anterior != null
				&& Boolean.TRUE.equals(anterior.getPrincipal()) != Boolean.TRUE.equals(imagen.getPrincipal());
		Imagen guardada = new Imagen(imagen);
		alTerminar(() -> {
			if (cambioPortada) {
				almacenamientoService.actualizarImagen(guardada);
			}
		}, () -> {
			if (anterior == null) {
				imagenes.remove(id);
			} else {
				imagenes.put(id, anterior);
			}
		});
		return imagen;
	}

	/**
	 * Quita una imagen de la memoria; el archivo lo borra
	 * {@link AlmacenamientoService#eliminar(String)}.
	 *
	 * @param imagen Imagen a quitar.
	 */
	public void delete(Imagen imagen) {
		Map<Long, Imagen> imagenes = imagenes(imagen.getIdAlojamiento());
		Imagen anterior = imagenes.remove(imagen.getIdImagen());
		alTerminar(() -> {
		}, () -> {
			if (anterior != null) {
				imagenes.put(anterior.getIdImagen(), anterior);
			}
		});
	}

	/**
	 * Mapa de imágenes de un alojamiento; lo crea si no existe.
	 *
	 * @param idAlojamiento alojamiento
	 * @return imágenes del alojamiento por id
	 */
	private Map<Long, Imagen> imagenes(Long idAlojamiento) {
		return porAlojamiento.computeIfAbsent(idAlojamiento, id -> new ConcurrentHashMap<>());
	}

	/**
	 * Ejecuta una acción cuando la transacción actual se confirma y otra si se
	 * revierte. Si no hay transacción, confirma de una vez.
	 *
	 * @param alConfirmar acción si la transacción se confirma
	 * @param alRevertir acción si la transacción se revierte
	 */
	private static void alTerminar(Runnable alConfirmar, Runnable alRevertir) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			alConfirmar.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int estado) {
				if (estado == STATUS_COMMITTED) {
					alConfirmar.run();
				} else {
					alRevertir.run();
				}
			}
		});
	}

	/**
	 * Calcula un id numérico estable a partir del public_id de Cloudinary: el
	 * mismo public_id da el mismo id después de reiniciar. Se descartan bits
	 * para que JavaScript lo pueda representar sin perder precisión.
	 *
	 * @param publicId id del archivo en Cloudinary
	 * @return id numérico de la imagen
	 */
	static long idDe(String publicId) {
		return UUID.nameUUIDFromBytes(publicId.getBytes(StandardCharsets.UTF_8))
				.getMostSignificantBits() >>> BITS_DESCARTADOS_ID;
	}
}
