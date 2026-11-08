package co.edu.unbosque.marketplaceInversionesLR.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unbosque.marketplaceInversionesLR.entity.ConfiguracionTarifa;

/**
 * Acceso a la configuración de tarifas. Cada cambio queda como una fila nueva,
 * así que la configuración vigente es la más reciente.
 */
public interface ConfiguracionTarifaRepository extends JpaRepository<ConfiguracionTarifa, Long> {

	/**
	 * Configuración vigente: la de fecha más reciente (y, si hay empate, la de
	 * id mayor).
	 *
	 * @return la configuración vigente, o vacío si nunca se ha guardado una
	 */
	public Optional<ConfiguracionTarifa> findTopByOrderByFechaDescIdConfiguracionDesc();
}
