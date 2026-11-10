package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.HistorialEstadoAlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ImagenRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService.FiltrosBusqueda;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-23 Buscar alojamientos con filtros: validación de
 * los filtros, elección del orden y datos de cada resultado. La base de datos
 * se simula; los filtros sobre la base se prueban en
 * {@code BusquedaAlojamientosTest}.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionServiceBusquedaTest {

	/** Publicación de prueba. */
	private static final Long ID = 20L;

	/** Dueño de la publicación. */
	private static final Long ANFITRION = 4L;

	/** Página por defecto de la búsqueda: los más recientes primero. */
	private static final Pageable RECIENTES = PageRequest.of(0, 12, Sort.by(Sort.Order.desc("fechaCreacion")));

	/** Fecha base: dentro de un mes, para no depender del día en que se corre. */
	private static final LocalDate BASE = LocalDate.now().plusMonths(1);

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private ImagenRepository imagenRepo;

	@Mock
	private PeriodoDisponibilidadRepository periodoRepo;

	@Mock
	private ServicioAdicionalRepository servicioRepo;

	@Mock
	private PoliticaCancelacionRepository politicaRepo;

	@Mock
	private HistorialEstadoAlojamientoRepository historialRepo;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private ResenaRepository resenaRepo;

	@Mock
	private AlmacenamientoService almacenamientoService;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private GeocodingService geocodingService;

	@Mock
	private NotificacionService notificacionService;

	private PublicacionService servicio;

	/** Publicación ACTIVA que devuelve el repositorio simulado. */
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new PublicacionService(alojamientoRepo, imagenRepo, periodoRepo, servicioRepo, politicaRepo,
				historialRepo, usuarioRepo, resenaRepo, almacenamientoService, auditoriaService, geocodingService,
				notificacionService, new ModelMapper());
		alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID);
		alojamiento.setIdAnfitrion(ANFITRION);
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setTitulo("Cabaña en el lago");
		alojamiento.setCiudad("Guatapé");
		alojamiento.setTipo("Cabaña");
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("320000"));
		alojamiento.getAmenidades().add("wifi");
	}

	@Test
	@DisplayName("CA-087 Sin orden busca con la página recibida (los más recientes) y arma cada resultado")
	@SuppressWarnings("unchecked")
	void buscaSinOrden_CA087() {
		when(alojamientoRepo.findAll(any(Specification.class), eq(RECIENTES))).thenReturn(pagina(RECIENTES));
		resenas(14.0 / 3, 3L);

		Resultado<PaginaDTO<AlojamientoDTO>> resultado = servicio.buscar(new FiltrosBusqueda("Guatapé", null, null, 2, "cabaña",
				new BigDecimal("100000"), new BigDecimal("400000"), List.of("wifi")), null, RECIENTES);

		assertThat(resultado.esExito()).isTrue();
		PaginaDTO<AlojamientoDTO> pagina = resultado.getDato();
		assertThat(pagina.getTotalElementos()).isEqualTo(1);
		AlojamientoDTO dto = pagina.getContenido().get(0);
		assertThat(dto.getIdAlojamiento()).isEqualTo(ID);
		assertThat(dto.getCiudad()).isEqualTo("Guatapé");
		assertThat(dto.getAmenidades()).containsExactly("wifi");
		assertThat(dto.getNombreAnfitrion()).isEqualTo("Ana");
		// 4,666... se redondea a una cifra decimal
		assertThat(dto.getPromedioCalificacion()).isEqualTo(4.7);
		assertThat(dto.getCantidadResenas()).isEqualTo(3L);
	}

	@Test
	@DisplayName("CA-087 Sin reseñas publicadas el promedio va vacío y la cantidad en cero")
	@SuppressWarnings("unchecked")
	void sinResenas_CA087() {
		when(alojamientoRepo.findAll(any(Specification.class), eq(RECIENTES))).thenReturn(pagina(RECIENTES));
		resenas(null, 0L);

		AlojamientoDTO dto = servicio.buscar(new FiltrosBusqueda(null, null, null, null, null, null, null, null), "  ", RECIENTES)
				.getDato().getContenido().get(0);

		assertThat(dto.getPromedioCalificacion()).isNull();
		assertThat(dto.getCantidadResenas()).isZero();
	}

	@Test
	@DisplayName("CA-087 Con fechas válidas también busca")
	@SuppressWarnings("unchecked")
	void conFechas_CA087() {
		when(alojamientoRepo.findAll(any(Specification.class), eq(RECIENTES))).thenReturn(pagina(RECIENTES));
		resenas(null, 0L);

		assertThat(servicio.buscar(new FiltrosBusqueda(null, BASE, BASE.plusDays(2), null, null, null, null, List.of()), null,
				RECIENTES).esExito()).isTrue();
	}

	@Test
	@DisplayName("Filtros inválidos responden datos inválidos sin consultar la base de datos")
	void filtrosInvalidos() {
		assertInvalido(new FiltrosBusqueda(null, BASE, null, null, null, null, null, null), CalendarioService.FECHAS_FALTANTES);
		assertInvalido(new FiltrosBusqueda(null, null, BASE, null, null, null, null, null), CalendarioService.FECHAS_FALTANTES);
		assertInvalido(new FiltrosBusqueda(null, BASE, BASE, null, null, null, null, null), CalendarioService.SALIDA_NO_POSTERIOR);
		assertInvalido(new FiltrosBusqueda(null, LocalDate.now().minusDays(1), BASE, null, null, null, null, null),
				CalendarioService.LLEGADA_EN_EL_PASADO);
		assertInvalido(new FiltrosBusqueda(null, null, null, 0, null, null, null, null), PublicacionService.HUESPEDES_INVALIDOS);
		assertInvalido(new FiltrosBusqueda(null, null, null, -2, null, null, null, null), PublicacionService.HUESPEDES_INVALIDOS);
		assertInvalido(new FiltrosBusqueda(null, null, null, null, null, new BigDecimal("-1"), null, null),
				PublicacionService.PRECIO_NEGATIVO);
		assertInvalido(new FiltrosBusqueda(null, null, null, null, null, null, new BigDecimal("-0.01"), null),
				PublicacionService.PRECIO_NEGATIVO);
		assertInvalido(new FiltrosBusqueda(null, null, null, null, null, new BigDecimal("500000"), new BigDecimal("100000"), null),
				PublicacionService.PRECIOS_INVERTIDOS);
		verifyNoInteractions(alojamientoRepo);
	}

	@Test
	@DisplayName("El mismo precio como mínimo y máximo es válido")
	@SuppressWarnings("unchecked")
	void precioExacto() {
		when(alojamientoRepo.findAll(any(Specification.class), eq(RECIENTES))).thenReturn(Page.empty(RECIENTES));

		assertThat(servicio.buscar(new FiltrosBusqueda(null, null, null, null, null, new BigDecimal("320000"),
				new BigDecimal("320000"), null), null, RECIENTES).esExito()).isTrue();
	}

	@Test
	@DisplayName("Ordenar por precio usa el orden de la base de datos con desempate por id")
	@SuppressWarnings("unchecked")
	void ordenPorPrecio() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		when(alojamientoRepo.findAll(any(Specification.class), pagina.capture())).thenReturn(Page.empty());
		FiltrosBusqueda sinFiltros = new FiltrosBusqueda(null, null, null, null, null, null, null, null);

		assertThat(servicio.buscar(sinFiltros, "precio_menor", RECIENTES).esExito()).isTrue();
		assertThat(servicio.buscar(sinFiltros, " PRECIO_MAYOR ", PageRequest.of(2, 5)).esExito()).isTrue();

		Pageable menor = pagina.getAllValues().get(0);
		assertThat(menor.getSort().getOrderFor("precioNoche").isAscending()).isTrue();
		assertThat(menor.getSort().getOrderFor("idAlojamiento")).isNotNull();
		Pageable mayor = pagina.getAllValues().get(1);
		assertThat(mayor.getSort().getOrderFor("precioNoche").isDescending()).isTrue();
		assertThat(mayor.getPageNumber()).isEqualTo(2);
		assertThat(mayor.getPageSize()).isEqualTo(5);
	}

	@Test
	@DisplayName("Ordenar por reseñas cuenta el total sin el orden y pagina sin otro orden")
	@SuppressWarnings("unchecked")
	void ordenPorResenas() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		when(alojamientoRepo.findAll(any(Specification.class), any(Specification.class), pagina.capture()))
				.thenReturn(Page.empty());
		FiltrosBusqueda sinFiltros = new FiltrosBusqueda(null, null, null, null, null, null, null, null);

		assertThat(servicio.buscar(sinFiltros, "recomendados", RECIENTES).esExito()).isTrue();
		assertThat(servicio.buscar(sinFiltros, "mejor_calificados", RECIENTES).esExito()).isTrue();

		assertThat(pagina.getAllValues()).allSatisfy(p -> {
			assertThat(p.getSort().isUnsorted()).isTrue();
			assertThat(p.getPageSize()).isEqualTo(12);
		});
	}

	@Test
	@DisplayName("Un orden que no existe responde datos inválidos sin consultar la base de datos")
	void ordenInvalido() {
		Resultado<PaginaDTO<AlojamientoDTO>> busqueda = servicio
				.buscar(new FiltrosBusqueda(null, null, null, null, null, null, null, null), "barato", RECIENTES);
		Resultado<PaginaDTO<AlojamientoDTO>> catalogo = servicio.disponibles("barato", RECIENTES);

		assertThat(busqueda.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(busqueda.getMensaje()).isEqualTo(PublicacionService.ORDEN_INVALIDO);
		assertThat(catalogo.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		verifyNoInteractions(alojamientoRepo);
	}

	@Test
	@DisplayName("El catálogo ordena por recomendados si no se indica orden")
	@SuppressWarnings("unchecked")
	void catalogoRecomendadosPorDefecto() {
		Pageable sinOrden = PageRequest.of(0, 20);
		when(alojamientoRepo.findAll(any(Specification.class), any(Specification.class), eq(sinOrden)))
				.thenReturn(pagina(sinOrden));
		resenas(5.0, 1L);

		Resultado<PaginaDTO<AlojamientoDTO>> resultado = servicio.disponibles(null, sinOrden);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getContenido()).singleElement()
				.satisfies(d -> assertThat(d.getPromedioCalificacion()).isEqualTo(5.0));
		verify(alojamientoRepo, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	@DisplayName("Los tipos se devuelven en el orden de la consulta con su cantidad")
	void cantidadPorTipo() {
		when(alojamientoRepo.contarPorTipo()).thenReturn(List.of(new Object[] { "Apartamento", 2L },
				new Object[] { "Cabaña", 1L }, new Object[] { "Casa", 5L }));

		Map<String, Long> tipos = servicio.cantidadPorTipo();

		assertThat(tipos).containsExactly(Map.entry("Apartamento", 2L), Map.entry("Cabaña", 1L),
				Map.entry("Casa", 5L));
	}

	/**
	 * Verifica que la búsqueda responda datos inválidos con el mensaje.
	 *
	 * @param filtros filtros a probar
	 * @param mensaje mensaje esperado
	 */
	private void assertInvalido(FiltrosBusqueda filtros, String mensaje) {
		Resultado<PaginaDTO<AlojamientoDTO>> resultado = servicio.buscar(filtros, null, RECIENTES);
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo(mensaje);
	}

	/**
	 * Página con la publicación de prueba.
	 *
	 * @param pageable página pedida
	 * @return página de un elemento
	 */
	private Page<Alojamiento> pagina(Pageable pageable) {
		return new PageImpl<>(List.of(alojamiento), pageable, 1);
	}

	/**
	 * Simula las reseñas publicadas y el anfitrión de la publicación.
	 *
	 * @param promedio promedio de calificaciones; {@code null} si no tiene
	 * @param cantidad cantidad de reseñas
	 */
	private void resenas(Double promedio, long cantidad) {
		when(resenaRepo.promedio(ID, EstadoResena.PUBLICADA)).thenReturn(promedio);
		when(resenaRepo.countByIdAlojamientoAndEstado(ID, EstadoResena.PUBLICADA)).thenReturn(cantidad);
		Usuario ana = new Usuario();
		ana.setNombre("Ana");
		when(usuarioRepo.findById(ANFITRION)).thenReturn(Optional.of(ana));
	}
}
