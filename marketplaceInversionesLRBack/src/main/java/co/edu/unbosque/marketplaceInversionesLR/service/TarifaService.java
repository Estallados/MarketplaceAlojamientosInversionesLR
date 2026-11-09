package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConfiguracionTarifaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.ConfiguracionTarifa;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConfiguracionTarifaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Porcentajes de limpieza y de servicio que se aplican al cotizar.
 * <p>
 * Se usa la configuración global vigente (la fija un administrador o un
 * anfitrión); si no hay ninguna configuración guardada, los valores por
 * defecto:
 * {@value #LIMPIEZA_POR_DEFECTO} % de limpieza y
 * {@value #SERVICIO_POR_DEFECTO} % de servicio.
 * </p>
 */
@Service
public class TarifaService {

	/** Porcentaje de limpieza por defecto. */
	public static final int LIMPIEZA_POR_DEFECTO = 5;
	/** Porcentaje de servicio por defecto. */
	public static final int SERVICIO_POR_DEFECTO = 10;

	/** Cien, para validar y calcular porcentajes. */
	private static final BigDecimal CIEN = BigDecimal.valueOf(100);

	/** Repositorio de la configuración de tarifas. */
	private final ConfiguracionTarifaRepository configuracionRepo;

	/** Deja en la auditoría cada cambio de los porcentajes (HU-26). */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el servicio.
	 *
	 * @param configuracionRepo repositorio de la configuración de tarifas
	 * @param auditoriaService  auditoría de los cambios
	 */
	public TarifaService(ConfiguracionTarifaRepository configuracionRepo, AuditoriaService auditoriaService) {
		this.configuracionRepo = configuracionRepo;
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Configuración global vigente, o la de por defecto si no hay ninguna
	 * guardada.
	 *
	 * @return porcentajes globales vigentes
	 */
	public ConfiguracionTarifaDTO vigente() {
		Optional<ConfiguracionTarifa> guardada = configuracionRepo.findTopByOrderByFechaDescIdConfiguracionDesc();
		ConfiguracionTarifaDTO dto = new ConfiguracionTarifaDTO();
		if (guardada.isPresent()) {
			dto.setIdConfiguracion(guardada.get().getIdConfiguracion());
			dto.setPorcentajeLimpieza(guardada.get().getPorcentajeLimpieza());
			dto.setPorcentajeServicio(guardada.get().getPorcentajeServicio());
			dto.setIdUsuario(guardada.get().getIdUsuario());
			dto.setFecha(guardada.get().getFecha());
			dto.setPorDefecto(false);
		} else {
			dto.setPorcentajeLimpieza(BigDecimal.valueOf(LIMPIEZA_POR_DEFECTO));
			dto.setPorcentajeServicio(BigDecimal.valueOf(SERVICIO_POR_DEFECTO));
			dto.setPorDefecto(true);
		}
		return dto;
	}

	/**
	 * Porcentaje de limpieza que se cobra en un alojamiento: el propio del
	 * alojamiento si lo tiene, o el global vigente.
	 *
	 * @param alojamiento alojamiento a cotizar
	 * @return porcentaje de limpieza
	 */
	public BigDecimal porcentajeLimpieza(Alojamiento alojamiento) {
		return alojamiento.getPorcentajeLimpieza() != null ? alojamiento.getPorcentajeLimpieza()
				: vigente().getPorcentajeLimpieza();
	}

	/**
	 * Porcentaje de servicio que se cobra en un alojamiento: el propio del
	 * alojamiento si lo tiene, o el global vigente.
	 *
	 * @param alojamiento alojamiento a cotizar
	 * @return porcentaje de servicio
	 */
	public BigDecimal porcentajeServicio(Alojamiento alojamiento) {
		return alojamiento.getPorcentajeServicio() != null ? alojamiento.getPorcentajeServicio()
				: vigente().getPorcentajeServicio();
	}

	/**
	 * Cambia los porcentajes globales. Inserta una configuración nueva, así se
	 * conserva el historial; los cambios aplican a las cotizaciones
	 * siguientes.
	 *
	 * @param datos   porcentajes nuevos
	 * @param idActor administrador o anfitrión que hace el cambio
	 * @return la configuración nueva; o DATOS_INVALIDOS si algún porcentaje
	 *         falta o no está entre 0 y 100
	 */
	@Transactional
	public Resultado<ConfiguracionTarifaDTO> actualizar(ConfiguracionTarifaDTO datos, Long idActor) {
		if (datos == null || !porcentajeValido(datos.getPorcentajeLimpieza())
				|| !porcentajeValido(datos.getPorcentajeServicio())) {
			String mensaje = "Los porcentajes de limpieza y servicio son obligatorios y deben estar entre 0 y 100.";
			auditoriaService.registrar(idActor, AuditoriaService.TARIFA_ACTUALIZADA,
					AuditoriaService.ENTIDAD_CONFIGURACION, null, ResultadoAuditoria.RECHAZADO, mensaje);
			return Resultado.error(Resultado.DATOS_INVALIDOS, mensaje);
		}
		ConfiguracionTarifa configuracion = new ConfiguracionTarifa();
		configuracion.setPorcentajeLimpieza(datos.getPorcentajeLimpieza());
		configuracion.setPorcentajeServicio(datos.getPorcentajeServicio());
		configuracion.setIdUsuario(idActor);
		configuracion.setFecha(LocalDateTime.now());
		configuracionRepo.save(configuracion);
		ConfiguracionTarifaDTO nueva = vigente();
		auditoriaService.registrar(idActor, AuditoriaService.TARIFA_ACTUALIZADA, AuditoriaService.ENTIDAD_CONFIGURACION,
				nueva.getIdConfiguracion(), ResultadoAuditoria.EXITOSO, "Limpieza " + datos.getPorcentajeLimpieza()
						+ "%, servicio " + datos.getPorcentajeServicio() + "%");
		return Resultado.exito(nueva);
	}

	/**
	 * Un porcentaje es válido si está entre 0 y 100.
	 *
	 * @param porcentaje porcentaje a revisar
	 * @return {@code true} si es válido
	 */
	public static boolean porcentajeValido(BigDecimal porcentaje) {
		return porcentaje != null && porcentaje.signum() >= 0 && porcentaje.compareTo(CIEN) <= 0;
	}
}
