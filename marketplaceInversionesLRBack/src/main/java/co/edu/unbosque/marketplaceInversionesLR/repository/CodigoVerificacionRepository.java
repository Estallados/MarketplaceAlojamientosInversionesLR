package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import co.edu.unbosque.marketplaceInversionesLR.entity.CodigoVerificacion;

/**
 * Almacén en memoria de los códigos de verificación de correo; no usa la base
 * de datos.
 * <p>
 * Guarda un solo código por usuario, porque cada código nuevo invalida el
 * anterior. Los códigos se pierden al reiniciar la aplicación (el usuario pide
 * otro) y no se comparten entre instancias.
 * </p>
 */
@Repository
public class CodigoVerificacionRepository {

	/** Cada cuánto se limpian los códigos usados o vencidos (5 minutos). */
	private static final long INTERVALO_LIMPIEZA_MS = 300_000;

	/** Código vigente de cada usuario. */
	private final Map<Long, CodigoVerificacion> codigos = new ConcurrentHashMap<>();

	/**
	 * Guarda el código como el único del usuario.
	 *
	 * @param codigo código a guardar
	 * @return el mismo código
	 */
	public CodigoVerificacion save(CodigoVerificacion codigo) {
		codigos.put(codigo.getIdUsuario(), codigo);
		return codigo;
	}

	/**
	 * Código vigente (sin usar) del usuario.
	 *
	 * @param idUsuario usuario dueño del código
	 * @return el código sin usar, si existe
	 */
	public Optional<CodigoVerificacion> findVigente(Long idUsuario) {
		return Optional.ofNullable(codigos.get(idUsuario)).filter(codigo -> !codigo.isUsado());
	}

	/**
	 * Cantidad de códigos guardados.
	 *
	 * @return cantidad de códigos
	 */
	public long count() {
		return codigos.size();
	}

	/** Borra todos los códigos. */
	public void deleteAll() {
		codigos.clear();
	}

	/** Quita de la memoria los códigos ya usados o vencidos. */
	@Scheduled(fixedDelay = INTERVALO_LIMPIEZA_MS)
	public void limpiar() {
		LocalDateTime ahora = LocalDateTime.now();
		codigos.values().removeIf(codigo -> codigo.isUsado() || codigo.getFechaExpiracion().isBefore(ahora));
	}
}
