package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
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
import co.edu.unbosque.marketplaceInversionesLR.entity.ServicioAdicional;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.CotizacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas unitarias de la cotización (HU-22, HU-31), con los repositorios, el
 * calendario y las tarifas simulados.
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
	private ServicioAdicionalRepository servicioRepo;

	@Mock
	private CotizacionRepository cotizacionRepo;

	@Mock
	private CalendarioService calendarioService;

	@Mock
	private TarifaService tarifaService;

	private CotizacionService servicio;

	@BeforeEach
	void preparar() {
		servicio = new CotizacionService(alojamientoRepo, servicioRepo, cotizacionRepo, calendarioService,
				tarifaService, new ModelMapper(), JsonMapper.builder().build());
	}

	@Test
	@DisplayName("CA-086 El total es el precio base más limpieza y servicio, redondeado a 2 decimales")
	void calcula_CA086() {
		CotizacionService.Desglose desglose = CotizacionService.calcular(new BigDecimal("200000"), 3,
				new BigDecimal("5"), new BigDecimal("10"), List.of());

		assertThat(desglose.precioBase()).isEqualByComparingTo("600000");
		assertThat(desglose.valorLimpieza()).isEqualByComparingTo("30000");
		assertThat(desglose.valorServicio()).isEqualByComparingTo("60000");
		assertThat(desglose.total()).isEqualByComparingTo("690000");

		CotizacionService.Desglose decimales = CotizacionService.calcular(new BigDecimal("99999.99"), 1,
				new BigDecimal("7.5"), new BigDecimal("12.25"), List.of());
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

	@Test
	@DisplayName("CA-080 El total es la suma correcta de todos los componentes")
	void totalEsLaSuma_CA080() {
		CotizacionService.Desglose d = CotizacionService.calcular(new BigDecimal("150000"), 3, new BigDecimal("5"),
				new BigDecimal("10"), List.of(new BigDecimal("20000"), new BigDecimal("15000")));

		assertThat(d.precioBase()).isEqualByComparingTo("450000");
		assertThat(d.descuento()).isEqualByComparingTo("0");
		assertThat(d.valorLimpieza()).isEqualByComparingTo("22500");
		assertThat(d.valorServicio()).isEqualByComparingTo("45000");
		assertThat(d.valorAdicionales()).isEqualByComparingTo("35000");
		assertThat(d.total()).isEqualByComparingTo("552500").isEqualByComparingTo(d.precioBase()
				.subtract(d.descuento()).add(d.valorLimpieza()).add(d.valorServicio()).add(d.valorAdicionales()));
	}

	@Test
	@DisplayName("CA-082 Una salida igual o anterior a la llegada se rechaza por fechas inválidas")
	void salidaNoPosterior_CA082() {
		alojamiento(EstadoAlojamiento.ACTIVA);

		for (LocalDate salida : List.of(BASE, BASE.minusDays(1))) {
			Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitud(BASE, salida, 1), ID_HUESPED);

			assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
			assertThat(resultado.getMensaje()).isEqualTo(CalendarioService.SALIDA_NO_POSTERIOR);
		}
		verify(cotizacionRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-083 Se rechaza e informa el motivo si excede la capacidad o las fechas no están libres")
	void rechazaCapacidadYFechas_CA083() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(2))).thenReturn(false);

		Resultado<CotizacionDTO> excede = servicio.cotizar(solicitud(BASE, BASE.plusDays(2), 9), ID_HUESPED);
		Resultado<CotizacionDTO> ocupado = servicio.cotizar(solicitud(BASE, BASE.plusDays(2), 2), ID_HUESPED);

		assertThat(excede.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(excede.getMensaje()).contains("(9)").contains("capacidad");
		assertThat(ocupado.getCodigo()).isEqualTo(Resultado.NO_DISPONIBLE);
		assertThat(ocupado.getMensaje()).isEqualTo(CotizacionService.FECHAS_NO_DISPONIBLES);
		verify(cotizacionRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-084 Cada cotización nueva aplica el porcentaje vigente, incluido uno recién modificado")
	void aplicaPorcentajeVigente_CA084() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(tarifaService.porcentajeLimpieza(any())).thenReturn(new BigDecimal("5"), new BigDecimal("8"));
		when(tarifaService.porcentajeServicio(any())).thenReturn(new BigDecimal("10"));
		when(cotizacionRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

		CotizacionDTO antes = servicio.cotizar(solicitud(BASE, BASE.plusDays(3), 1), ID_HUESPED).getDato();
		CotizacionDTO despues = servicio.cotizar(solicitud(BASE, BASE.plusDays(3), 1), ID_HUESPED).getDato();

		assertThat(antes.getPorcentajeLimpieza()).isEqualByComparingTo("5");
		assertThat(antes.getValorLimpieza()).isEqualByComparingTo("30000");
		assertThat(despues.getPorcentajeLimpieza()).isEqualByComparingTo("8");
		assertThat(despues.getValorLimpieza()).isEqualByComparingTo("48000");
		assertThat(despues.getTotal()).isEqualByComparingTo("708000");
	}

	@Test
	@DisplayName("CA-179 Muestra noches, base, limpieza, servicio, descuento, cada servicio con su costo y el total")
	void muestraElDesglose_CA179() {
		alojamientoConServicios();

		CotizacionDTO cotizacion = servicio.cotizar(solicitudConServicios(1L), ID_HUESPED).getDato();

		assertThat(cotizacion.getNoches()).isEqualTo(3);
		assertThat(cotizacion.getPrecioBase()).isEqualByComparingTo("600000");
		assertThat(cotizacion.getValorLimpieza()).isEqualByComparingTo("30000");
		assertThat(cotizacion.getValorServicio()).isEqualByComparingTo("60000");
		assertThat(cotizacion.getDescuento()).isEqualByComparingTo("0");
		assertThat(cotizacion.getValorAdicionales()).isEqualByComparingTo("30000");
		assertThat(cotizacion.getTotal()).isEqualByComparingTo("720000");
		assertThat(cotizacion.getServiciosAdicionales()).hasSize(1);
		assertThat(cotizacion.getServiciosAdicionales().get(0).getNombre()).isEqualTo("Desayuno");
		assertThat(cotizacion.getServiciosAdicionales().get(0).getCosto()).isEqualByComparingTo("30000");
		assertThat(cotizacion.getServiciosSeleccionados()).containsExactly(1L);
		assertThat(cotizacion.getServiciosDisponibles()).hasSize(2);
	}

	@Test
	@DisplayName("CA-183 El total incluye únicamente los servicios seleccionados, sin cobrar dos veces uno repetido")
	void soloLosSeleccionados_CA183() {
		alojamientoConServicios();

		assertThat(servicio.cotizar(solicitudConServicios(1L, 2L), ID_HUESPED).getDato().getTotal())
				.isEqualByComparingTo("770000");
		assertThat(servicio.cotizar(solicitudConServicios(2L), ID_HUESPED).getDato().getTotal())
				.isEqualByComparingTo("740000");
		assertThat(servicio.cotizar(solicitudConServicios(), ID_HUESPED).getDato().getTotal())
				.isEqualByComparingTo("690000");
		assertThat(servicio.cotizar(solicitudConServicios(1L, 1L), ID_HUESPED).getDato().getTotal())
				.isEqualByComparingTo("720000");
	}

	@Test
	@DisplayName("CA-183 Un servicio de otro alojamiento se rechaza y no se guarda la cotización")
	void servicioAjeno_CA183() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(servicioRepo.findByIdAlojamientoOrderByIdServicio(ID_ALOJAMIENTO))
				.thenReturn(List.of(servicioAdicional(1L, "Desayuno", "30000")));

		Resultado<CotizacionDTO> resultado = servicio.cotizar(solicitudConServicios(99L), ID_HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).contains("99");
		verify(cotizacionRepo, never()).save(any());
	}

	/** Alojamiento ACTIVA, libre y con dos servicios: 30.000 y 50.000. */
	private void alojamientoConServicios() {
		alojamiento(EstadoAlojamiento.ACTIVA);
		when(calendarioService.estaLibre(ID_ALOJAMIENTO, BASE, BASE.plusDays(3))).thenReturn(true);
		when(tarifaService.porcentajeLimpieza(any())).thenReturn(new BigDecimal("5"));
		when(tarifaService.porcentajeServicio(any())).thenReturn(new BigDecimal("10"));
		when(servicioRepo.findByIdAlojamientoOrderByIdServicio(ID_ALOJAMIENTO)).thenReturn(
				List.of(servicioAdicional(1L, "Desayuno", "30000"), servicioAdicional(2L, "Traslado", "50000")));
		when(cotizacionRepo.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
	}

	private static ServicioAdicional servicioAdicional(Long id, String nombre, String costo) {
		ServicioAdicional servicio = new ServicioAdicional();
		servicio.setIdServicio(id);
		servicio.setNombre(nombre);
		servicio.setCosto(new BigDecimal(costo));
		servicio.setIdAlojamiento(ID_ALOJAMIENTO);
		return servicio;
	}

	private static CotizacionDTO solicitudConServicios(Long... servicios) {
		CotizacionDTO solicitud = solicitud(BASE, BASE.plusDays(3), 2);
		solicitud.setServiciosSeleccionados(Arrays.asList(servicios));
		return solicitud;
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
