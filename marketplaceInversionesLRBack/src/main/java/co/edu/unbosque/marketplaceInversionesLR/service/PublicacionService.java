package co.edu.unbosque.marketplaceInversionesLR.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.HistorialEstadoAlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ImagenDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PoliticaCancelacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;
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
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Publicaciones de alojamiento: creación en BORRADOR con sus imágenes (HU-15),
 * edición, imágenes y servicios adicionales (HU-16), validación de los datos y
 * de la información mínima para enviar a revisión, y políticas de cancelación
 * disponibles.
 * <p>
 * Cada cambio de estado guarda un {@link HistorialEstadoAlojamiento}.
 * </p>
 */
@Service
public class PublicacionService {

	/** Mensaje cuando el alojamiento no existe. */
	private static final String NO_EXISTE = "El alojamiento no existe.";

	/** Mensaje cuando el usuario no es el dueño ni administrador. */
	private static final String SIN_PERMISO = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.";

	/** Mensaje cuando un servicio adicional no es válido. */
	private static final String SERVICIO_INVALIDO = "El servicio adicional necesita un nombre (máximo 255 "
			+ "caracteres) y un costo positivo de máximo 1.000.000.000.";

	/** Mensaje cuando el motivo del rechazo falta o es muy largo (HU-32). */
	public static final String MOTIVO_INVALIDO = "El motivo del rechazo es obligatorio y puede tener máximo "
			+ LimitesTexto.MOTIVO + " caracteres.";

	/** Mensaje cuando se aprueba un alojamiento que ya no tiene fechas (HU-32). */
	public static final String SIN_FECHAS = "El alojamiento no tiene fechas de disponibilidad desde hoy; el "
			+ "anfitrión debe agregarlas antes de publicarlo.";

	/** Motivo que queda en el historial al aprobar. */
	public static final String APROBADA = "Aprobada";

	/** Prefijo del motivo que queda en el historial al rechazar. */
	public static final String RECHAZADA = "Rechazada: ";

	/** Valor absoluto máximo de la latitud. */
	private static final int LATITUD_MAXIMA = 90;

	/** Valor absoluto máximo de la longitud. */
	private static final int LONGITUD_MAXIMA = 180;

	/** Tope del precio por noche y de cada servicio adicional (columnas NUMERIC). */
	private static final BigDecimal MONTO_MAXIMO = new BigDecimal("1000000000");

	/** Máximo de imágenes por alojamiento. */
	public static final int MAXIMO_IMAGENES = 15;

	/** Repositorio de alojamientos. */
	private final AlojamientoRepository alojamientoRepo;

	/** Almacén de imágenes. */
	private final ImagenRepository imagenRepo;

	/** Repositorio de períodos de disponibilidad. */
	private final PeriodoDisponibilidadRepository periodoRepo;

	/** Repositorio de servicios adicionales. */
	private final ServicioAdicionalRepository servicioRepo;

	/** Repositorio de políticas de cancelación. */
	private final PoliticaCancelacionRepository politicaRepo;

	/** Historial de estados. */
	private final HistorialEstadoAlojamientoRepository historialRepo;

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Valida y guarda las imágenes en Cloudinary. */
	private final AlmacenamientoService almacenamientoService;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/** Busca las coordenadas de la dirección. */
	private final GeocodingService geocodingService;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/** Avisa al anfitrión las decisiones de revisión (HU-32). */
	private final NotificacionService notificacionService;

	/**
	 * Crea el servicio.
	 *
	 * @param alojamientoRepo       repositorio de alojamientos
	 * @param imagenRepo            almacén de imágenes
	 * @param periodoRepo           repositorio de períodos
	 * @param servicioRepo          repositorio de servicios adicionales
	 * @param politicaRepo          repositorio de políticas
	 * @param historialRepo         historial de estados
	 * @param usuarioRepo           repositorio de usuarios
	 * @param almacenamientoService servicio de almacenamiento de imágenes
	 * @param auditoriaService      servicio de auditoría
	 * @param geocodingService      servicio de coordenadas
	 * @param notificacionService   avisos al anfitrión
	 * @param modelMapper           copiador de entidades a DTO
	 */
	public PublicacionService(AlojamientoRepository alojamientoRepo, ImagenRepository imagenRepo,
			PeriodoDisponibilidadRepository periodoRepo, ServicioAdicionalRepository servicioRepo,
			PoliticaCancelacionRepository politicaRepo, HistorialEstadoAlojamientoRepository historialRepo,
			UsuarioRepository usuarioRepo,
			AlmacenamientoService almacenamientoService, AuditoriaService auditoriaService,
			GeocodingService geocodingService, NotificacionService notificacionService, ModelMapper modelMapper) {
		this.notificacionService = notificacionService;
		this.alojamientoRepo = alojamientoRepo;
		this.imagenRepo = imagenRepo;
		this.periodoRepo = periodoRepo;
		this.servicioRepo = servicioRepo;
		this.politicaRepo = politicaRepo;
		this.historialRepo = historialRepo;
		this.usuarioRepo = usuarioRepo;
		this.almacenamientoService = almacenamientoService;
		this.auditoriaService = auditoriaService;
		this.geocodingService = geocodingService;
		this.modelMapper = modelMapper;
	}

	/**
	 * Políticas de cancelación disponibles para asignar a un alojamiento.
	 *
	 * @return lista de políticas
	 */
	public List<PoliticaCancelacionDTO> politicas() {
		return politicaRepo.findAll().stream().map(p -> modelMapper.map(p, PoliticaCancelacionDTO.class)).toList();
	}

