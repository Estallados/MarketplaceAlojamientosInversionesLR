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
import java.time.LocalDateTime;
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
import co.edu.unbosque.marketplaceInversionesLR.dto.ImagenDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Imagen;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.ServicioAdicional;
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
 * Pruebas unitarias de HU-16: edición de la publicación, sus imágenes y sus
 * servicios adicionales. La base de datos, Cloudinary y el geocoding se
 * simulan.
 */
@ExtendWith(MockitoExtension.class)
class PublicacionServiceActualizacionTest {

	/** Dueño de la publicación. */
	private static final Long ANFITRION = 4L;

	/** Usuario autenticado que no es el dueño. */
	private static final Long OTRO = 9L;

	/** Administrador. */
	private static final Long ADMIN = 1L;

	/** Publicación de prueba. */
	private static final Long ID = 20L;

	/** Servicio adicional de prueba. */
	private static final Long ID_SERVICIO = 7L;

	/** Mensaje del rechazo por permisos. */
	private static final String SIN_PERMISO = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.";

	/** Mensaje del servicio adicional inválido. */
	private static final String SERVICIO_INVALIDO = "El servicio adicional necesita un nombre (máximo 255 "
			+ "caracteres) y un costo positivo de máximo 1.000.000.000.";

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

	private PublicacionService servicio;

	/** Publicación que devuelve el repositorio simulado. */
	private Alojamiento alojamiento;

