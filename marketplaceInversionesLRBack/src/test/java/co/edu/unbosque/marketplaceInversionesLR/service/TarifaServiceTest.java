package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConfiguracionTarifaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.ConfiguracionTarifa;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConfiguracionTarifaRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de los porcentajes de limpieza y servicio. La base de datos
 * se simula.
 */
@ExtendWith(MockitoExtension.class)
class TarifaServiceTest {

	@Mock
	private ConfiguracionTarifaRepository configuracionRepo;

	@Mock
	private AuditoriaService auditoriaService;

	private TarifaService servicio;

	@BeforeEach
	void preparar() {
		servicio = new TarifaService(configuracionRepo, auditoriaService);
	}

	@Test
	@DisplayName("CA-084 Sin configuración guardada se usan los porcentajes por defecto (5 % y 10 %)")
	void porDefecto_CA084() {
		when(configuracionRepo.findTopByOrderByFechaDescIdConfiguracionDesc()).thenReturn(Optional.empty());

		ConfiguracionTarifaDTO vigente = servicio.vigente();

		assertThat(vigente.getPorDefecto()).isTrue();
		assertThat(vigente.getPorcentajeLimpieza()).isEqualByComparingTo("5");
		assertThat(vigente.getPorcentajeServicio()).isEqualByComparingTo("10");
		assertThat(vigente.getIdConfiguracion()).isNull();
		assertThat(vigente.getIdUsuario()).isNull();
		assertThat(vigente.getFecha()).isNull();
	}

	@Test
	@DisplayName("CA-084 Con configuración guardada se usa la vigente")
	void configuracionVigente_CA084() {
		LocalDateTime fecha = LocalDateTime.now();
		when(configuracionRepo.findTopByOrderByFechaDescIdConfiguracionDesc())
				.thenReturn(Optional.of(guardada(3L, "7.5", "12", 1L, fecha)));

		ConfiguracionTarifaDTO vigente = servicio.vigente();

		assertThat(vigente.getPorDefecto()).isFalse();
		assertThat(vigente.getIdConfiguracion()).isEqualTo(3L);
		assertThat(vigente.getPorcentajeLimpieza()).isEqualByComparingTo("7.5");
		assertThat(vigente.getPorcentajeServicio()).isEqualByComparingTo("12");
		assertThat(vigente.getIdUsuario()).isEqualTo(1L);
		assertThat(vigente.getFecha()).isEqualTo(fecha);
	}

	@Test
	@DisplayName("Actualizar guarda una configuración nueva con el administrador y la fecha, y devuelve la vigente")
	void actualizar() {
		when(configuracionRepo.findTopByOrderByFechaDescIdConfiguracionDesc())
				.thenReturn(Optional.of(guardada(4L, "8", "15", 1L, LocalDateTime.now())));

		Resultado<ConfiguracionTarifaDTO> resultado = servicio.actualizar(datos("8", "15"), 1L);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getPorcentajeLimpieza()).isEqualByComparingTo("8");
		ArgumentCaptor<ConfiguracionTarifa> guardada = ArgumentCaptor.forClass(ConfiguracionTarifa.class);
		verify(configuracionRepo).save(guardada.capture());
		assertThat(guardada.getValue().getPorcentajeLimpieza()).isEqualByComparingTo("8");
		assertThat(guardada.getValue().getPorcentajeServicio()).isEqualByComparingTo("15");
		assertThat(guardada.getValue().getIdUsuario()).isEqualTo(1L);
		assertThat(guardada.getValue().getFecha()).isNotNull();
		// HU-26: el cambio queda en la auditoría con el actor y la configuración nueva
		verify(auditoriaService).registrar(eq(1L), eq(AuditoriaService.TARIFA_ACTUALIZADA),
				eq(AuditoriaService.ENTIDAD_CONFIGURACION), eq(4L), eq(ResultadoAuditoria.EXITOSO), anyString());
	}

	@Test
	@DisplayName("CA-136 Un cambio de porcentajes rechazado también queda en la auditoría")
	void rechazoAuditado_CA136() {
		servicio.actualizar(datos("-1", "10"), 3L);

		verify(auditoriaService).registrar(eq(3L), eq(AuditoriaService.TARIFA_ACTUALIZADA),
				eq(AuditoriaService.ENTIDAD_CONFIGURACION), isNull(), eq(ResultadoAuditoria.RECHAZADO), anyString());
	}

	@Test
	@DisplayName("Rechaza porcentajes faltantes, negativos o mayores que 100 sin guardar nada")
	void rechazaPorcentajesInvalidos() {
		assertThat(servicio.actualizar(null, 1L).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.actualizar(datos(null, "10"), 1L).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.actualizar(datos("5", null), 1L).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.actualizar(datos("-1", "10"), 1L).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		Resultado<ConfiguracionTarifaDTO> resultado = servicio.actualizar(datos("5", "100.01"), 1L);
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje())
				.isEqualTo("Los porcentajes de limpieza y servicio son obligatorios y deben estar entre 0 y 100.");
		verify(configuracionRepo, never()).save(any());
	}

	@Test
	@DisplayName("Un porcentaje es válido entre 0 y 100, incluidos los extremos")
	void porcentajeValido() {
		assertThat(TarifaService.porcentajeValido(BigDecimal.ZERO)).isTrue();
		assertThat(TarifaService.porcentajeValido(new BigDecimal("100"))).isTrue();
		assertThat(TarifaService.porcentajeValido(new BigDecimal("12.5"))).isTrue();
		assertThat(TarifaService.porcentajeValido(new BigDecimal("-0.01"))).isFalse();
		assertThat(TarifaService.porcentajeValido(new BigDecimal("100.01"))).isFalse();
		assertThat(TarifaService.porcentajeValido(null)).isFalse();
	}

	/**
	 * Porcentajes de entrada.
	 *
	 * @param limpieza porcentaje de limpieza; puede ser null
	 * @param servicio porcentaje de servicio; puede ser null
	 * @return DTO de entrada
	 */
	private static ConfiguracionTarifaDTO datos(String limpieza, String servicio) {
		ConfiguracionTarifaDTO dto = new ConfiguracionTarifaDTO();
		dto.setPorcentajeLimpieza(limpieza == null ? null : new BigDecimal(limpieza));
		dto.setPorcentajeServicio(servicio == null ? null : new BigDecimal(servicio));
		return dto;
	}

	/**
	 * Configuración guardada de prueba.
	 *
	 * @return configuración
	 */
	private static ConfiguracionTarifa guardada(Long id, String limpieza, String servicio, Long idUsuario,
			LocalDateTime fecha) {
		ConfiguracionTarifa configuracion = new ConfiguracionTarifa();
		configuracion.setIdConfiguracion(id);
		configuracion.setPorcentajeLimpieza(new BigDecimal(limpieza));
		configuracion.setPorcentajeServicio(new BigDecimal(servicio));
		configuracion.setIdUsuario(idUsuario);
		configuracion.setFecha(fecha);
		return configuracion;
	}
}
