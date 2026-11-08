package co.edu.unbosque.marketplaceInversionesLR.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;

/**
 * Acceso a los alojamientos. Las consultas del catálogo, la búsqueda y los
 * listados del anfitrión se agregan con sus historias de usuario.
 */
public interface AlojamientoRepository extends JpaRepository<Alojamiento, Long> {
}
