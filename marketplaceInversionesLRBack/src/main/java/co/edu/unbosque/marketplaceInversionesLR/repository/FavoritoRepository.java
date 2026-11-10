package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.Favorito;

/**
 * Acceso a los favoritos de los usuarios.
 */
public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

	/**
	 * Favoritos de un usuario, paginados.
	 *
	 * @param idUsuario usuario dueño de los favoritos
	 * @param pageable  página y orden
	 * @return página de favoritos
	 */
	Page<Favorito> findByIdUsuario(Long idUsuario, Pageable pageable);

	/**
	 * Busca si un usuario ya tiene un alojamiento en favoritos.
	 *
	 * @param idUsuario     usuario
	 * @param idAlojamiento alojamiento
	 * @return el favorito, si existe
	 */
	Optional<Favorito> findByIdUsuarioAndIdAlojamiento(Long idUsuario, Long idAlojamiento);
}
