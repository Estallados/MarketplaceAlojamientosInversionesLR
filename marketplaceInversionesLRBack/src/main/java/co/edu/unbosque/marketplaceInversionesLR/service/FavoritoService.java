package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.FavoritoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Favorito;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.FavoritoRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Alojamientos favoritos de cada usuario (HU-29). Un alojamiento aparece una
 * sola vez en los favoritos de cada usuario, y solo se pueden guardar
 * alojamientos publicados.
 */
@Service
public class FavoritoService {

	/** Mensaje cuando el alojamiento no existe o no está publicado. */
	public static final String NO_EXISTE = "El alojamiento no existe.";

	/** Mensaje cuando el alojamiento no está en los favoritos del usuario. */
	public static final String NO_ES_FAVORITO = "El alojamiento no está en tus favoritos.";

	/** Repositorio de favoritos. */
	private final FavoritoRepository favoritoRepo;

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Para armar los datos del alojamiento de cada favorito. */
	private final PublicacionService publicacionService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/**
	 * Crea el servicio.
	 *
	 * @param favoritoRepo       repositorio de favoritos
	 * @param alojamientoRepo    repositorio de alojamientos
	 * @param publicacionService servicio de publicaciones
	 * @param modelMapper        copiador de entidades a DTO
	 */
	public FavoritoService(FavoritoRepository favoritoRepo, AlojamientoRepository alojamientoRepo,
			PublicacionService publicacionService, ModelMapper modelMapper) {
		this.favoritoRepo = favoritoRepo;
		this.alojamientoRepo = alojamientoRepo;
		this.publicacionService = publicacionService;
		this.modelMapper = modelMapper;
	}

	/**
	 * Indica si el alojamiento ya está en los favoritos del usuario.
	 *
	 * @param idUsuario     usuario
	 * @param idAlojamiento alojamiento
	 * @return {@code true} si ya es favorito
	 */
	public boolean esFavorito(Long idUsuario, Long idAlojamiento) {
		return favoritoRepo.findByIdUsuarioAndIdAlojamiento(idUsuario, idAlojamiento).isPresent();
	}

	/**
	 * Agrega un alojamiento publicado (ACTIVA) a los favoritos. Si ya estaba,
	 * devuelve el favorito que ya existía (CA-173).
	 *
	 * @param idUsuario     usuario
	 * @param idAlojamiento alojamiento a guardar
	 * @return el favorito; o NO_ENCONTRADO si el alojamiento no existe o no está
	 *         publicado
	 */
	@Transactional
	public Resultado<FavoritoDTO> agregar(Long idUsuario, Long idAlojamiento) {
		if (alojamientoRepo.findById(idAlojamiento).filter(a -> a.getEstado() == EstadoAlojamiento.ACTIVA).isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Optional<Favorito> existente = favoritoRepo.findByIdUsuarioAndIdAlojamiento(idUsuario, idAlojamiento);
		if (existente.isPresent()) {
			return Resultado.exito(convertirDTO(existente.get()));
		}
		Favorito favorito = new Favorito();
		favorito.setIdUsuario(idUsuario);
		favorito.setIdAlojamiento(idAlojamiento);
		favorito.setFechaAgregado(LocalDateTime.now());
		try {
			return Resultado.exito(convertirDTO(favoritoRepo.saveAndFlush(favorito)));
		} catch (DataIntegrityViolationException e) {
			// Dos solicitudes iguales al mismo tiempo: el índice único deja solo una
			return favoritoRepo.findByIdUsuarioAndIdAlojamiento(idUsuario, idAlojamiento)
					.map(f -> Resultado.exito(convertirDTO(f)))
					.orElseGet(() -> Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE));
		}
	}

	/**
	 * Quita un alojamiento de los favoritos del usuario (CA-171).
	 *
	 * @param idUsuario     usuario
	 * @param idAlojamiento alojamiento a quitar
	 * @return éxito; o NO_ENCONTRADO si no estaba en sus favoritos
	 */
	@Transactional
	public Resultado<Void> eliminar(Long idUsuario, Long idAlojamiento) {
		Optional<Favorito> favorito = favoritoRepo.findByIdUsuarioAndIdAlojamiento(idUsuario, idAlojamiento);
		if (favorito.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_ES_FAVORITO);
		}
		favoritoRepo.delete(favorito.get());
		return Resultado.exito(null);
	}

	/**
	 * Favoritos del usuario autenticado, solo los suyos. Si no tiene, la página
	 * viene vacía (CA-170).
	 *
	 * @param idUsuario usuario
	 * @param pagina    página y orden
	 * @return página de favoritos con los datos de cada alojamiento
	 */
	public PaginaDTO<FavoritoDTO> listar(Long idUsuario, Pageable pagina) {
		return PaginaDTO.de(favoritoRepo.findByIdUsuario(idUsuario, pagina), this::convertirDTO);
	}

	/**
	 * Convierte un favorito a DTO e incluye los datos del alojamiento.
	 *
	 * @param favorito favorito a convertir
	 * @return favorito con los datos del alojamiento
	 */
	private FavoritoDTO convertirDTO(Favorito favorito) {
		FavoritoDTO dto = modelMapper.map(favorito, FavoritoDTO.class);
		alojamientoRepo.findById(favorito.getIdAlojamiento())
				.ifPresent(a -> dto.setAlojamiento(publicacionService.convertirDTO(a)));
		return dto;
	}
}
