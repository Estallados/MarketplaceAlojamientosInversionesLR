package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.ServicioAdicional;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.HistorialEstadoAlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ImagenRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-18: detalle de un alojamiento y su ubicación para
 * el mapa. La base de datos se simula.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionServiceDetalleTest {

	/** Dueño de la publicación. */
	private static final Long ANFITRION = 4L;

	/** Usuario autenticado que no es el dueño. */
	private static final Long OTRO = 9L;

	/** Publicación de prueba. */
	private static final Long ID = 20L;

	/** Política de la publicación. */
	private static final Long POLITICA = 1L;

	/** Mensaje cuando no existe o no se puede ver. */
	private static final String NO_EXISTE = "El alojamiento no existe.";

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

	/** Publicación que devuelve el repositorio simulado. */
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
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setDescripcion("Vista al lago");
		alojamiento.setUbicacion("Calle 1 # 2-3");
		alojamiento.setCiudad("Guatapé");
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(BigDecimal.TEN);
		alojamiento.setIdPolitica(POLITICA);
		alojamiento.setLatitud(6.23);
		alojamiento.setLongitud(-75.15);
		lenient().when(alojamientoRepo.findById(ID)).thenReturn(Optional.of(alojamiento));
	}

	@Test
	@DisplayName("CA-103 El anfitrión recibe sus publicaciones con el estado de cada una")
	void misPublicaciones_CA103() {
		Alojamiento borrador = new Alojamiento();
		borrador.setIdAlojamiento(21L);
		borrador.setIdAnfitrion(ANFITRION);
		borrador.setEstado(EstadoAlojamiento.BORRADOR);
		Pageable pagina = PageRequest.of(0, 20);
		when(alojamientoRepo.findByIdAnfitrion(ANFITRION, pagina))
				.thenReturn(new PageImpl<>(List.of(alojamiento, borrador)));

		PaginaDTO<AlojamientoDTO> resultado = servicio.mios(ANFITRION, pagina);

		assertThat(resultado.getTotalElementos()).isEqualTo(2);
		assertThat(resultado.getContenido()).extracting(AlojamientoDTO::getIdAlojamiento).containsExactly(ID, 21L);
		assertThat(resultado.getContenido()).extracting(a -> a.getEstado().toString()).containsExactly("ACTIVA",
				"BORRADOR");
	}

	@Test
	@DisplayName("CA-103 Un anfitrión sin publicaciones recibe la página vacía")
	void sinPublicaciones_CA103() {
		Pageable pagina = PageRequest.of(0, 20);
		when(alojamientoRepo.findByIdAnfitrion(OTRO, pagina)).thenReturn(new PageImpl<>(List.of()));

		PaginaDTO<AlojamientoDTO> resultado = servicio.mios(OTRO, pagina);

		assertThat(resultado.getContenido()).isEmpty();
		assertThat(resultado.getTotalElementos()).isZero();
	}

	@Test
	@DisplayName("CA-093 Una publicación ACTIVA muestra su información detallada aunque no haya sesión")
	void detalleDePublicacionActiva_CA093() {
		datosRelacionados();

		Resultado<AlojamientoDTO> resultado = servicio.obtener(ID, null, false);

		assertThat(resultado.esExito()).isTrue();
		AlojamientoDTO detalle = resultado.getDato();
		assertThat(detalle.getIdAlojamiento()).isEqualTo(ID);
		assertThat(detalle.getTitulo()).isEqualTo("Casa en el lago");
		assertThat(detalle.getDescripcion()).isEqualTo("Vista al lago");
		assertThat(detalle.getCiudad()).isEqualTo("Guatapé");
		assertThat(detalle.getCapacidad()).isEqualTo(4);
		assertThat(detalle.getPrecioNoche()).isEqualTo(BigDecimal.TEN);
		assertThat(detalle.getEstado()).isEqualTo(EstadoAlojamiento.ACTIVA);
		assertThat(detalle.getImagenes()).containsExactly("https://img/principal.jpg");
		assertThat(detalle.getServiciosAdicionales()).hasSize(1);
		assertThat(detalle.getNombrePolitica()).isEqualTo("Flexible");
		assertThat(detalle.getNombreAnfitrion()).isEqualTo("Ana");
	}

	@Test
	@DisplayName("CA-164 El detalle trae la ubicación registrada para mostrarla en el mapa")
	void detalleConUbicacion_CA164() {
		datosRelacionados();

		AlojamientoDTO detalle = servicio.obtener(ID, null, false).getDato();

		assertThat(detalle.getUbicacion()).isEqualTo("Calle 1 # 2-3");
		assertThat(detalle.getLatitud()).isEqualTo(6.23);
		assertThat(detalle.getLongitud()).isEqualTo(-75.15);
	}

	@Test
	@DisplayName("Sin coordenadas registradas el detalle se muestra igual, sin ubicación en el mapa")
	void detalleSinCoordenadas() {
		datosRelacionados();
		alojamiento.setLatitud(null);
		alojamiento.setLongitud(null);

		Resultado<AlojamientoDTO> resultado = servicio.obtener(ID, OTRO, false);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getLatitud()).isNull();
		assertThat(resultado.getDato().getLongitud()).isNull();
	}

	@Test
	@DisplayName("Una publicación no ACTIVA no se muestra sin sesión ni a otros usuarios")
	void noActivaOculta() {
		for (EstadoAlojamiento estado : EstadoAlojamiento.values()) {
			if (estado != EstadoAlojamiento.ACTIVA) {
				alojamiento.setEstado(estado);

				assertNoEncontrado(servicio.obtener(ID, null, false));
				assertNoEncontrado(servicio.obtener(ID, OTRO, false));
			}
		}
	}

	@Test
	@DisplayName("Una publicación no ACTIVA sí la ven su anfitrión y un administrador")
	void noActivaVisibleParaDuenoYAdmin() {
		datosRelacionados();
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);

		assertThat(servicio.obtener(ID, ANFITRION, false).esExito()).isTrue();
		assertThat(servicio.obtener(ID, OTRO, true).esExito()).isTrue();
	}

	@Test
	@DisplayName("Si la publicación no existe responde que no se encontró")
	void publicacionInexistente() {
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		assertNoEncontrado(servicio.obtener(99L, null, true));
	}

	/**
	 * Comprueba que el resultado sea NO_ENCONTRADO con su mensaje.
	 *
	 * @param resultado resultado del servicio
	 */
	private static void assertNoEncontrado(Resultado<AlojamientoDTO> resultado) {
		assertThat(resultado.esExito()).isFalse();
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo(NO_EXISTE);
	}

	/** Simula la imagen, el servicio, la política y el anfitrión de la publicación. */
	private void datosRelacionados() {
		Imagen imagen = new Imagen();
		imagen.setUrl("https://img/principal.jpg");
		imagen.setPrincipal(true);
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(imagen));

		ServicioAdicional servicioAdicional = new ServicioAdicional();
		servicioAdicional.setNombre("Desayuno");
		servicioAdicional.setCosto(BigDecimal.ONE);
		when(servicioRepo.findByIdAlojamientoOrderByIdServicio(ID)).thenReturn(List.of(servicioAdicional));

		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre("Flexible");
		when(politicaRepo.findById(POLITICA)).thenReturn(Optional.of(politica));

		Usuario anfitrion = new Usuario();
		anfitrion.setNombre("Ana");
		when(usuarioRepo.findById(ANFITRION)).thenReturn(Optional.of(anfitrion));
	}
}
