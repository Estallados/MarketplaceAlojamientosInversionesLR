package co.edu.unbosque.marketplaceInversionesLR.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.RegistroExportacion;

/**
 * Acceso al historial de exportaciones de reportes (HU-05). Solo usa las
 * operaciones básicas de {@link JpaRepository}.
 */
public interface RegistroExportacionRepository extends JpaRepository<RegistroExportacion, Long> {
}