	@BeforeEach
	void preparar() {
		servicio = new PublicacionService(alojamientoRepo, imagenRepo, periodoRepo, servicioRepo, politicaRepo,
				historialRepo, usuarioRepo, almacenamientoService, auditoriaService, geocodingService,
				new ModelMapper());
		alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ID);
		alojamiento.setIdAnfitrion(ANFITRION);
		alojamiento.setTitulo("Original");
		alojamiento.setDescripcion("Descripción original");
		alojamiento.setUbicacion("Calle 1");
		alojamiento.setCiudad("Bogotá");
		alojamiento.setLatitud(4.6);
		alojamiento.setLongitud(-74.08);
		alojamiento.setCapacidad(2);
		alojamiento.setPrecioNoche(new BigDecimal("100000"));
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);
		alojamiento.setAmenidades(Set.of("wifi"));
		alojamiento.setFechaActualizacion(LocalDateTime.of(2026, 1, 1, 8, 0));
		lenient().when(alojamientoRepo.findById(ID)).thenReturn(Optional.of(alojamiento));
		lenient().when(alojamientoRepo.save(any(Alojamiento.class)))
				.thenAnswer(invocacion -> invocacion.getArgument(0));
		lenient().when(servicioRepo.save(any(ServicioAdicional.class))).thenAnswer(invocacion -> {
			ServicioAdicional guardado = invocacion.getArgument(0);
			guardado.setIdServicio(ID_SERVICIO);
			return guardado;
		});
	}

	@Test
	@DisplayName("CA-100 El propietario modifica su publicación y el sistema guarda y muestra los datos nuevos")
	void propietarioActualiza_CA100() {
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setTitulo("  Nuevo título ");
		datos.setCapacidad(6);
		datos.setPrecioNoche(new BigDecimal("180000"));
		datos.setReglas("No fumar");
		datos.setAmenidades(Set.of(" Piscina "));
		datos.setEstado(EstadoAlojamiento.ACTIVA);
		datos.setIdAnfitrion(OTRO);

		Resultado<AlojamientoDTO> resultado = servicio.actualizar(ID, datos, ANFITRION, false);

		assertThat(resultado.esExito()).isTrue();
		AlojamientoDTO dto = resultado.getDato();
		assertThat(dto.getTitulo()).isEqualTo("Nuevo título");
		assertThat(dto.getCapacidad()).isEqualTo(6);
		assertThat(dto.getPrecioNoche()).isEqualByComparingTo("180000");
		assertThat(dto.getReglas()).isEqualTo("No fumar");
		assertThat(dto.getAmenidades()).containsExactly("piscina");
		// Lo que no llegó no cambia, y el estado y el dueño no se editan por aquí
		assertThat(dto.getDescripcion()).isEqualTo("Descripción original");
		assertThat(dto.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(dto.getIdAnfitrion()).isEqualTo(ANFITRION);
		assertThat(alojamiento.getFechaActualizacion()).isAfter(LocalDateTime.of(2026, 1, 2, 0, 0));
		verify(alojamientoRepo).save(alojamiento);
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.ALOJAMIENTO_ACTUALIZADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO, null);
	}

	@Test
	@DisplayName("CA-113 Un administrador puede modificar una publicación que no es suya")
	void administradorModifica_CA113() {
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setTitulo("Corregido por admin");

		Resultado<AlojamientoDTO> resultado = servicio.actualizar(ID, datos, ADMIN, true);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getTitulo()).isEqualTo("Corregido por admin");
		assertThat(resultado.getDato().getIdAnfitrion()).isEqualTo(ANFITRION);
	}

	@Test
	@DisplayName("CA-113 Aun siendo el propietario, los datos deben cumplir las demás reglas")
	void propietarioCumpleLasReglas_CA113() {
		AlojamientoDTO capacidad = new AlojamientoDTO();
		capacidad.setCapacidad(0);
		AlojamientoDTO precio = new AlojamientoDTO();
		precio.setPrecioNoche(BigDecimal.ZERO);

		assertThat(servicio.actualizar(ID, capacidad, ANFITRION, false).getMensaje())
				.isEqualTo("La capacidad debe ser mayor que cero.");
		Resultado<AlojamientoDTO> resultado = servicio.actualizar(ID, precio, ANFITRION, false);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo("El precio por noche debe ser positivo y de máximo 1.000.000.000.");
		assertThat(servicio.actualizar(ID, null, ANFITRION, false).getMensaje())
				.isEqualTo("Debes enviar los datos del alojamiento.");
		verify(alojamientoRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-101 Quien no es propietario ni administrador no puede modificar la publicación ni sus servicios")
	void sinPermisoNoModifica_CA101() {
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setTitulo("Hackeado");

		assertSinPermiso(servicio.actualizar(ID, datos, OTRO, false));
		assertSinPermiso(servicio.agregarServicio(ID, servicio("Robado", "1"), OTRO, false));
		assertSinPermiso(servicio.actualizarServicio(ID, ID_SERVICIO, servicio("Robado", "1"), OTRO, false));
		assertSinPermiso(servicio.eliminarServicio(ID, ID_SERVICIO, OTRO, false));

		assertThat(alojamiento.getTitulo()).isEqualTo("Original");
		verify(alojamientoRepo, never()).save(any());
		verify(servicioRepo, never()).save(any());
		verify(servicioRepo, never()).delete(any());
	}

	@Test
	@DisplayName("CA-114 Un usuario ajeno a la publicación no puede tocar sus imágenes y el intento queda en la auditoría")
	void usuarioAjenoDenegado_CA114() {
		assertSinPermiso(servicio.subirImagenes(ID, List.of(imagen("a.jpg")), OTRO, false));
		assertSinPermiso(servicio.eliminarImagen(ID, 1L, OTRO, false));
		assertSinPermiso(servicio.marcarPrincipal(ID, 1L, OTRO, false));
		assertSinPermiso(servicio.actualizar(ID, new AlojamientoDTO(), null, false));

		verify(imagenRepo, never()).save(any());
		verify(imagenRepo, never()).delete(any());
		verify(auditoriaService, org.mockito.Mockito.times(3)).registrar(OTRO, AuditoriaService.IMAGEN_ALOJAMIENTO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.RECHAZADO,
				"Sin permiso sobre el alojamiento");
	}

	@Test
	@DisplayName("Una publicación inexistente responde NO_ENCONTRADO en todas las operaciones")
	void alojamientoInexistente() {
		when(alojamientoRepo.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.actualizar(99L, new AlojamientoDTO(), ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.subirImagenes(99L, List.of(), ANFITRION, false).getMensaje())
				.isEqualTo("El alojamiento no existe.");
		assertThat(servicio.eliminarImagen(99L, 1L, ANFITRION, false).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.marcarPrincipal(99L, 1L, ANFITRION, false).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.agregarServicio(99L, servicio("Desayuno", "1"), ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.actualizarServicio(99L, ID_SERVICIO, servicio("Desayuno", "1"), ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.eliminarServicio(99L, ID_SERVICIO, ANFITRION, false).getCodigo())
				.isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("Si cambia la dirección y no llegan coordenadas, se vuelven a buscar en el mapa")
	void cambioDeDireccionBuscaCoordenadas() {
		when(geocodingService.ubicar("Calle 9", "Bogotá")).thenReturn(Optional.of(new Coordenadas(4.7, -74.1)));
		AlojamientoDTO datos = new AlojamientoDTO();
		datos.setUbicacion("Calle 9");

		AlojamientoDTO dto = servicio.actualizar(ID, datos, ANFITRION, false).getDato();

		assertThat(dto.getLatitud()).isEqualTo(4.7);
		assertThat(dto.getLongitud()).isEqualTo(-74.1);
	}

	@Test
	@DisplayName("No consulta el mapa si la dirección no cambia o si llegan coordenadas")
	void noBuscaCoordenadasSinNecesidad() {
		AlojamientoDTO sinCambio = new AlojamientoDTO();
		sinCambio.setTitulo("Otro título");
		AlojamientoDTO conCoordenadas = new AlojamientoDTO();
		conCoordenadas.setCiudad("Cali");
		conCoordenadas.setLatitud(3.4);
		conCoordenadas.setLongitud(-76.5);

		servicio.actualizar(ID, sinCambio, ANFITRION, false);
		AlojamientoDTO dto = servicio.actualizar(ID, conCoordenadas, ANFITRION, false).getDato();

		assertThat(dto.getLatitud()).isEqualTo(3.4);
		verify(geocodingService, never()).ubicar(any(), any());
	}

	@Test
	@DisplayName("Sube imágenes nuevas; con portada existente la nueva no la reemplaza")
	void subeImagenes() {
		Imagen portada = imagenGuardada(1L, true);
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(portada));
		when(almacenamientoService.errorImagen(any())).thenReturn(null);
		when(imagenRepo.siguienteOrden()).thenReturn(5L);
		when(almacenamientoService.guardarImagen(any(), eq(ID), eq(false), anyLong()))
				.thenReturn(new ImagenSubida("ruta-b", "https://img/b.jpg"));

		Resultado<List<ImagenDTO>> resultado = servicio.subirImagenes(ID, List.of(imagen("b.jpg")), ANFITRION, false);

		assertThat(resultado.esExito()).isTrue();
		ArgumentCaptor<Imagen> guardada = ArgumentCaptor.forClass(Imagen.class);
		verify(imagenRepo).save(guardada.capture());
		assertThat(guardada.getValue().getPrincipal()).isFalse();
		assertThat(guardada.getValue().getIdAlojamiento()).isEqualTo(ID);
		assertThat(guardada.getValue().getUrl()).isEqualTo("https://img/b.jpg");
		verify(alojamientoRepo).save(alojamiento);
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.IMAGEN_ALOJAMIENTO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO, "Subidas: 1");
	}

	@Test
	@DisplayName("No sube imágenes si el lote es inválido o supera el máximo")
	void rechazaImagenes() {
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(java.util.Collections.nCopies(15, imagenGuardada(1L, false)));

		assertThat(servicio.subirImagenes(ID, List.of(imagen("a.jpg")), ANFITRION, false).getMensaje())
				.isEqualTo("Un alojamiento puede tener máximo 15 imágenes.");

		verify(imagenRepo, never()).save(any());
		verify(alojamientoRepo, never()).save(any());
	}

	@Test
	@DisplayName("Al eliminar la portada, la siguiente imagen pasa a ser la principal")
	void eliminaLaPortada() {
		Imagen portada = imagenGuardada(1L, true);
		Imagen segunda = imagenGuardada(2L, false);
		when(imagenRepo.findByIdImagenAndIdAlojamiento(1L, ID)).thenReturn(Optional.of(portada));
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(portada, segunda));

		assertThat(servicio.eliminarImagen(ID, 1L, ANFITRION, false).esExito()).isTrue();

		verify(imagenRepo).delete(portada);
		verify(almacenamientoService).eliminar("ruta-1");
		verify(imagenRepo).save(segunda);
		assertThat(segunda.getPrincipal()).isTrue();
	}

	@Test
	@DisplayName("Eliminar una imagen que no es la portada no cambia la principal")
	void eliminaOtraImagen() {
		Imagen portada = imagenGuardada(1L, true);
		Imagen segunda = imagenGuardada(2L, false);
		when(imagenRepo.findByIdImagenAndIdAlojamiento(2L, ID)).thenReturn(Optional.of(segunda));
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(portada, segunda));

		assertThat(servicio.eliminarImagen(ID, 2L, ANFITRION, false).esExito()).isTrue();

		verify(imagenRepo, never()).save(any());
		assertThat(portada.getPrincipal()).isTrue();
	}

	@Test
	@DisplayName("El alojamiento debe conservar al menos una imagen y no se elimina una que no existe")
	void noEliminaLaUltimaImagen() {
		Imagen unica = imagenGuardada(1L, true);
		when(imagenRepo.findByIdImagenAndIdAlojamiento(1L, ID)).thenReturn(Optional.of(unica));
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(unica));
		when(imagenRepo.findByIdImagenAndIdAlojamiento(8L, ID)).thenReturn(Optional.empty());

		Resultado<List<ImagenDTO>> ultima = servicio.eliminarImagen(ID, 1L, ANFITRION, false);

		assertThat(ultima.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(ultima.getMensaje())
				.isEqualTo("El alojamiento debe conservar al menos una imagen. Sube otra antes de borrar esta.");
		assertThat(servicio.eliminarImagen(ID, 8L, ANFITRION, false).getMensaje()).isEqualTo("La imagen no existe.");
		verify(imagenRepo, never()).delete(any());
		verify(almacenamientoService, never()).eliminar(any());
	}

	@Test
	@DisplayName("Marcar una imagen como principal quita la marca de las demás")
	void marcaPrincipal() {
		Imagen portada = imagenGuardada(1L, true);
		Imagen segunda = imagenGuardada(2L, false);
		when(imagenRepo.findByIdImagenAndIdAlojamiento(2L, ID)).thenReturn(Optional.of(segunda));
		when(imagenRepo.findByIdAlojamiento(ID)).thenReturn(List.of(portada, segunda));

		assertThat(servicio.marcarPrincipal(ID, 2L, ANFITRION, false).esExito()).isTrue();

		assertThat(portada.getPrincipal()).isFalse();
		assertThat(segunda.getPrincipal()).isTrue();
		verify(alojamientoRepo).save(alojamiento);
	}

	@Test
	@DisplayName("No marca como principal una imagen que no es del alojamiento")
	void noMarcaImagenAjena() {
		when(imagenRepo.findByIdImagenAndIdAlojamiento(8L, ID)).thenReturn(Optional.empty());

		Resultado<List<ImagenDTO>> resultado = servicio.marcarPrincipal(ID, 8L, ANFITRION, false);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo("La imagen no existe.");
		verify(imagenRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-185 El propietario registra un servicio adicional con nombre y costo positivo y queda asociado a su publicación")
	void registraServicio_CA185() {
		Resultado<ServicioAdicionalDTO> resultado = servicio.agregarServicio(ID, servicio("  Desayuno ", "20000"),
				ANFITRION, false);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getIdServicio()).isEqualTo(ID_SERVICIO);
		assertThat(resultado.getDato().getIdAlojamiento()).isEqualTo(ID);
		assertThat(resultado.getDato().getNombre()).isEqualTo("Desayuno");
		assertThat(resultado.getDato().getCosto()).isEqualByComparingTo("20000");
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.SERVICIO_ADICIONAL,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO, "Agregado: Desayuno");
	}

	@Test
	@DisplayName("CA-185 El propietario modifica un servicio adicional de su publicación")
	void modificaServicio_CA185() {
		ServicioAdicional existente = servicioGuardado();
		when(servicioRepo.findByIdServicioAndIdAlojamiento(ID_SERVICIO, ID)).thenReturn(Optional.of(existente));

		Resultado<ServicioAdicionalDTO> resultado = servicio.actualizarServicio(ID, ID_SERVICIO,
				servicio("Traslado", "35000"), ANFITRION, false);

		assertThat(resultado.esExito()).isTrue();
		assertThat(existente.getNombre()).isEqualTo("Traslado");
		assertThat(existente.getCosto()).isEqualByComparingTo("35000");
		assertThat(resultado.getDato().getNombre()).isEqualTo("Traslado");
	}

	@Test
	@DisplayName("CA-185 El propietario elimina un servicio adicional de su publicación")
	void eliminaServicio_CA185() {
		ServicioAdicional existente = servicioGuardado();
		when(servicioRepo.findByIdServicioAndIdAlojamiento(ID_SERVICIO, ID)).thenReturn(Optional.of(existente));

		assertThat(servicio.eliminarServicio(ID, ID_SERVICIO, ANFITRION, false).esExito()).isTrue();

		verify(servicioRepo).delete(existente);
		verify(auditoriaService).registrar(ANFITRION, AuditoriaService.SERVICIO_ADICIONAL,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, ID, ResultadoAuditoria.EXITOSO, "Eliminado: Desayuno");
	}

	@Test
	@DisplayName("CA-185 Se rechaza un servicio sin nombre o sin costo positivo dentro del máximo")
	void rechazaServicioInvalido_CA185() {
		assertServicioInvalido(null);
		assertServicioInvalido(servicio(null, "100"));
		assertServicioInvalido(servicio("   ", "100"));
		assertServicioInvalido(servicio("x".repeat(256), "100"));
		assertServicioInvalido(servicio("Desayuno", null));
		assertServicioInvalido(servicio("Desayuno", "0"));
		assertServicioInvalido(servicio("Desayuno", "-1"));
		assertServicioInvalido(servicio("Desayuno", "1000000000.01"));
		verify(servicioRepo, never()).save(any());
	}

	@Test
	@DisplayName("Acepta el nombre de 255 caracteres y el costo máximo")
	void aceptaLosLimitesDelServicio() {
		assertThat(servicio.agregarServicio(ID, servicio("x".repeat(255), "1000000000"), ANFITRION, false).esExito())
				.isTrue();
	}

	@Test
	@DisplayName("Modificar con datos inválidos o un servicio de otro alojamiento no guarda nada")
	void modificarServicioRechazado() {
		when(servicioRepo.findByIdServicioAndIdAlojamiento(ID_SERVICIO, ID)).thenReturn(Optional.of(servicioGuardado()));
		when(servicioRepo.findByIdServicioAndIdAlojamiento(8L, ID)).thenReturn(Optional.empty());

		assertThat(servicio.actualizarServicio(ID, ID_SERVICIO, servicio("Desayuno", "0"), ANFITRION, false).getMensaje())
				.isEqualTo(SERVICIO_INVALIDO);
		Resultado<ServicioAdicionalDTO> ajeno = servicio.actualizarServicio(ID, 8L, servicio("Desayuno", "1"), ANFITRION,
				false);

		assertThat(ajeno.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(ajeno.getMensaje()).isEqualTo("El servicio adicional no existe.");
		assertThat(servicio.eliminarServicio(ID, 8L, ANFITRION, false).getMensaje())
				.isEqualTo("El servicio adicional no existe.");
		verify(servicioRepo, never()).save(any());
		verify(servicioRepo, never()).delete(any());
	}

	@Test
	@DisplayName("La publicación muestra sus servicios adicionales vigentes")
	void muestraServicios() {
		when(servicioRepo.findByIdAlojamientoOrderByIdServicio(ID)).thenReturn(List.of(servicioGuardado()));

		AlojamientoDTO dto = servicio.convertirDTO(alojamiento);

		assertThat(dto.getServiciosAdicionales()).singleElement().satisfies(s -> {
			assertThat(s.getIdServicio()).isEqualTo(ID_SERVICIO);
			assertThat(s.getNombre()).isEqualTo("Desayuno");
			assertThat(s.getCosto()).isEqualByComparingTo("20000");
			assertThat(s.getIdAlojamiento()).isEqualTo(ID);
		});
	}

	/**
	 * Revisa que la operación se haya rechazado por falta de permisos.
	 *
	 * @param resultado resultado de la operación
	 */
	private static void assertSinPermiso(Resultado<?> resultado) {
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(resultado.getMensaje()).isEqualTo(SIN_PERMISO);
	}

	/**
	 * Revisa que el servicio se rechace por datos inválidos.
	 *
	 * @param datos datos del servicio
	 */
	private void assertServicioInvalido(ServicioAdicionalDTO datos) {
		Resultado<ServicioAdicionalDTO> resultado = servicio.agregarServicio(ID, datos, ANFITRION, false);
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(resultado.getMensaje()).isEqualTo(SERVICIO_INVALIDO);
	}

	/**
	 * Datos de un servicio adicional.
	 *
	 * @param nombre nombre del servicio
	 * @param costo  costo; puede ser {@code null}
	 * @return datos del servicio
	 */
	private static ServicioAdicionalDTO servicio(String nombre, String costo) {
		ServicioAdicionalDTO datos = new ServicioAdicionalDTO();
		datos.setNombre(nombre);
		datos.setCosto(costo == null ? null : new BigDecimal(costo));
		return datos;
	}

	/**
	 * Servicio adicional ya guardado en la publicación de prueba.
	 *
	 * @return servicio
	 */
	private static ServicioAdicional servicioGuardado() {
		ServicioAdicional existente = new ServicioAdicional();
		existente.setIdServicio(ID_SERVICIO);
		existente.setIdAlojamiento(ID);
		existente.setNombre("Desayuno");
		existente.setCosto(new BigDecimal("20000"));
		return existente;
	}

	/**
	 * Imagen ya guardada en la publicación de prueba.
	 *
	 * @param id        id de la imagen
	 * @param principal si es la portada
	 * @return imagen
	 */
	private static Imagen imagenGuardada(Long id, boolean principal) {
		Imagen imagen = new Imagen();
		imagen.setIdImagen(id);
		imagen.setIdAlojamiento(ID);
		imagen.setRutaArchivo("ruta-" + id);
		imagen.setUrl("https://img/" + id);
		imagen.setPrincipal(principal);
		imagen.setOrden(id);
		return imagen;
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