	/**
	 * Historial de estados de un alojamiento (lo ven el dueño o un
	 * administrador).
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       usuario que consulta
	 * @param esAdmin       {@code true} si es administrador
	 * @return los cambios de estado; o NO_ENCONTRADO si no existe o el usuario
	 *         no puede verlo
	 */
	public Resultado<List<HistorialEstadoAlojamientoDTO>> historial(Long idAlojamiento, Long idActor,
			boolean esAdmin) {
		Optional<Alojamiento> alojamiento = alojamientoRepo.findById(idAlojamiento);
		if (alojamiento.isEmpty() || !(esAdmin || esPropietario(alojamiento.get(), idActor))) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		return Resultado.exito(historialRepo.findByIdAlojamientoOrderByFechaAscIdHistorialAsc(idAlojamiento).stream()
				.map(h -> modelMapper.map(h, HistorialEstadoAlojamientoDTO.class)).toList());
	}

	/**
	 * Crea una publicación en BORRADOR con sus imágenes (CA-094). Solo el
	 * título es obligatorio en el borrador, pero debe llegar con al menos una
	 * imagen; la primera queda como portada. El id, el estado y el dueño
	 * enviados se ignoran. Si no llegan coordenadas, se buscan con la dirección
	 * y la ciudad ({@link GeocodingService}).
	 *
	 * @param datos       datos del alojamiento
	 * @param archivos    imágenes (JPG, PNG o WEBP de hasta 5 MB), entre 1 y
	 *                    {@link #MAXIMO_IMAGENES}
	 * @param idAnfitrion anfitrión autenticado
	 * @return la publicación; o DATOS_INVALIDOS si los datos o las imágenes no
	 *         son válidos
	 */
	@Transactional
	public Resultado<AlojamientoDTO> crear(AlojamientoDTO datos, List<MultipartFile> archivos, Long idAnfitrion) {
		String error = validar(datos, true);
		if (error == null) {
			error = errorArchivos(archivos, 0);
		}
		if (error != null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, error);
		}

