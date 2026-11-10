package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PoliticaCancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.HistorialEstadoAlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ImagenRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PeriodoDisponibilidadRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ServicioAdicionalRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.service.CloudinaryService.ImagenSubida;
import co.edu.unbosque.marketplaceInversionesLR.service.GeocodingService.Coordenadas;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de la creación de publicaciones (HU-15). La base de datos,
 * Cloudinary y el geocoding se simulan.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionServiceTest {

	/** Anfitrión que crea la publicación. */
	private static final Long ANFITRION = 4L;

	/** Id que recibe el alojamiento al guardarse. */
	private static final Long ID = 20L;

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
	private AlmacenamientoService almacenamientoService;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private GeocodingService geocodingService;

	@Mock
	private NotificacionService notificacionService;

	private PublicacionService servicio;

	/** Imágenes que quedan "guardadas" en el almacén simulado. */
	private final List<Imagen> guardadas = new ArrayList<>();

	@BeforeEach
	void preparar() {
		servicio = new PublicacionService(alojamientoRepo, imagenRepo, periodoRepo, servicioRepo, politicaRepo,
				historialRepo, usuarioRepo, almacenamientoService, auditoriaService, geocodingService,
				notificacionService, new ModelMapper());
	}

	@Test
	@DisplayName("CA-094 El anfitrión crea la publicación con sus datos e imágenes y queda en BORRADOR")
	void creaEnBorrador_CA094() {
		prepararGuardado();
		when(politicaRepo.existsById(1L)).thenReturn(true);
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre("Flexible");
		when(politicaRepo.findById(1L)).thenReturn(Optional.of(politica));
		Usuario anfitrion = new Usuario();
		anfitrion.setNombre("Ana");
		when(usuarioRepo.findById(ANFITRION)).thenReturn(Optional.of(anfitrion));

		Resultado<AlojamientoDTO> resultado = servicio.crear(datosCompletos(), List.of(imagen("a.jpg"), imagen("b.jpg")),
				ANFITRION);

		assertThat(resultado.esExito()).isTrue();
		AlojamientoDTO dto = resultado.getDato();
		assertThat(dto.getIdAlojamiento()).isEqualTo(ID);
		assertThat(dto.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(dto.getIdAnfitrion()).isEqualTo(ANFITRION);
		assertThat(dto.getTitulo()).isEqualTo("Casa en el lago");
		assertThat(dto.getDescripcion()).isEqualTo("Vista al lago");
		assertThat(dto.getUbicacion()).isEqualTo("Calle 1 # 2-3");
		assertThat(dto.getTipo()).isEqualTo("Casa");
		assertThat(dto.getCapacidad()).isEqualTo(4);
		assertThat(dto.getPrecioNoche()).isEqualByComparingTo("250000");
		assertThat(dto.getReglas()).isEqualTo("No fumar");
		assertThat(dto.getAmenidades()).containsExactlyInAnyOrder("wifi", "piscina");
		assertThat(dto.getImagenes()).containsExactly("https://img/a.jpg", "https://img/b.jpg");
		assertThat(dto.getDetalleImagenes().get(0).getPrincipal()).isTrue();
		assertThat(dto.getNombrePolitica()).isEqualTo("Flexible");
		assertThat(dto.getNombreAnfitrion()).isEqualTo("Ana");
		assertThat(dto.getFechaCreacion()).isNotNull().isEqualTo(dto.getFechaActualizacion());

		ArgumentCaptor<HistorialEstadoAlojamiento> historial = ArgumentCaptor
				.forClass(HistorialEstadoAlojamiento.class);
		verify(historialRepo).save(historial.capture());
		assertThat(historial.getValue().getEstadoAnterior()).isNull();
		assertThat(historial.getValue().getEstadoNuevo()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(historial.getValue().getIdUsuario()).isEqualTo(ANFITRION);
		// Llegaron coordenadas: no se consulta el mapa
		verify(geocodingService, never()).ubicar(any(), any());
	}

	@Test
	@DisplayName("Sin coordenadas busca la dirección en el mapa; solo el título es obligatorio en el borrador")
	void ubicaEnElMapaSinCoordenadas() {
		prepararGuardado();
		when(geocodingService.ubicar(null, "Bogotá")).thenReturn(Optional.of(new Coordenadas(4.6, -74.08)));
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setTitulo("  Solo título  ");
		datos.setCiudad("Bogotá");

		AlojamientoDTO dto = servicio.crear(datos, List.of(imagen("a.png")), ANFITRION).getDato();

		assertThat(dto.getTitulo()).isEqualTo("Solo título");
		assertThat(dto.getLatitud()).isEqualTo(4.6);
		assertThat(dto.getLongitud()).isEqualTo(-74.08);
		assertThat(dto.getNombrePolitica()).isNull();
	}

	@Test
	@DisplayName("Si el mapa no encuentra la dirección, las coordenadas quedan vacías")
	void sinResultadoDelMapa() {
		prepararGuardado();
		when(geocodingService.ubicar(any(), any())).thenReturn(Optional.empty());
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setTitulo("Casa");

		AlojamientoDTO dto = servicio.crear(datos, List.of(imagen("a.png")), ANFITRION).getDato();

		assertThat(dto.getLatitud()).isNull();
		assertThat(dto.getLongitud()).isNull();
	}

	@Test
	@DisplayName("CA-098 La capacidad o el precio iguales o menores que cero se rechazan e informan que deben ser mayores que cero")
	void capacidadYPrecioMayoresQueCero_CA098() {
		assertThat(error(conCapacidad(0))).isEqualTo("La capacidad debe ser mayor que cero.");
		assertThat(error(conCapacidad(-2))).isEqualTo("La capacidad debe ser mayor que cero.");
		assertThat(error(conPrecio("0"))).isEqualTo("El precio por noche debe ser positivo y de máximo 1.000.000.000.");
		assertThat(error(conPrecio("-1"))).isEqualTo("El precio por noche debe ser positivo y de máximo 1.000.000.000.");
		assertThat(error(conPrecio("1000000000.01")))
				.isEqualTo("El precio por noche debe ser positivo y de máximo 1.000.000.000.");
		verify(alojamientoRepo, never()).save(any());
	}

	@Test
	@DisplayName("Rechaza datos faltantes, textos largos, números negativos, porcentajes y coordenadas fuera de rango")
	void rechazaDatosInvalidos() {
		assertThat(servicio.crear(null, List.of(imagen("a.jpg")), ANFITRION).getMensaje())
				.isEqualTo("Debes enviar los datos del alojamiento.");
		AlojamientoDTO sinTitulo = new AlojamientoDTO();
		sinTitulo.setTitulo(" ");
		assertThat(error(sinTitulo)).isEqualTo("El título es obligatorio.");

		AlojamientoDTO largo = basico();
		largo.setCiudad("x".repeat(256));
		assertThat(error(largo)).startsWith("El título, la ubicación, la ciudad y el tipo pueden tener máximo 255");
		AlojamientoDTO descripcion = basico();
		descripcion.setReglas("x".repeat(2001));
		assertThat(error(descripcion)).startsWith("La descripción y las reglas pueden tener máximo 2000");
		AlojamientoDTO amenidad = basico();
		amenidad.setAmenidades(Set.of("x".repeat(256)));
		assertThat(error(amenidad)).startsWith("Cada amenidad puede tener máximo 255");

		AlojamientoDTO negativos = basico();
		negativos.setBanos(-1);
		assertThat(error(negativos)).isEqualTo("Las habitaciones, las camas y los baños no pueden ser negativos.");
		AlojamientoDTO porcentaje = basico();
		porcentaje.setPorcentajeServicio(new BigDecimal("101"));
		assertThat(error(porcentaje)).isEqualTo("Los porcentajes de limpieza y servicio deben estar entre 0 y 100.");
		AlojamientoDTO coordenadas = basico();
		coordenadas.setLatitud(91.0);
		assertThat(error(coordenadas))
				.isEqualTo("La latitud debe estar entre -90 y 90 y la longitud entre -180 y 180.");

		AlojamientoDTO politica = basico();
		politica.setIdPolitica(99L);
		when(politicaRepo.existsById(99L)).thenReturn(false);
		assertThat(error(politica)).isEqualTo("La política de cancelación no existe.");
	}

	@Test
	@DisplayName("Exige entre 1 y 15 imágenes válidas antes de guardar")
	void validaLasImagenes() {
		assertThat(servicio.crear(basico(), null, ANFITRION).getMensaje()).isEqualTo("Debes enviar al menos una imagen.");
		assertThat(servicio.crear(basico(), List.of(), ANFITRION).getMensaje())
				.isEqualTo("Debes enviar al menos una imagen.");
		assertThat(servicio.crear(basico(), Collections.nCopies(16, imagen("a.jpg")), ANFITRION).getMensaje())
				.isEqualTo("Un alojamiento puede tener máximo 15 imágenes.");

		MultipartFile pdf = new MockMultipartFile("archivos", "archivo.pdf", "application/pdf", new byte[] { 1 });
		when(almacenamientoService.errorImagen(pdf)).thenReturn("Solo se aceptan imágenes JPG, PNG o WEBP.");
		assertThat(servicio.crear(basico(), List.of(pdf), ANFITRION).getMensaje())
				.isEqualTo("Solo se aceptan imágenes JPG, PNG o WEBP. (archivo.pdf)");

		List<MultipartFile> conNulo = new ArrayList<>();
		conNulo.add(null);
		when(almacenamientoService.errorImagen(null)).thenReturn("El archivo está vacío.");
		assertThat(servicio.crear(basico(), conNulo, ANFITRION).getMensaje()).isEqualTo("El archivo está vacío.");
		verify(alojamientoRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-097 Una publicación sin la información mínima indica los datos que le faltan")
	void indicaLosDatosFaltantes_CA097() {
		Alojamiento borrador = new Alojamiento();
		borrador.setIdAlojamiento(ID);
		borrador.setTitulo("Solo título");
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of());

		assertThat(servicio.camposFaltantes(borrador)).containsExactly("descripción", "ubicación", "ciudad", "tipo",
				"reglas", "capacidad", "habitaciones", "baños", "hora de entrada", "hora de salida",
				"precio por noche", "política de cancelación", "al menos una imagen",
				"fechas de disponibilidad desde hoy");
	}

	@Test
	@DisplayName("CA-097 Una publicación con toda la información mínima no tiene datos faltantes")
	void sinDatosFaltantes_CA097() {
		Alojamiento completo = new Alojamiento();
		completo.setIdAlojamiento(ID);
		completo.setTitulo("Casa");
		completo.setDescripcion("Descripción");
		completo.setUbicacion("Calle 1");
		completo.setCiudad("Bogotá");
		completo.setTipo("Casa");
		completo.setReglas("No fumar");
		completo.setCapacidad(2);
		completo.setHabitaciones(1);
		completo.setBanos(1);
		completo.setHoraEntrada(LocalTime.of(15, 0));
		completo.setHoraSalida(LocalTime.of(11, 0));
		completo.setPrecioNoche(BigDecimal.TEN);
		completo.setIdPolitica(1L);
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(new Imagen()));
		when(periodoRepo.existsByIdAlojamientoAndFechaFinAfter(eq(ID), any())).thenReturn(true);

		assertThat(servicio.camposFaltantes(completo)).isEmpty();
	}

	@Test
	@DisplayName("Lista las políticas de cancelación disponibles")
	void politicas() {
		PoliticaCancelacion flexible = new PoliticaCancelacion();
		flexible.setIdPolitica(1L);
		flexible.setNombre("Flexible");
		flexible.setDiasReembolsoTotal(1);
		when(politicaRepo.findAll()).thenReturn(List.of(flexible));

		List<PoliticaCancelacionDTO> politicas = servicio.politicas();

		assertThat(politicas).singleElement().satisfies(p -> {
			assertThat(p.getIdPolitica()).isEqualTo(1L);
			assertThat(p.getNombre()).isEqualTo("Flexible");
			assertThat(p.getDiasReembolsoTotal()).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("Las imágenes salen con la principal primero")
	void principalPrimero() {
		Imagen segunda = new Imagen();
		segunda.setUrl("b");
		segunda.setPrincipal(false);
		Imagen portada = new Imagen();
		portada.setUrl("a");
		portada.setPrincipal(true);
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(segunda, portada));

		assertThat(servicio.imagenesDTO(ID)).extracting("url").containsExactly("a", "b");
	}

	/**
	 * Simula el guardado: el alojamiento recibe su id y las imágenes subidas
	 * quedan en el almacén.
	 */
	private void prepararGuardado() {
		when(alojamientoRepo.save(any(Alojamiento.class))).thenAnswer(invocacion -> {
			Alojamiento alojamiento = invocacion.getArgument(0);
			alojamiento.setIdAlojamiento(ID);
			return alojamiento;
		});
		lenient().when(almacenamientoService.errorImagen(any())).thenReturn(null);
		when(imagenRepo.siguienteOrden()).thenReturn(1L, 2L, 3L);
		when(almacenamientoService.guardarImagen(any(), eq(ID), any(Boolean.class), anyLong()))
				.thenAnswer(invocacion -> {
					MultipartFile archivo = invocacion.getArgument(0);
					return new ImagenSubida("id-" + archivo.getOriginalFilename(),
							"https://img/" + archivo.getOriginalFilename());
				});
		when(imagenRepo.save(any(Imagen.class))).thenAnswer(invocacion -> {
			guardadas.add(invocacion.getArgument(0));
			return invocacion.getArgument(0);
		});
		when(imagenRepo.findByIdAlojamiento(ID)).thenAnswer(invocacion -> guardadas);
	}

	/**
	 * Mensaje de error al crear con los datos indicados y una imagen válida.
	 *
	 * @param datos datos del alojamiento
	 * @return mensaje de error
	 */
	private String error(AlojamientoDTO datos) {
		lenient().when(almacenamientoService.errorImagen(any())).thenReturn(null);
		Resultado<AlojamientoDTO> resultado = servicio.crear(datos, List.of(imagen("a.jpg")), ANFITRION);
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		return resultado.getMensaje();
	}

	/**
	 * Datos con solo el título.
	 *
	 * @return datos básicos
	 */
	private static AlojamientoDTO basico() {
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setTitulo("Casa");
		return datos;
	}

	/**
	 * Datos con la capacidad indicada.
	 *
	 * @param capacidad capacidad
	 * @return datos
	 */
	private static AlojamientoDTO conCapacidad(int capacidad) {
		AlojamientoDTO datos = basico();
		datos.setCapacidad(capacidad);
		return datos;
	}

	/**
	 * Datos con el precio indicado.
	 *
	 * @param precio precio por noche
	 * @return datos
	 */
	private static AlojamientoDTO conPrecio(String precio) {
		AlojamientoDTO datos = basico();
		datos.setPrecioNoche(new BigDecimal(precio));
		return datos;
	}

	/**
	 * Datos completos de una publicación.
	 *
	 * @return datos
	 */
	private static AlojamientoDTO datosCompletos() {
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setIdAlojamiento(999L);
		datos.setEstado(EstadoAlojamiento.ACTIVA);
		datos.setIdAnfitrion(77L);
		datos.setTitulo("Casa en el lago");
		datos.setDescripcion("Vista al lago");
		datos.setUbicacion("Calle 1 # 2-3");
		datos.setCiudad("Guatapé");
		datos.setLatitud(6.23);
		datos.setLongitud(-75.15);
		datos.setTipo("Casa");
		datos.setCapacidad(4);
		datos.setHabitaciones(2);
		datos.setCamas(3);
		datos.setBanos(2);
		datos.setHoraEntrada(LocalTime.of(15, 0));
		datos.setHoraSalida(LocalTime.of(11, 0));
		datos.setPrecioNoche(new BigDecimal("250000"));
		datos.setPorcentajeLimpieza(new BigDecimal("5"));
		datos.setPorcentajeServicio(new BigDecimal("10"));
		datos.setReglas("No fumar");
		datos.setIdPolitica(1L);
		datos.setAmenidades(Set.of(" WiFi ", "Piscina", " "));
		return datos;
	}

	/**
	 * Archivo de imagen de prueba.
	 *
	 * @param nombre nombre del archivo
	 * @return archivo
	 */
	private static MultipartFile imagen(String nombre) {
		return new MockMultipartFile("archivos", nombre, "image/jpeg", new byte[] { 1, 2, 3 });
	}
}
