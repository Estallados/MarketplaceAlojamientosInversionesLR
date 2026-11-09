package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import co.edu.unbosque.marketplaceInversionesLR.dto.CotizacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Cotizacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de la cotización básica que usa HU-22, con los
 * repositorios, el calendario y las tarifas simulados.
 */
@ExtendWith(MockitoExtension.class)
class CotizacionServiceTest {

	/** Alojamiento de prueba. */
	private static final Long ID_ALOJAMIENTO = 10L;

	/** Huésped que cotiza. */
	private static final Long ID_HUESPED = 3L;

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private CotizacionRepository cotizacionRepo;

	@Mock
	private CalendarioService calendarioService;

	@Mock
	private TarifaService tarifaService;

	private CotizacionService servicio;

	@BeforeEach
	void preparar() {
		servicio = new CotizacionService(alojamientoRepo, cotizacionRepo, calendarioService, tarifaService,
				new ModelMapper());
	}

	@Test
	@DisplayName("CA-086 El total es el precio base más limpieza y servicio, redondeado a 2 decimales")
	void calcula_CA086() {
		CotizacionService.Desglose desglose = CotizacionService.calcular(new BigDecimal("200000"), 3,
				new BigDecimal("5"), new BigDecimal("10"));

		assertThat(desglose.precioBase()).isEqualByComparingTo("600000");
		assertThat(desglose.valorLimpieza()).isEqualByComparingTo("30000");
		assertThat(desglose.valorServicio()).isEqualByComparingTo("60000");
		assertThat(desglose.total()).isEqualByComparingTo("690000");

		CotizacionService.Desglose decimales = CotizacionService.calcular(new BigDecimal("99999.99"), 1,
				new BigDecimal("7.5"), new BigDecimal("12.25"));
		assertThat(decimales.valorLimpieza()).isEqualByComparingTo("7500.00");
		assertThat(decimales.valorServicio()).isEqualByComparingTo("12250.00");
		assertThat(decimales.total()).isEqualByComparingTo(
				decimales.precioBase().add(decimales.valorLimpieza()).add(decimales.valorServicio()));
	}

