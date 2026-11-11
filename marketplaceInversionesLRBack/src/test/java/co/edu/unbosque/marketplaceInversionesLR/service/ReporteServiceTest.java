package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.DashboardDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.DemandaCiudadDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoPago;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoReserva;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PagoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;

/**
 * Pruebas unitarias del dashboard (HU-04, CA-022) con los repositorios
 * simulados.
 */
@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private PagoRepository pagoRepo;

	@Mock
	private CancelacionRepository cancelacionRepo;

	private ReporteService servicio;

	@BeforeEach
	void preparar() {
		servicio = new ReporteService(usuarioRepo, alojamientoRepo, reservaRepo, pagoRepo, cancelacionRepo);
	}

	/** Deja los repositorios con datos registrados de ejemplo. */
	private void conDatos() {
		when(usuarioRepo.count()).thenReturn(7L);
		when(alojamientoRepo.contarPorEstado()).thenReturn(List.of(
				new Object[] { EstadoAlojamiento.ACTIVA, 3L }, new Object[] { EstadoAlojamiento.BORRADOR, 1L }));
		when(reservaRepo.contarPorEstado()).thenReturn(List.of(
				new Object[] { EstadoReserva.CONFIRMADA, 4L }, new Object[] { EstadoReserva.CANCELADA, 2L }));
		when(reservaRepo.countByEstadoAndFechaSalidaBefore(EstadoReserva.CONFIRMADA, LocalDate.now())).thenReturn(1L);
		when(pagoRepo.sumarValores(List.of(EstadoPago.APROBADO, EstadoPago.REEMBOLSADO)))
				.thenReturn(new BigDecimal("1000000"));
		when(cancelacionRepo.sumarReembolsos()).thenReturn(new BigDecimal("250000"));
		when(reservaRepo.contarCiudadesTop(any(Pageable.class))).thenReturn(
				List.of(new DemandaCiudadDTO("Bogotá", 4L), new DemandaCiudadDTO("Medellín", 2L)));
	}

	@Test
	@DisplayName("CA-022 El dashboard trae usuarios, publicaciones activas, reservas por estado, ingresos y ciudades")
	void indicadores_CA022() {
		conDatos();

		DashboardDTO dashboard = servicio.dashboard(5);

		assertThat(dashboard.totalUsuarios()).isEqualTo(7);
		assertThat(dashboard.alojamientos().total()).isEqualTo(4);
		assertThat(dashboard.alojamientos().porEstado()).containsEntry("ACTIVA", 3L).containsEntry("BORRADOR", 1L);
		assertThat(dashboard.alojamientos().confirmadasFinalizadas()).isNull();
		assertThat(dashboard.reservas().total()).isEqualTo(6);
		assertThat(dashboard.reservas().porEstado()).containsEntry("CONFIRMADA", 4L).containsEntry("CANCELADA", 2L);
		assertThat(dashboard.reservas().confirmadasFinalizadas()).isEqualTo(1L);
		assertThat(dashboard.ingresosEstimados()).isEqualByComparingTo("750000");
		assertThat(dashboard.ciudadesTop()).extracting(DemandaCiudadDTO::ciudad).containsExactly("Bogotá",
				"Medellín");
	}

	@Test
	@DisplayName("CA-022 Los estados sin registros aparecen con 0, en el orden del enum")
	void estadosEnCero_CA022() {
		conDatos();

		DashboardDTO dashboard = servicio.dashboard(5);

		assertThat(dashboard.alojamientos().porEstado()).containsOnlyKeys("BORRADOR", "PENDIENTE_REVISION", "ACTIVA",
				"PAUSADA", "BLOQUEADA").containsEntry("PAUSADA", 0L);
		assertThat(dashboard.reservas().porEstado().keySet()).containsExactly("PENDIENTE_PAGO", "CONFIRMADA",
				"EN_REVISION", "CANCELADA");
		assertThat(dashboard.reservas().porEstado()).containsEntry("EN_REVISION", 0L);
	}

	@Test
	@DisplayName("CA-022 La cantidad de ciudades del ranking queda entre 1 y el máximo")
	void limiteDeCiudades_CA022() {
		conDatos();
		ArgumentCaptor<Pageable> limite = ArgumentCaptor.forClass(Pageable.class);

		servicio.dashboard(0);
		servicio.dashboard(500);

		verify(reservaRepo, times(2)).contarCiudadesTop(limite.capture());
		assertThat(limite.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1,
				ReporteService.CIUDADES_MAXIMAS);
	}
}