		Alojamiento alojamiento = new Alojamiento();
		aplicar(datos, alojamiento);
		if (sinCoordenadas(datos)) {
			ubicarEnMapa(alojamiento);
		}
		alojamiento.setIdAnfitrion(idAnfitrion);
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);
		alojamiento.setFechaCreacion(LocalDateTime.now());
		alojamiento.setFechaActualizacion(alojamiento.getFechaCreacion());
		alojamiento = alojamientoRepo.save(alojamiento);
		guardarArchivos(alojamiento.getIdAlojamiento(), archivos, false);
		registrarHistorial(alojamiento.getIdAlojamiento(), null, EstadoAlojamiento.BORRADOR, idAnfitrion, null);
		return Resultado.exito(convertirDTO(alojamiento));
	}

	/**
	 * BORRADOR → PENDIENTE_REVISION (anfitrión). Exige la información mínima
	 * completa e indica los datos que faltan. Que falten datos es un conflicto
	 * con el estado del borrador, no un error de la petición (que no trae
	 * cuerpo).
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       anfitrión
	 * @return la publicación; NO_ENCONTRADO; SIN_PERMISO; ESTADO_INVALIDO si el
	 *         estado no lo permite o faltan datos
	 */
	@Transactional
	public Resultado<AlojamientoDTO> enviarRevision(Long idAlojamiento, Long idActor) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, false,
				AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		Alojamiento alojamiento = carga.getDato();
		if (alojamiento.getEstado() != EstadoAlojamiento.BORRADOR) {
			return rechazarTransicion(alojamiento, EstadoAlojamiento.PENDIENTE_REVISION, idActor);
		}
		List<String> faltantes = camposFaltantes(alojamiento);
		if (!faltantes.isEmpty()) {
			String mensaje = "Completa los datos antes de enviar a revisión. Falta: " + String.join(", ", faltantes)
					+ ".";
			auditoriaService.registrar(idActor, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
					AuditoriaService.ENTIDAD_ALOJAMIENTO, idAlojamiento, ResultadoAuditoria.RECHAZADO, mensaje);
			return Resultado.error(Resultado.ESTADO_INVALIDO, mensaje);
		}
		return Resultado.exito(cambiarEstado(alojamiento, EstadoAlojamiento.PENDIENTE_REVISION, idActor, null));
	}

	/**
	 * ACTIVA → PAUSADA (anfitrión). Una publicación pausada no recibe
	 * reservas.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       anfitrión
	 * @return la publicación; NO_ENCONTRADO, SIN_PERMISO o ESTADO_INVALIDO
	 */
	@Transactional
	public Resultado<AlojamientoDTO> pausar(Long idAlojamiento, Long idActor) {
		return transicionDelAnfitrion(idAlojamiento, idActor, EstadoAlojamiento.ACTIVA, EstadoAlojamiento.PAUSADA);
	}

	/**
	 * PAUSADA → ACTIVA (anfitrión).
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       anfitrión
	 * @return la publicación; NO_ENCONTRADO, SIN_PERMISO o ESTADO_INVALIDO
	 */
	@Transactional
	public Resultado<AlojamientoDTO> reanudar(Long idAlojamiento, Long idActor) {
		return transicionDelAnfitrion(idAlojamiento, idActor, EstadoAlojamiento.PAUSADA, EstadoAlojamiento.ACTIVA);
	}

	/**
	 * Alojamientos de un estado, por ejemplo la cola PENDIENTE_REVISION; sin
	 * estado, todos (vista del administrador, HU-32).
	 *
	 * @param estado estado a filtrar; {@code null} para todos
	 * @param pagina página y orden
	 * @return página de alojamientos
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<AlojamientoDTO> listarPorEstado(EstadoAlojamiento estado, Pageable pagina) {
		Page<Alojamiento> encontrados = estado == null ? alojamientoRepo.findAll(pagina)
				: alojamientoRepo.findByEstado(estado, pagina);
		return PaginaDTO.de(encontrados, this::convertirDTO);
	}

	/**
	 * PENDIENTE_REVISION → ACTIVA (administrador, HU-32): queda disponible para
	 * reservas y visible en las búsquedas, y se le avisa al anfitrión (CA-158).
	 * La decisión queda en el historial y en la auditoría (CA-196).
	 *
	 * @param idAlojamiento alojamiento
	 * @param idAdmin       administrador
	 * @return la publicación ACTIVA; NO_ENCONTRADO; ESTADO_INVALIDO si no está
	 *         en revisión o ya no tiene fechas de disponibilidad desde hoy
	 */
	@Transactional
	public Resultado<AlojamientoDTO> aprobar(Long idAlojamiento, Long idAdmin) {
		Optional<Alojamiento> encontrado = alojamientoRepo.findById(idAlojamiento);
		if (encontrado.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Alojamiento alojamiento = encontrado.get();
		if (alojamiento.getEstado() != EstadoAlojamiento.PENDIENTE_REVISION) {
			return rechazarTransicion(alojamiento, EstadoAlojamiento.ACTIVA, idAdmin);
		}
		// El anfitrión pudo borrar sus fechas mientras estaba en revisión
		if (!tieneFechas(alojamiento)) {
			auditoriaService.registrar(idAdmin, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
					AuditoriaService.ENTIDAD_ALOJAMIENTO, idAlojamiento, ResultadoAuditoria.RECHAZADO, SIN_FECHAS);
			return Resultado.error(Resultado.ESTADO_INVALIDO, SIN_FECHAS);
		}
		AlojamientoDTO dto = cambiarEstado(alojamiento, EstadoAlojamiento.ACTIVA, idAdmin, APROBADA);
		notificacionService.enviar(alojamiento.getIdAnfitrion(), NotificacionService.ALOJAMIENTO_APROBADO,
				"Tu alojamiento \"" + alojamiento.getTitulo() + "\" fue aprobado y ya está publicado.");
		return Resultado.exito(dto);
	}

	/**
	 * PENDIENTE_REVISION → BORRADOR (administrador, HU-32), con motivo
	 * obligatorio. El motivo queda en el historial y se le avisa al anfitrión
	 * para que corrija y la vuelva a enviar (CA-195). La decisión queda en la
	 * auditoría (CA-196).
	 *
	 * @param idAlojamiento alojamiento
	 * @param idAdmin       administrador
	 * @param motivo        motivo del rechazo
	 * @return la publicación en BORRADOR; DATOS_INVALIDOS si falta el motivo o
	 *         es muy largo; NO_ENCONTRADO; ESTADO_INVALIDO si no está en revisión
	 */
	@Transactional
	public Resultado<AlojamientoDTO> rechazar(Long idAlojamiento, Long idAdmin, String motivo) {
		if (motivo == null || motivo.isBlank() || LimitesTexto.excede(motivo, LimitesTexto.MOTIVO)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, MOTIVO_INVALIDO);
		}
		Optional<Alojamiento> encontrado = alojamientoRepo.findById(idAlojamiento);
		if (encontrado.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		Alojamiento alojamiento = encontrado.get();
		if (alojamiento.getEstado() != EstadoAlojamiento.PENDIENTE_REVISION) {
			return rechazarTransicion(alojamiento, EstadoAlojamiento.BORRADOR, idAdmin);
		}
		String limpio = motivo.trim();
		AlojamientoDTO dto = cambiarEstado(alojamiento, EstadoAlojamiento.BORRADOR, idAdmin, RECHAZADA + limpio);
		notificacionService.enviar(alojamiento.getIdAnfitrion(), NotificacionService.ALOJAMIENTO_RECHAZADO,
				"Tu alojamiento \"" + alojamiento.getTitulo() + "\" fue rechazado. Motivo: " + limpio
						+ ". Corrígelo y vuelve a enviarlo a revisión.");
		return Resultado.exito(dto);
	}

	/**
	 * Edita una publicación (dueño o administrador). Los
	 * campos en {@code null} se dejan como están y las amenidades enviadas
	 * reemplazan a las anteriores. El estado, el dueño y las imágenes no se
	 * cambian por esta vía: las imágenes se manejan con {@link #subirImagenes},
	 * {@link #eliminarImagen} y {@link #marcarPrincipal}. Si cambian la
	 * dirección o la ciudad y no llegan coordenadas, se vuelven a buscar.
	 *
	 * @param idAlojamiento alojamiento
	 * @param datos         datos nuevos
	 * @param idActor       usuario que edita
	 * @param esAdmin       {@code true} si es administrador
	 * @return la publicación; NO_ENCONTRADO, DATOS_INVALIDOS o SIN_PERMISO
	 */
	@Transactional
	public Resultado<AlojamientoDTO> actualizar(Long idAlojamiento, AlojamientoDTO datos, Long idActor,
			boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.ALOJAMIENTO_ACTUALIZADO);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		String error = validar(datos, false);
		if (error != null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, error);
		}

		Alojamiento alojamiento = carga.getDato();
		String direccionAnterior = alojamiento.getUbicacion();
		String ciudadAnterior = alojamiento.getCiudad();
		aplicar(datos, alojamiento);
		boolean cambioDireccion = !Objects.equals(direccionAnterior, alojamiento.getUbicacion())
				|| !Objects.equals(ciudadAnterior, alojamiento.getCiudad());
		if (cambioDireccion && sinCoordenadas(datos)) {
			ubicarEnMapa(alojamiento);
		}
		alojamiento.setFechaActualizacion(LocalDateTime.now());
		alojamiento = alojamientoRepo.save(alojamiento);

		auditoriaService.registrar(idActor, AuditoriaService.ALOJAMIENTO_ACTUALIZADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, idAlojamiento, ResultadoAuditoria.EXITOSO, null);
		return Resultado.exito(convertirDTO(alojamiento));
	}

	/**
	 * Sube una o varias imágenes (JPG, PNG o WEBP de hasta 5 MB) al
	 * alojamiento. Se validan todas antes de guardar alguna. Si el alojamiento
	 * no tenía portada, la primera imagen subida queda como principal.
	 *
	 * @param idAlojamiento alojamiento
	 * @param archivos      imágenes a subir
	 * @param idActor       usuario que sube
	 * @param esAdmin       {@code true} si es administrador
	 * @return todas las imágenes del alojamiento (la principal primero);
	 *         NO_ENCONTRADO; DATOS_INVALIDOS si los archivos no sirven o se
	 *         supera el máximo; SIN_PERMISO
	 */
	@Transactional
	public Resultado<List<ImagenDTO>> subirImagenes(Long idAlojamiento, List<MultipartFile> archivos, Long idActor,
			boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.IMAGEN_ALOJAMIENTO);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		List<Imagen> actuales = imagenRepo.findByIdAlojamiento(idAlojamiento);
		String error = errorArchivos(archivos, actuales.size());
		if (error != null) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, error);
		}

		guardarArchivos(idAlojamiento, archivos,
				actuales.stream().anyMatch(i -> Boolean.TRUE.equals(i.getPrincipal())));
		marcarActualizado(carga.getDato());
		auditoriaService.registrar(idActor, AuditoriaService.IMAGEN_ALOJAMIENTO, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, ResultadoAuditoria.EXITOSO, "Subidas: " + archivos.size());
		return Resultado.exito(imagenesDTO(idAlojamiento));
	}

	/**
	 * Elimina una imagen (y su archivo, si fue subido). Si era la portada, la
	 * siguiente pasa a serlo. Todo alojamiento, también en BORRADOR, debe
	 * conservar al menos una imagen.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idImagen      imagen a eliminar
	 * @param idActor       usuario que elimina
	 * @param esAdmin       {@code true} si es administrador
	 * @return las imágenes restantes; NO_ENCONTRADO; SIN_PERMISO;
	 *         ESTADO_INVALIDO si es la última imagen
	 */
	@Transactional
	public Resultado<List<ImagenDTO>> eliminarImagen(Long idAlojamiento, Long idImagen, Long idActor,
			boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.IMAGEN_ALOJAMIENTO);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		Optional<Imagen> imagen = imagenRepo.findByIdImagenAndIdAlojamiento(idImagen, idAlojamiento);
		if (imagen.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, "La imagen no existe.");
		}
		List<Imagen> actuales = imagenRepo.findByIdAlojamiento(idAlojamiento);
		if (actuales.size() == 1) {
			return Resultado.error(Resultado.ESTADO_INVALIDO,
					"El alojamiento debe conservar al menos una imagen. Sube otra antes de borrar esta.");
		}

		imagenRepo.delete(imagen.get());
		almacenamientoService.eliminar(imagen.get().getRutaArchivo());
		if (Boolean.TRUE.equals(imagen.get().getPrincipal())) {
			actuales.stream().filter(i -> !i.getIdImagen().equals(idImagen)).findFirst().ifPresent(siguiente -> {
				siguiente.setPrincipal(true);
				imagenRepo.save(siguiente);
			});
		}
		marcarActualizado(carga.getDato());
		auditoriaService.registrar(idActor, AuditoriaService.IMAGEN_ALOJAMIENTO, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, ResultadoAuditoria.EXITOSO, "Eliminada la imagen " + idImagen);
		return Resultado.exito(imagenesDTO(idAlojamiento));
	}

	/**
	 * Marca una imagen como portada; las demás dejan de serlo.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idImagen      imagen a marcar
	 * @param idActor       usuario que la marca
	 * @param esAdmin       {@code true} si es administrador
	 * @return las imágenes (la principal primero); NO_ENCONTRADO o SIN_PERMISO
	 */
	@Transactional
	public Resultado<List<ImagenDTO>> marcarPrincipal(Long idAlojamiento, Long idImagen, Long idActor,
			boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.IMAGEN_ALOJAMIENTO);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		if (imagenRepo.findByIdImagenAndIdAlojamiento(idImagen, idAlojamiento).isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, "La imagen no existe.");
		}
		for (Imagen imagen : imagenRepo.findByIdAlojamiento(idAlojamiento)) {
			imagen.setPrincipal(imagen.getIdImagen().equals(idImagen));
			imagenRepo.save(imagen);
		}
		marcarActualizado(carga.getDato());
		auditoriaService.registrar(idActor, AuditoriaService.IMAGEN_ALOJAMIENTO, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, ResultadoAuditoria.EXITOSO, "Principal: imagen " + idImagen);
		return Resultado.exito(imagenesDTO(idAlojamiento));
	}

	/**
	 * Agrega un servicio adicional con nombre y costo positivo.
	 *
	 * @param idAlojamiento alojamiento
	 * @param datos         nombre y costo
	 * @param idActor       usuario que lo agrega
	 * @param esAdmin       {@code true} si es administrador
	 * @return el servicio; NO_ENCONTRADO, DATOS_INVALIDOS o SIN_PERMISO
	 */
	@Transactional
	public Resultado<ServicioAdicionalDTO> agregarServicio(Long idAlojamiento, ServicioAdicionalDTO datos,
			Long idActor, boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.SERVICIO_ADICIONAL);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		if (!servicioValido(datos)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, SERVICIO_INVALIDO);
		}
		ServicioAdicional servicio = new ServicioAdicional();
		servicio.setIdAlojamiento(idAlojamiento);
		servicio.setNombre(datos.getNombre().trim());
		servicio.setCosto(datos.getCosto());
		servicio = servicioRepo.save(servicio);
		auditoriaService.registrar(idActor, AuditoriaService.SERVICIO_ADICIONAL, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, ResultadoAuditoria.EXITOSO, "Agregado: " + servicio.getNombre());
		return Resultado.exito(modelMapper.map(servicio, ServicioAdicionalDTO.class));
	}

	/**
	 * Modifica un servicio adicional. El cambio aplica a las cotizaciones
	 * nuevas; las reservas ya confirmadas conservan su desglose congelado.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idServicio    servicio
	 * @param datos         nombre y costo nuevos
	 * @param idActor       usuario que lo modifica
	 * @param esAdmin       {@code true} si es administrador
	 * @return el servicio; NO_ENCONTRADO, DATOS_INVALIDOS o SIN_PERMISO
	 */
	@Transactional
	public Resultado<ServicioAdicionalDTO> actualizarServicio(Long idAlojamiento, Long idServicio,
			ServicioAdicionalDTO datos, Long idActor, boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.SERVICIO_ADICIONAL);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		Optional<ServicioAdicional> servicio = servicioRepo.findByIdServicioAndIdAlojamiento(idServicio,
				idAlojamiento);
		if (servicio.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, "El servicio adicional no existe.");
		}
		if (!servicioValido(datos)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, SERVICIO_INVALIDO);
		}
		servicio.get().setNombre(datos.getNombre().trim());
		servicio.get().setCosto(datos.getCosto());
		ServicioAdicional guardado = servicioRepo.save(servicio.get());
		auditoriaService.registrar(idActor, AuditoriaService.SERVICIO_ADICIONAL, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, ResultadoAuditoria.EXITOSO, "Modificado: " + guardado.getNombre());
		return Resultado.exito(modelMapper.map(guardado, ServicioAdicionalDTO.class));
	}

	/**
	 * Elimina un servicio adicional; las reservas confirmadas no cambian.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idServicio    servicio
	 * @param idActor       usuario que lo elimina
	 * @param esAdmin       {@code true} si es administrador
	 * @return éxito; NO_ENCONTRADO o SIN_PERMISO
	 */
	@Transactional
	public Resultado<Void> eliminarServicio(Long idAlojamiento, Long idServicio, Long idActor, boolean esAdmin) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, esAdmin,
				AuditoriaService.SERVICIO_ADICIONAL);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		Optional<ServicioAdicional> servicio = servicioRepo.findByIdServicioAndIdAlojamiento(idServicio,
				idAlojamiento);
		if (servicio.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, "El servicio adicional no existe.");
		}
		servicioRepo.delete(servicio.get());
		auditoriaService.registrar(idActor, AuditoriaService.SERVICIO_ADICIONAL, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, ResultadoAuditoria.EXITOSO, "Eliminado: " + servicio.get().getNombre());
		return Resultado.exito(null);
	}

	/**
	 * Datos mínimos para enviar a revisión (CA-097): título, descripción,
	 * ubicación, ciudad, tipo, capacidad, habitaciones, baños, horas de entrada
	 * y salida, precio, reglas, política de cancelación, al menos una imagen y
	 * fechas de disponibilidad desde hoy (sin ellas no saldría en el catálogo).
	 *
	 * @param alojamiento alojamiento a revisar
	 * @return nombres de los datos que faltan (vacía si está completo)
	 */
	public List<String> camposFaltantes(Alojamiento alojamiento) {
		List<String> faltantes = new ArrayList<>();
		agregarSiFalta(faltantes, alojamiento.getTitulo(), "título");
		agregarSiFalta(faltantes, alojamiento.getDescripcion(), "descripción");
		agregarSiFalta(faltantes, alojamiento.getUbicacion(), "ubicación");
		agregarSiFalta(faltantes, alojamiento.getCiudad(), "ciudad");
		agregarSiFalta(faltantes, alojamiento.getTipo(), "tipo");
		agregarSiFalta(faltantes, alojamiento.getReglas(), "reglas");
		if (alojamiento.getCapacidad() == null) {
			faltantes.add("capacidad");
		}
		if (alojamiento.getHabitaciones() == null) {
			faltantes.add("habitaciones");
		}
		if (alojamiento.getBanos() == null) {
			faltantes.add("baños");
		}
		if (alojamiento.getHoraEntrada() == null) {
			faltantes.add("hora de entrada");
		}
		if (alojamiento.getHoraSalida() == null) {
			faltantes.add("hora de salida");
		}
		if (alojamiento.getPrecioNoche() == null) {
			faltantes.add("precio por noche");
		}
		if (alojamiento.getIdPolitica() == null) {
			faltantes.add("política de cancelación");
		}
		if (imagenRepo.findByIdAlojamiento(alojamiento.getIdAlojamiento()).isEmpty()) {
			faltantes.add("al menos una imagen");
		}
		if (!tieneFechas(alojamiento)) {
			faltantes.add("fechas de disponibilidad desde hoy");
		}
		return faltantes;
	}

	/**
	 * Indica si tiene fechas para reservar.
	 *
	 * @param alojamiento alojamiento
	 * @return {@code true} si tiene al menos una noche ofrecida desde hoy
	 */
	private boolean tieneFechas(Alojamiento alojamiento) {
		return periodoRepo.existsByIdAlojamientoAndFechaFinAfter(alojamiento.getIdAlojamiento(), LocalDate.now());
	}

	/**
	 * Detalle de un alojamiento. Si está ACTIVA lo ve cualquiera; en otro
	 * estado solo su anfitrión o un administrador.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       usuario que consulta; {@code null} si no hay sesión
	 * @param esAdmin       {@code true} si es administrador
	 * @return el alojamiento; o NO_ENCONTRADO si no existe o el usuario no
	 *         puede verlo
	 */
	public Resultado<AlojamientoDTO> obtener(Long idAlojamiento, Long idActor, boolean esAdmin) {
		Optional<Alojamiento> alojamiento = alojamientoRepo.findById(idAlojamiento);
		if (alojamiento.isEmpty() || !esVisible(alojamiento.get(), idActor, esAdmin)) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		return Resultado.exito(convertirDTO(alojamiento.get()));
	}

	/**
	 * Convierte la entidad a DTO con las imágenes (la principal primero), los
	 * servicios adicionales, el nombre de la política y el nombre del
	 * anfitrión.
	 *
	 * @param alojamiento alojamiento
	 * @return alojamiento para la API
	 */
	public AlojamientoDTO convertirDTO(Alojamiento alojamiento) {
		AlojamientoDTO dto = modelMapper.map(alojamiento, AlojamientoDTO.class);
		dto.setAmenidades(new HashSet<>(alojamiento.getAmenidades()));
		List<ImagenDTO> imagenes = imagenesDTO(alojamiento.getIdAlojamiento());
		dto.setDetalleImagenes(imagenes);
		dto.setImagenes(imagenes.stream().map(ImagenDTO::getUrl).toList());
		dto.setServiciosAdicionales(servicioRepo.findByIdAlojamientoOrderByIdServicio(alojamiento.getIdAlojamiento())
				.stream().map(s -> modelMapper.map(s, ServicioAdicionalDTO.class)).toList());
		if (alojamiento.getIdPolitica() != null) {
			politicaRepo.findById(alojamiento.getIdPolitica()).ifPresent(p -> dto.setNombrePolitica(p.getNombre()));
		}
		usuarioRepo.findById(alojamiento.getIdAnfitrion()).ifPresent(u -> dto.setNombreAnfitrion(u.getNombre()));
		return dto;
	}

	/**
	 * Imágenes del alojamiento con la principal primero y luego en orden de
	 * carga.
	 *
	 * @param idAlojamiento alojamiento
	 * @return lista de imágenes
	 */
	public List<ImagenDTO> imagenesDTO(Long idAlojamiento) {
		return imagenRepo.findByIdAlojamiento(idAlojamiento).stream()
				.sorted(Comparator.comparing((Imagen i) -> !Boolean.TRUE.equals(i.getPrincipal())))
				.map(i -> modelMapper.map(i, ImagenDTO.class)).toList();
	}

	/**
	 * Indica si el usuario puede ver el alojamiento: si está ACTIVA, si es
	 * administrador o si es el dueño.
	 *
	 * @param alojamiento alojamiento
	 * @param idActor     usuario; puede ser {@code null}
	 * @param esAdmin     {@code true} si es administrador
	 * @return {@code true} si lo puede ver
	 */
	private static boolean esVisible(Alojamiento alojamiento, Long idActor, boolean esAdmin) {
		return alojamiento.getEstado() == EstadoAlojamiento.ACTIVA || esAdmin || esPropietario(alojamiento, idActor);
	}

	/**
	 * Indica si el usuario es el dueño del alojamiento.
	 *
	 * @param alojamiento alojamiento
	 * @param idActor     usuario; puede ser {@code null}
	 * @return {@code true} si es el dueño
	 */
	private static boolean esPropietario(Alojamiento alojamiento, Long idActor) {
		return idActor != null && idActor.equals(alojamiento.getIdAnfitrion());
	}

	/**
	 * Carga el alojamiento si el usuario puede modificarlo; si no, registra el
	 * intento en la auditoría.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       usuario
	 * @param esAdmin       {@code true} si es administrador
	 * @param accion        acción que se audita si se rechaza
	 * @return el alojamiento; NO_ENCONTRADO o SIN_PERMISO
	 */
	private Resultado<Alojamiento> cargarModificable(Long idAlojamiento, Long idActor, boolean esAdmin,
			String accion) {
		Optional<Alojamiento> alojamiento = alojamientoRepo.findById(idAlojamiento);
		if (alojamiento.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_EXISTE);
		}
		if (!esAdmin && !esPropietario(alojamiento.get(), idActor)) {
			auditoriaService.registrar(idActor, accion, AuditoriaService.ENTIDAD_ALOJAMIENTO, idAlojamiento,
					ResultadoAuditoria.RECHAZADO, "Sin permiso sobre el alojamiento");
			return Resultado.error(Resultado.SIN_PERMISO, SIN_PERMISO);
		}
		return Resultado.exito(alojamiento.get());
	}

	/**
	 * Cambio de estado que hace el anfitrión: verifica que sea el dueño y que
	 * el estado actual sea el esperado.
	 *
	 * @param idAlojamiento alojamiento
	 * @param idActor       anfitrión
	 * @param desde         estado en que debe estar
	 * @param hacia         estado al que pasa
	 * @return la publicación; o el error correspondiente
	 */
	private Resultado<AlojamientoDTO> transicionDelAnfitrion(Long idAlojamiento, Long idActor,
			EstadoAlojamiento desde, EstadoAlojamiento hacia) {
		Resultado<Alojamiento> carga = cargarModificable(idAlojamiento, idActor, false,
				AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO);
		if (!carga.esExito()) {
			return Resultado.error(carga.getCodigo(), carga.getMensaje());
		}
		if (carga.getDato().getEstado() != desde) {
			return rechazarTransicion(carga.getDato(), hacia, idActor);
		}
		return Resultado.exito(cambiarEstado(carga.getDato(), hacia, idActor, null));
	}

	/**
	 * Cambia el estado del alojamiento, guarda el historial y lo registra en
	 * la auditoría.
	 *
	 * @param alojamiento alojamiento
	 * @param nuevo       estado nuevo
	 * @param idActor     usuario que hace el cambio
	 * @param motivo      motivo; puede ser {@code null}
	 * @return la publicación con el estado nuevo
	 */
	private AlojamientoDTO cambiarEstado(Alojamiento alojamiento, EstadoAlojamiento nuevo, Long idActor,
			String motivo) {
		EstadoAlojamiento anterior = alojamiento.getEstado();
		alojamiento.setEstado(nuevo);
		Alojamiento guardado = alojamientoRepo.save(alojamiento);
		registrarHistorial(guardado.getIdAlojamiento(), anterior, nuevo, idActor, motivo);
		auditoriaService.registrar(idActor, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, guardado.getIdAlojamiento(), ResultadoAuditoria.EXITOSO,
				anterior + " -> " + nuevo + (motivo == null ? "" : ". " + motivo));
		return convertirDTO(guardado);
	}

	/**
	 * Registra en la auditoría un cambio de estado no permitido y arma el
	 * error.
	 *
	 * @param alojamiento alojamiento
	 * @param destino     estado pedido
	 * @param idActor     usuario que lo pidió
	 * @return resultado con ESTADO_INVALIDO
	 */
	private Resultado<AlojamientoDTO> rechazarTransicion(Alojamiento alojamiento, EstadoAlojamiento destino,
			Long idActor) {
		String mensaje = "No se puede pasar de " + alojamiento.getEstado() + " a " + destino + ".";
		auditoriaService.registrar(idActor, AuditoriaService.ALOJAMIENTO_CAMBIO_ESTADO,
				AuditoriaService.ENTIDAD_ALOJAMIENTO, alojamiento.getIdAlojamiento(), ResultadoAuditoria.RECHAZADO,
				mensaje);
		return Resultado.error(Resultado.ESTADO_INVALIDO, mensaje);
	}

	/**
	 * Guarda un cambio de estado en el historial.
	 *
	 * @param idAlojamiento alojamiento
	 * @param anterior      estado anterior
	 * @param nuevo         estado nuevo
	 * @param idActor       usuario que hizo el cambio
	 * @param motivo        motivo; puede ser {@code null}
	 */
	private void registrarHistorial(Long idAlojamiento, EstadoAlojamiento anterior, EstadoAlojamiento nuevo,
			Long idActor, String motivo) {
		HistorialEstadoAlojamiento historial = new HistorialEstadoAlojamiento();
		historial.setIdAlojamiento(idAlojamiento);
		historial.setEstadoAnterior(anterior);
		historial.setEstadoNuevo(nuevo);
		historial.setIdUsuario(idActor);
		historial.setMotivo(motivo);
		historial.setFecha(LocalDateTime.now());
		historialRepo.save(historial);
	}

	/**
	 * Valida los datos enviados del alojamiento (largos, números positivos,
	 * coordenadas en rango, política existente).
	 *
	 * @param datos    datos a validar
	 * @param creacion {@code true} al crear, {@code false} al editar
	 * @return el mensaje de error, o {@code null} si son válidos
	 */
	private String validar(AlojamientoDTO datos, boolean creacion) {
		if (datos == null) {
			return "Debes enviar los datos del alojamiento.";
		}
		if (creacion && (datos.getTitulo() == null || datos.getTitulo().isBlank())) {
			return "El título es obligatorio.";
		}
		if (LimitesTexto.excede(datos.getTitulo(), LimitesTexto.CORTO)
				|| LimitesTexto.excede(datos.getUbicacion(), LimitesTexto.CORTO)
				|| LimitesTexto.excede(datos.getCiudad(), LimitesTexto.CORTO)
				|| LimitesTexto.excede(datos.getTipo(), LimitesTexto.CORTO)) {
			return "El título, la ubicación, la ciudad y el tipo pueden tener máximo " + LimitesTexto.CORTO
					+ " caracteres.";
		}
		if (LimitesTexto.excede(datos.getDescripcion(), LimitesTexto.LARGO)
				|| LimitesTexto.excede(datos.getReglas(), LimitesTexto.LARGO)) {
			return "La descripción y las reglas pueden tener máximo " + LimitesTexto.LARGO + " caracteres.";
		}
		if (datos.getAmenidades() != null
				&& datos.getAmenidades().stream().anyMatch(a -> LimitesTexto.excede(a, LimitesTexto.CORTO))) {
			return "Cada amenidad puede tener máximo " + LimitesTexto.CORTO + " caracteres.";
		}
		if (datos.getCapacidad() != null && datos.getCapacidad() <= 0) {
			return "La capacidad debe ser mayor que cero.";
		}
		if ((datos.getHabitaciones() != null && datos.getHabitaciones() < 0)
				|| (datos.getCamas() != null && datos.getCamas() < 0)
				|| (datos.getBanos() != null && datos.getBanos() < 0)) {
			return "Las habitaciones, las camas y los baños no pueden ser negativos.";
		}
		if (datos.getPrecioNoche() != null
				&& (datos.getPrecioNoche().signum() <= 0 || datos.getPrecioNoche().compareTo(MONTO_MAXIMO) > 0)) {
			return "El precio por noche debe ser positivo y de máximo 1.000.000.000.";
		}
		if ((datos.getPorcentajeLimpieza() != null && !TarifaService.porcentajeValido(datos.getPorcentajeLimpieza()))
				|| (datos.getPorcentajeServicio() != null
						&& !TarifaService.porcentajeValido(datos.getPorcentajeServicio()))) {
			return "Los porcentajes de limpieza y servicio deben estar entre 0 y 100.";
		}
		if ((datos.getLatitud() != null && Math.abs(datos.getLatitud()) > LATITUD_MAXIMA)
				|| (datos.getLongitud() != null && Math.abs(datos.getLongitud()) > LONGITUD_MAXIMA)) {
			return "La latitud debe estar entre -90 y 90 y la longitud entre -180 y 180.";
		}
		if (datos.getIdPolitica() != null && !politicaRepo.existsById(datos.getIdPolitica())) {
			return "La política de cancelación no existe.";
		}
		return null;
	}

	/**
	 * Indica si los datos no traen ni latitud ni longitud.
	 *
	 * @param datos datos enviados
	 * @return {@code true} si no traen coordenadas
	 */
	private static boolean sinCoordenadas(AlojamientoDTO datos) {
		return datos.getLatitud() == null && datos.getLongitud() == null;
	}

	/**
	 * Guarda las coordenadas que corresponden a la dirección y la ciudad. Si no
	 * se encuentran, las deja en {@code null} (el frontend muestra entonces la
	 * zona de la ciudad).
	 *
	 * @param alojamiento alojamiento a ubicar
	 */
	private void ubicarEnMapa(Alojamiento alojamiento) {
		Optional<Coordenadas> coordenadas = geocodingService.ubicar(alojamiento.getUbicacion(),
				alojamiento.getCiudad());
		alojamiento.setLatitud(coordenadas.map(Coordenadas::latitud).orElse(null));
		alojamiento.setLongitud(coordenadas.map(Coordenadas::longitud).orElse(null));
	}

	/**
	 * Copia al alojamiento los campos que no vengan en {@code null}. Las
	 * amenidades se guardan en minúsculas y sin repetir.
	 *
	 * @param datos       datos enviados
	 * @param alojamiento alojamiento a modificar
	 */
	private static void aplicar(AlojamientoDTO datos, Alojamiento alojamiento) {
		if (datos.getTitulo() != null) {
			alojamiento.setTitulo(datos.getTitulo().trim());
		}
		if (datos.getDescripcion() != null) {
			alojamiento.setDescripcion(datos.getDescripcion().trim());
		}
		if (datos.getUbicacion() != null) {
			alojamiento.setUbicacion(datos.getUbicacion().trim());
		}
		if (datos.getCiudad() != null) {
			alojamiento.setCiudad(datos.getCiudad().trim());
		}
		if (datos.getTipo() != null) {
			alojamiento.setTipo(datos.getTipo().trim());
		}
		if (datos.getReglas() != null) {
			alojamiento.setReglas(datos.getReglas().trim());
		}
		if (datos.getLatitud() != null) {
			alojamiento.setLatitud(datos.getLatitud());
		}
		if (datos.getLongitud() != null) {
			alojamiento.setLongitud(datos.getLongitud());
		}
		if (datos.getCapacidad() != null) {
			alojamiento.setCapacidad(datos.getCapacidad());
		}
		if (datos.getHabitaciones() != null) {
			alojamiento.setHabitaciones(datos.getHabitaciones());
		}
		if (datos.getCamas() != null) {
			alojamiento.setCamas(datos.getCamas());
		}
		if (datos.getBanos() != null) {
			alojamiento.setBanos(datos.getBanos());
		}
		if (datos.getHoraEntrada() != null) {
			alojamiento.setHoraEntrada(datos.getHoraEntrada());
		}
		if (datos.getHoraSalida() != null) {
			alojamiento.setHoraSalida(datos.getHoraSalida());
		}
		if (datos.getPrecioNoche() != null) {
			alojamiento.setPrecioNoche(datos.getPrecioNoche());
		}
		if (datos.getPorcentajeLimpieza() != null) {
			alojamiento.setPorcentajeLimpieza(datos.getPorcentajeLimpieza());
		}
		if (datos.getPorcentajeServicio() != null) {
			alojamiento.setPorcentajeServicio(datos.getPorcentajeServicio());
		}
		if (datos.getIdPolitica() != null) {
			alojamiento.setIdPolitica(datos.getIdPolitica());
		}
		if (datos.getAmenidades() != null && !datos.getAmenidades().isEmpty()) {
			Set<String> amenidades = new HashSet<>();
			for (String amenidad : datos.getAmenidades()) {
				if (amenidad != null && !amenidad.isBlank()) {
					amenidades.add(amenidad.trim().toLowerCase());
				}
			}
			alojamiento.setAmenidades(amenidades);
		}
	}

	/**
	 * Valida un lote de imágenes antes de guardar alguna: que venga al menos
	 * una, que no se supere {@link #MAXIMO_IMAGENES} y que cada archivo sea una
	 * imagen válida.
	 *
	 * @param archivos imágenes recibidas
	 * @param actuales imágenes que ya tiene el alojamiento
	 * @return el mensaje de error, o {@code null} si el lote es válido
	 */
	private String errorArchivos(List<MultipartFile> archivos, int actuales) {
		if (archivos == null || archivos.isEmpty()) {
			return "Debes enviar al menos una imagen.";
		}
		if (actuales + archivos.size() > MAXIMO_IMAGENES) {
			return "Un alojamiento puede tener máximo " + MAXIMO_IMAGENES + " imágenes.";
		}
		for (MultipartFile archivo : archivos) {
			String error = almacenamientoService.errorImagen(archivo);
			if (error != null) {
				return archivo == null ? error : error + " (" + archivo.getOriginalFilename() + ")";
			}
		}
		return null;
	}

	/**
	 * Guarda los archivos ya validados; si el alojamiento no tenía portada, el
	 * primero queda como principal.
	 *
	 * @param idAlojamiento  alojamiento
	 * @param archivos       imágenes a guardar
	 * @param tienePrincipal {@code true} si el alojamiento ya tiene portada
	 */
	private void guardarArchivos(Long idAlojamiento, List<MultipartFile> archivos, boolean tienePrincipal) {
		for (MultipartFile archivo : archivos) {
			long orden = imagenRepo.siguienteOrden();
			ImagenSubida subida = almacenamientoService.guardarImagen(archivo, idAlojamiento, !tienePrincipal, orden);
			Imagen imagen = new Imagen();
			imagen.setIdAlojamiento(idAlojamiento);
			imagen.setRutaArchivo(subida.publicId());
			imagen.setUrl(subida.url());
			imagen.setPrincipal(!tienePrincipal);
			imagen.setOrden(orden);
			imagenRepo.save(imagen);
			tienePrincipal = true;
		}
	}

	/**
	 * Pone la fecha de actualización del alojamiento en este momento y lo
	 * guarda.
	 *
	 * @param alojamiento alojamiento
	 */
	private void marcarActualizado(Alojamiento alojamiento) {
		alojamiento.setFechaActualizacion(LocalDateTime.now());
		alojamientoRepo.save(alojamiento);
	}

	/**
	 * Indica si un servicio adicional tiene nombre (máximo 255 caracteres) y un
	 * costo positivo dentro del máximo.
	 *
	 * @param datos servicio a revisar
	 * @return {@code true} si es válido
	 */
	private static boolean servicioValido(ServicioAdicionalDTO datos) {
		return datos != null && datos.getNombre() != null && !datos.getNombre().isBlank()
				&& !LimitesTexto.excede(datos.getNombre(), LimitesTexto.CORTO) && datos.getCosto() != null
				&& datos.getCosto().signum() > 0 && datos.getCosto().compareTo(MONTO_MAXIMO) <= 0;
	}

	/**
	 * Agrega el nombre del dato a la lista de faltantes si el valor está vacío.
	 *
	 * @param faltantes lista de datos faltantes
	 * @param valor     valor a revisar
	 * @param nombre    nombre del dato para el usuario
	 */
	private static void agregarSiFalta(List<String> faltantes, String valor, String nombre) {
		if (valor == null || valor.isBlank()) {
			faltantes.add(nombre);
		}
	}
}