	@Test
	@DisplayName("CA-070 La cotización guarda cada componente del precio y vence en 30 minutos")
	void cotiza_CA070() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(tarifaService.porcentajeLimpieza(any())).thenReturn(new BigDecimal("5"));
		when(tarifaService.porcentajeServicio(any())).thenReturn(new BigDecimal("10"));
		when(cotizacionRepo.save(any())).thenAnswer(invocacion -> {
			Cotizacion c = invocacion.getArgument(0);
			c.setIdCotizacion(42L);
			return c;
		});
		LocalDateTime antes = LocalDateTime.now();

		Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitud(BASE, BASE.plusDays(3), 2), ID_HUESPED);

		assertThat(resultado.esExito()).isTrue();
		CotizacionDTO cotizacion = resultado.getDato();
		assertThat(cotizacion.getIdCotizacion()).isEqualTo(42L);
		assertThat(cotizacion.getIdUsuario()).isEqualTo(ID_HUESPED);
		assertThat(cotizacion.getIdAlojamiento()).isEqualTo(ID_ALOJAMIENTO);
		assertThat(cotizacion.getHuespedes()).isEqualTo(2);
		assertThat(cotizacion.getNoches()).isEqualTo(3);
		assertThat(cotizacion.getPrecioNoche()).isEqualByComparingTo("200000");
		assertThat(cotizacion.getPrecioBase()).isEqualByComparingTo("600000");
		assertThat(cotizacion.getDescuento()).isEqualByComparingTo("0");
		assertThat(cotizacion.getValorLimpieza()).isEqualByComparingTo("30000");
		assertThat(cotizacion.getValorServicio()).isEqualByComparingTo("60000");
		assertThat(cotizacion.getValorAdicionales()).isEqualByComparingTo("0");
		assertThat(cotizacion.getTotal()).isEqualByComparingTo("690000");
		assertThat(cotizacion.getIdReserva()).isNull();
		assertThat(cotizacion.getFechaExpiracion()).isAfterOrEqualTo(antes.plusMinutes(30))
				.isEqualTo(cotizacion.getFechaCreacion().plusMinutes(CotizacionService.MINUTOS_VIGENCIA));
	}

	@Test
	@DisplayName("Si no se indican huéspedes, la cotización es para 1")
	void unHuespedPorDefecto() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(1))).thenReturn(true);
		when(tarifaService.porcentajeLimpieza(any())).thenReturn(BigDecimal.ZERO);
		when(tarifaService.porcentajeServicio(any())).thenReturn(BigDecimal.ZERO);
		when(cotizacionRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitud(BASE, BASE.plusDays(1), null), ID_HUESPED);

		assertThat(resultado.getDato().getHuespedes()).isEqualTo(1);
		assertThat(resultado.getDato().getTotal()).isEqualByComparingTo("200000");
	}

	@Test
	@DisplayName("Sin alojamiento no se cotiza")
	void faltaAlojamiento() {
		assertThat(servicio.cotizar(null, ID_HUESPED).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		Resultado<CotizacionDTO> resultado = servicio.cotizar(new CotizacionDTO(), ID_HUESPED);
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo(CotizacionService.FALTA_ALOJAMIENTO);
	}

	@Test
	@DisplayName("Un alojamiento que no existe responde no encontrado")
	void alojamientoNoExiste() {
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.empty());

		Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitud(BASE, BASE.plusDays(1), 1), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("CA-071 Un alojamiento que no está ACTIVA no se puede cotizar")
	void noActiva_CA071() {
		alojamiento(EstadoAlojamiento.PAUSADA);

		Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitud(BASE, BASE.plusDays(1), 1), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(resultado.getMensaje()).contains("PAUSADA");
		verify(cotizacionRepo, never()).save(any());
	}

	@Test
	@DisplayName("Fechas inválidas, huéspedes no positivos o sobre la capacidad no se cotizan")
	void datosInvalidos() {
		alojamiento(EstadoAlojamiento.ACTIVA);

		assertThat(servicio.cotizar(solicitud(BASE, BASE, 1), ID_HUESPED).getMensaje())
				.isEqualTo(CalendarioService.SALIDA_NO_POSTERIOR);
		assertThat(servicio.cotizar(solicitud(BASE, BASE.plusDays(1), 0), ID_HUESPED).getMensaje())
				.isEqualTo(CotizacionService.HUESPEDES_INVALIDOS);
		Resultado<CotizacionDTO> excede = servicio.cotizar(solicitud(BASE, BASE.plusDays(1), 5), ID_HUESPED);
		assertThat(excede.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(excede.getMensaje()).contains("capacidad");
		verify(cotizacionRepo, never()).save(any());
	}

	@Test
	@DisplayName("Si las fechas no están libres no se cotiza")
	void fechasOcupadas() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(2))).thenReturn(false);

		Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitud(BASE, BASE.plusDays(2), 1), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_DISPONIBLE);
		assertThat(resultado.getMensaje()).isEqualTo(CotizacionService.FECHAS_NO_DISPONIBLES);
		verify(cotizacionRepo, never()).save(any());
	}

	private void alojamiento(EstadoAlojamiento estado) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID_ALOJAMIENTO);
		alojamiento.setEstado(estado);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		when(alojamientoRepo.findById(ID_ALOJAMIENTO)).thenReturn(Optional.of(alojamiento));
	}

	private static CotizacionDTO solicitud(LocalDate entrada, LocalDate salida, Integer huespedes) {
		CotizacionDTO solicitud = new CotizacionDTO();
		solicitud.setIdAlojamiento(ID_ALOJAMIENTO);
		solicitud.setFechaEntrada(entrada);
		solicitud.setFechaSalida(salida);
		solicitud.setHuespedes(huespedes);
		return solicitud;
	}
}
