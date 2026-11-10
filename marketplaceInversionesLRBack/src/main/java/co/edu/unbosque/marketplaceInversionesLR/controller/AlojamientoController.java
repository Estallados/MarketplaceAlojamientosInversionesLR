package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ServicioAdicionalDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Controlador REST de alojamientos (publicaciones). Ruta base:
 * {@code /alojamientos}. La búsqueda, el catálogo, los tipos, las políticas y
 * el detalle de una publicación ACTIVA son públicos; lo demás exige token.
 * Contratos: {@code docs/openapi/alojamientos.yaml} y, para la búsqueda,
 * {@code docs/openapi/disponibilidad-busqueda.yaml}.
 */
@RestController
@RequestMapping("/alojamientos")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alojamientos", description = "Publicaciones de alojamiento")
public class AlojamientoController {

	/** Orden de los listados: los más recientes primero. */
	private static final Sort RECIENTES = Sort.by("fechaCreacion").descending();
	/** Orden por defecto de la búsqueda: los más recientes primero. */
	private static final Sort RECIENTES = Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("idAlojamiento"));

	/** Servicio de publicaciones. */
	private final PublicacionService publicacionService;

	/** Lee el JSON del alojamiento que llega como texto en el formulario. */
	private final ObjectMapper objectMapper;

	/** Deja en la auditoría la creación de publicaciones (HU-26). */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el controlador.
	 *
	 * @param publicacionService servicio de publicaciones
	 * @param objectMapper       conversor de JSON
	 * @param auditoriaService   auditoría de las publicaciones
	 */
	public AlojamientoController(PublicacionService publicacionService, ObjectMapper objectMapper,
			AuditoriaService auditoriaService) {
		this.publicacionService = publicacionService;
		this.objectMapper = objectMapper;
		this.auditoriaService = auditoriaService;
	}

	/** Solo para Swagger: los dos campos del formulario de {@link #crear}. */
	@Schema(name = "FormularioAlojamiento")
	static final class FormularioAlojamiento {

		/** JSON del alojamiento. */
		private AlojamientoDTO datos;

		/** Imágenes del alojamiento. */
		private List<MultipartFile> archivos;

		/**
		 * Devuelve JSON del alojamiento.
		 *
		 * @return JSON del alojamiento
		 */
		@Schema(description = "JSON del alojamiento (solo el título es obligatorio en el borrador). El id, el "
				+ "estado y el anfitrión se ignoran.", requiredMode = Schema.RequiredMode.REQUIRED)
		public AlojamientoDTO getDatos() {
			return datos;
		}

		/**
		 * Devuelve las imágenes del alojamiento.
		 *
		 * @return imágenes del alojamiento
		 */
		@ArraySchema(schema = @Schema(type = "string", format = "binary"), arraySchema = @Schema(description = "De 1 a "
				+ "15 imágenes JPG, PNG o WEBP de hasta 5 MB; la primera queda como principal"))
		public List<MultipartFile> getArchivos() {
			return archivos;
		}
	}

	/**
	 * Políticas de cancelación que el anfitrión puede asignar a su
	 * alojamiento. Ruta: {@code GET /alojamientos/politicas}.
	 *
	 * @return 200 con la lista de políticas
	 */
	@Operation(summary = "Políticas de cancelación disponibles (público)")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Políticas de cancelación")
	@GetMapping("/politicas")
	public ResponseEntity<Object> politicas() {
		return ResponseEntity.ok(publicacionService.politicas());
	}

	/**
	 * Búsqueda pública de alojamientos ACTIVA (HU-23, CA-087) con filtros
	 * opcionales que se combinan. Sin orden, los más recientes primero. Ruta:
	 * {@code GET /alojamientos}.
	 *
	 * @param ciudad       ciudad
	 * @param llegada      día de llegada (AAAA-MM-DD)
	 * @param salida       día de salida (AAAA-MM-DD)
	 * @param huespedes    cantidad de huéspedes
	 * @param tipo         tipo de alojamiento
	 * @param precioMinimo precio mínimo por noche
	 * @param precioMaximo precio máximo por noche
	 * @param amenidades   amenidades que debe tener todas
	 * @param orden        los mismos valores de {@code /disponibles}
	 * @param pagina       número de página (desde 0)
	 * @param tamanio      elementos por página
	 * @return 200 con la página de resultados; 400 si los filtros o el orden no
	 *         son válidos
	 */
	@Operation(summary = "Buscar alojamientos ACTIVA (público)", description = "Solo publicaciones ACTIVA que cumplan "
			+ "todos los filtros enviados (CA-087): ciudad y tipo sin distinguir mayúsculas, disponibilidad en el "
			+ "período (llegada y salida van juntas), capacidad para los huéspedes, precio por noche dentro del rango "
			+ "y amenidades (con varias, debe tenerlas todas). Los filtros vacíos no filtran. orden opcional como en "
			+ "/disponibles; sin él, los más recientes primero. Responde {contenido, pagina, tamanio, "
			+ "totalElementos, totalPaginas}.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Página de alojamientos ACTIVA que cumplen los filtros (CA-087)")
	@ApiResponse(responseCode = "400", description = CalendarioService.FECHAS_FALTANTES + " | "
			+ CalendarioService.SALIDA_NO_POSTERIOR + " | " + CalendarioService.LLEGADA_EN_EL_PASADO + " | "
			+ PublicacionService.HUESPEDES_INVALIDOS + " | " + PublicacionService.PRECIO_NEGATIVO + " | "
			+ PublicacionService.PRECIOS_INVERTIDOS + " | " + PublicacionService.ORDEN_INVALIDO)
	@GetMapping
	public ResponseEntity<Object> buscar(
			@Parameter(description = "Ciudad, sin distinguir mayúsculas; por ejemplo Medellín") @RequestParam(required = false) String ciudad,
			@Parameter(description = "Día de llegada (AAAA-MM-DD); va junto con la salida") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate llegada,
			@Parameter(description = "Día de salida (AAAA-MM-DD), posterior a la llegada") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salida,
			@Parameter(description = "Cantidad de huéspedes, mayor que cero") @RequestParam(required = false) Integer huespedes,
			@Parameter(description = "Tipo de alojamiento, sin distinguir mayúsculas; por ejemplo Casa") @RequestParam(required = false) String tipo,
			@Parameter(description = "Precio mínimo por noche") @RequestParam(required = false) BigDecimal precioMinimo,
			@Parameter(description = "Precio máximo por noche; no puede ser menor que el mínimo") @RequestParam(required = false) BigDecimal precioMaximo,
			@Parameter(description = "Amenidades que debe tener todas, por ejemplo wifi y parqueadero") @RequestParam(required = false) List<String> amenidades,
			@Parameter(description = "recomendados, precio_menor, precio_mayor o mejor_calificados", schema = @Schema(allowableValues = {
					PublicacionService.ORDEN_RECOMENDADOS, PublicacionService.ORDEN_PRECIO_MENOR,
					PublicacionService.ORDEN_PRECIO_MAYOR, PublicacionService.ORDEN_MEJOR_CALIFICADOS })) @RequestParam(required = false) String orden,
			@Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = "Elementos por página, de 1 a 100") @RequestParam(defaultValue = "12") int tamanio) {
		PublicacionService.FiltrosBusqueda filtros = new PublicacionService.FiltrosBusqueda(ciudad, llegada, salida,
				huespedes, tipo, precioMinimo, precioMaximo, amenidades);
		return RespuestaUtil.desde(publicacionService.buscar(filtros, orden,
				RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Catálogo público: todos los alojamientos que se pueden reservar
	 * (publicaciones ACTIVA con fechas abiertas desde hoy), para la página
	 * Explorar o el inicio. Ruta: {@code GET /alojamientos/disponibles}.
	 *
	 * @param orden   recomendados (por defecto), precio_menor, precio_mayor o
	 *                mejor_calificados
	 * @param pagina  número de página (desde 0)
	 * @param tamanio elementos por página
	 * @return 200 con la página de alojamientos; 400 si el orden no existe
	 */
	@Operation(summary = "Catálogo: alojamientos disponibles para reservar (público)", description = "Publicaciones "
			+ "ACTIVA con fechas abiertas desde hoy, para la página Explorar o el inicio. orden: recomendados (por "
			+ "defecto), precio_menor, precio_mayor o mejor_calificados.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Página de alojamientos que se pueden reservar")
	@ApiResponse(responseCode = "400", description = PublicacionService.ORDEN_INVALIDO)
	@GetMapping("/disponibles")
	public ResponseEntity<Object> disponibles(
			@Parameter(description = "recomendados (por defecto), precio_menor, precio_mayor o mejor_calificados", schema = @Schema(allowableValues = {
					PublicacionService.ORDEN_RECOMENDADOS, PublicacionService.ORDEN_PRECIO_MENOR,
					PublicacionService.ORDEN_PRECIO_MAYOR, PublicacionService.ORDEN_MEJOR_CALIFICADOS })) @RequestParam(required = false) String orden,
			@Parameter(description = "Número de página, desde 0") @RequestParam(defaultValue = "0") int pagina,
			@Parameter(description = "Elementos por página, de 1 a 100") @RequestParam(defaultValue = "20") int tamanio) {
		return RespuestaUtil.desde(publicacionService.disponibles(orden,
				RespuestaUtil.pagina(pagina, tamanio, Sort.unsorted())));
	}

	/**
	 * Cantidad de alojamientos por tipo, en cualquier estado, para los filtros
	 * de la página de inicio. Ruta: {@code GET /alojamientos/tipos}.
	 *
	 * @return 200 con {"Apartamento": 5, "Casa": 7, ...}
	 */
	@Operation(summary = "Cantidad de alojamientos por tipo (público)", description = "Cuenta los alojamientos en "
			+ "cualquier estado agrupados por tipo, en orden alfabético: {\"Apartamento\": 5, \"Casa\": 7}.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Cantidad de alojamientos de cada tipo")
	@GetMapping("/tipos")
	public ResponseEntity<Map<String, Long>> cantidadPorTipo() {
		return ResponseEntity.ok(publicacionService.cantidadPorTipo());
	}

	/**
	 * Crea un alojamiento en BORRADOR con sus imágenes (HU-15, solo
	 * anfitriones). Llega como multipart/form-data con dos campos:
	 * {@code datos}, el JSON del alojamiento (en el borrador solo el título es
	 * obligatorio), y {@code archivos}, de 1 a 15 imágenes JPG, PNG o WEBP de
	 * hasta 5 MB. La primera imagen queda como portada. Ruta:
	 * {@code POST /alojamientos}.
	 *
	 * @param datos          JSON del alojamiento, como texto (el frontend lo
	 *                       manda en el FormData sin tipo de contenido)
	 * @param archivos       imágenes del alojamiento
	 * @param authentication anfitrión autenticado
	 * @return 201 con el alojamiento (CA-094); 400 si los datos o las imágenes
	 *         no son válidos (CA-098); 413, 415 o 503 según el caso
	 */
	@Operation(summary = "Crear alojamiento en BORRADOR con sus imágenes (ANFITRION)", description = "multipart/form-data con dos campos: 'datos', el JSON del alojamiento (solo el título es "
			+ "obligatorio en el borrador), y 'archivos', de 1 a 15 imágenes JPG, PNG o WEBP de hasta 5 MB. "
			+ "La primera imagen queda como principal.")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "201", description = "Alojamiento creado en BORRADOR con sus imágenes")
	@ApiResponse(responseCode = "400", description = "El título es obligatorio. | Debes enviar al menos una imagen. | "
			+ "Un alojamiento puede tener máximo 15 imágenes. | Solo se aceptan imágenes JPG, PNG o WEBP. "
			+ "(archivo.pdf) | El archivo está vacío. (foto.png) | El campo 'datos' debe ser el JSON del "
			+ "alojamiento: ... | La capacidad debe ser mayor que cero. | La política de cancelación no existe.")
	@ApiResponse(responseCode = "413", description = "El archivo supera el tamaño permitido (5 MB por imagen).")
	@ApiResponse(responseCode = "415", description = "El cuerpo debe enviarse como multipart/form-data.")
	@ApiResponse(responseCode = "503", description = "No se pudo guardar la imagen. Intenta de nuevo.")
	// Swagger muestra 'datos' como JSON del alojamiento; el método lo recibe como texto
	// porque el front lo envía en el FormData sin tipo de contenido
	@io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = FormularioAlojamiento.class), encoding = @Encoding(name = "datos", contentType = MediaType.APPLICATION_JSON_VALUE)))
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Object> crear(@Parameter(hidden = true) @RequestPart("datos") String datos,
			@Parameter(hidden = true) @RequestPart(value = "archivos", required = false) List<MultipartFile> archivos,
			Authentication authentication) {
		Long idAnfitrion = RespuestaUtil.idUsuario(authentication);
		AlojamientoDTO alojamiento;
		try {
			alojamiento = objectMapper.readValue(datos, AlojamientoDTO.class);
		} catch (JacksonException e) {
			String mensaje = "El campo 'datos' debe ser el JSON del alojamiento: " + e.getOriginalMessage();
			auditar(idAnfitrion, null, ResultadoAuditoria.RECHAZADO, mensaje);
			return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, mensaje);
		}
		Resultado<AlojamientoDTO> resultado = publicacionService.crear(alojamiento, archivos, idAnfitrion);
		if (resultado.esExito()) {
			auditar(idAnfitrion, resultado.getDato().getIdAlojamiento(), ResultadoAuditoria.EXITOSO, null);
		} else {
			auditar(idAnfitrion, null, ResultadoAuditoria.RECHAZADO, resultado.getMensaje());
		}
		return RespuestaUtil.desde(resultado, HttpStatus.CREATED);
	}

	/**
	 * Mis alojamientos con su estado (HU-19, CA-103, solo anfitriones). Ruta:
	 * {@code GET /alojamientos/mios}.
	 *
	 * @param authentication anfitrión autenticado
	 * @param pagina         número de página (desde 0)
	 * @param tamanio        elementos por página
	 * @return 200 con la página de alojamientos
	 */
	@Operation(summary = "Mis alojamientos con su estado (ANFITRION)")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "200", description = "Página de alojamientos del anfitrión con su estado")
	@GetMapping("/mios")
	public ResponseEntity<Object> mios(Authentication authentication, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "20") int tamanio) {
		return ResponseEntity.ok(publicacionService.mios(RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.pagina(pagina, tamanio, RECIENTES)));
	}

	/**
	 * Detalle de un alojamiento (HU-18, CA-093), con la latitud y la longitud
	 * para el mapa (CA-164). Es público si está ACTIVA; en otro estado solo lo
	 * ven su anfitrión o un administrador. Ruta:
	 * {@code GET /alojamientos/{id}}.
	 *
	 * @param id             alojamiento
	 * @param authentication usuario autenticado; puede ser {@code null}
	 * @return 200 con el alojamiento, sus imágenes, servicios y anfitrión; 404
	 *         si no existe o no se puede ver
	 */
	@Operation(summary = "Detalle de un alojamiento", description = "Público si está ACTIVA; en otro estado solo para su anfitrión o un administrador.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Alojamiento con imágenes, servicios y anfitrión")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@GetMapping("/{id}")
	public ResponseEntity<Object> detalle(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.obtener(id, RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Historial de estados (dueño o administrador). Ruta:
	 * {@code GET /alojamientos/{id}/historial}.
	 *
	 * @param id             alojamiento
	 * @param authentication usuario autenticado
	 * @return 200 con los cambios de estado, del primero al último; 404 si no
	 *         existe o no se puede ver
	 */
	@Operation(summary = "Historial de estados (propietario o ADMIN)")
	@ApiResponse(responseCode = "200", description = "Cambios de estado, del primero al último")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@GetMapping("/{id}/historial")
	public ResponseEntity<Object> historial(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.historial(id, RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Edita un alojamiento (dueño o administrador). Los campos en
	 * {@code null} no cambian y las amenidades enviadas reemplazan las
	 * anteriores; las imágenes se manejan con las rutas de {@code /imagenes}.
	 * Ruta: {@code PUT /alojamientos/{id}}.
	 *
	 * @param id             alojamiento
	 * @param datos          datos nuevos
	 * @param authentication usuario autenticado
	 * @return 200 con el alojamiento; 400, 403 o 404 según el
	 *         caso
	 */
	@Operation(summary = "Editar alojamiento (propietario o ADMIN)", description = "Los campos en null no cambian; las amenidades enviadas reemplazan las anteriores. Las "
			+ "imágenes se gestionan con los endpoints de /imagenes.")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Alojamiento actualizado")
	@ApiResponse(responseCode = "400", description = "La capacidad debe ser mayor que cero. | El precio por noche debe "
			+ "ser positivo. | Las habitaciones, las camas y los baños no pueden ser negativos. | La política de cancelación "
			+ "no existe.")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@PutMapping("/{id}")
	public ResponseEntity<Object> actualizar(@PathVariable Long id, @RequestBody AlojamientoDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.actualizar(id, datos, RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Envía a revisión: BORRADOR → PENDIENTE_REVISION (solo anfitriones).
	 * Ruta: {@code POST /alojamientos/{id}/enviarrevision}.
	 *
	 * @param id             alojamiento
	 * @param authentication anfitrión autenticado
	 * @return 200 con el alojamiento; 403; 404; 409 si faltan datos o el estado
	 *         no lo permite
	 */
	@Operation(summary = "Enviar a revisión: BORRADOR → PENDIENTE_REVISION (ANFITRION)")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "200", description = "Alojamiento PENDIENTE_REVISION")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@ApiResponse(responseCode = "409", description = "Completa los datos antes de enviar a revisión. Falta: "
			+ "descripción, capacidad. | No se puede pasar de ACTIVA a PENDIENTE_REVISION.")
	@PostMapping("/{id}/enviarrevision")
	public ResponseEntity<Object> enviarRevision(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.enviarRevision(id, RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Pausa una publicación: ACTIVA → PAUSADA (solo anfitriones); deja de
	 * recibir reservas nuevas. Ruta: {@code POST /alojamientos/{id}/pausar}.
	 *
	 * @param id             alojamiento
	 * @param authentication anfitrión autenticado
	 * @return 200 con el alojamiento; 403, 404 o 409
	 */
	@Operation(summary = "Pausar: ACTIVA → PAUSADA (ANFITRION)")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "200", description = "Alojamiento PAUSADA: no recibe reservas nuevas")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@ApiResponse(responseCode = "409", description = "No se puede pasar de BORRADOR a PAUSADA.")
	@PostMapping("/{id}/pausar")
	public ResponseEntity<Object> pausar(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.pausar(id, RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Reanuda una publicación: PAUSADA → ACTIVA (solo anfitriones). Ruta:
	 * {@code POST /alojamientos/{id}/reanudar}.
	 *
	 * @param id             alojamiento
	 * @param authentication anfitrión autenticado
	 * @return 200 con el alojamiento; 403, 404 o 409
	 */
	@Operation(summary = "Reanudar: PAUSADA → ACTIVA (ANFITRION)")
	@PreAuthorize("hasRole('ANFITRION')")
	@ApiResponse(responseCode = "200", description = "Alojamiento ACTIVA")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@ApiResponse(responseCode = "409", description = "No se puede pasar de BLOQUEADA a ACTIVA.")
	@PostMapping("/{id}/reanudar")
	public ResponseEntity<Object> reanudar(@PathVariable Long id, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.reanudar(id, RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Sube una o varias imágenes (dueño o administrador): JPG, PNG o
	 * WEBP de hasta 5 MB, máximo 15 por alojamiento. Si no había portada, la
	 * primera queda como principal. Ruta:
	 * {@code POST /alojamientos/{id}/imagenes}.
	 *
	 * @param id             alojamiento
	 * @param archivos       imágenes a subir (campo {@code archivos})
	 * @param authentication usuario autenticado
	 * @return 201 con todas las imágenes, la principal primero; 400, 403, 404,
	 *         413, 415 o 503 según el caso
	 */
	@Operation(summary = "Subir imágenes (propietario o ADMIN)", description = "multipart/form-data con uno o varios archivos en el campo 'archivos'. JPG, PNG o WEBP de "
			+ "hasta 5 MB, máximo 15 por alojamiento. Si no había portada, la primera queda como principal.")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "201", description = "Todas las imágenes del alojamiento, la principal primero")
	@ApiResponse(responseCode = "400", description = "Debes enviar al menos una imagen. | Un alojamiento puede tener "
			+ "máximo 15 imágenes. | Solo se aceptan imágenes JPG, PNG o WEBP. (archivo.pdf) | El archivo está "
			+ "vacío. (foto.png)")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@ApiResponse(responseCode = "413", description = "El archivo supera el tamaño permitido (5 MB por imagen).")
	@ApiResponse(responseCode = "415", description = "El cuerpo debe enviarse como multipart/form-data.")
	@ApiResponse(responseCode = "503", description = "No se pudo guardar la imagen. Intenta de nuevo.")
	@PostMapping(value = "/{id}/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Object> subirImagenes(@PathVariable Long id,
			@RequestParam("archivos") List<MultipartFile> archivos, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.subirImagenes(id, archivos,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)), HttpStatus.CREATED);
	}

	/**
	 * Elimina una imagen (dueño o administrador). Si era la portada, la
	 * siguiente pasa a serlo; el alojamiento debe conservar al menos una
	 * imagen. Ruta: {@code DELETE /alojamientos/{id}/imagenes/{idImagen}}.
	 *
	 * @param id             alojamiento
	 * @param idImagen       imagen
	 * @param authentication usuario autenticado
	 * @return 200 con las imágenes restantes; 403; 404; 409 si es la última
	 *         imagen
	 */
	@Operation(summary = "Eliminar una imagen (propietario o ADMIN)", description = "Si era la principal, la siguiente pasa a serlo. El alojamiento debe conservar al menos "
			+ "una imagen (409 si es la última).")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Imágenes restantes, la principal primero")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe. | La imagen no existe.")
	@ApiResponse(responseCode = "409", description = "El alojamiento debe conservar al menos una imagen. Sube otra "
			+ "antes de borrar esta.")
	@DeleteMapping("/{id}/imagenes/{idImagen}")
	public ResponseEntity<Object> eliminarImagen(@PathVariable Long id, @PathVariable Long idImagen,
			Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.eliminarImagen(id, idImagen,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Marca una imagen como portada (dueño o administrador). Ruta:
	 * {@code PATCH /alojamientos/{id}/imagenes/{idImagen}/principal}.
	 *
	 * @param id             alojamiento
	 * @param idImagen       imagen
	 * @param authentication usuario autenticado
	 * @return 200 con las imágenes, la principal primero; 403 o 404
	 */
	@Operation(summary = "Marcar una imagen como principal (propietario o ADMIN)")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Imágenes, la principal primero")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe. | La imagen no existe.")
	@PatchMapping("/{id}/imagenes/{idImagen}/principal")
	public ResponseEntity<Object> marcarPrincipal(@PathVariable Long id, @PathVariable Long idImagen,
			Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.marcarPrincipal(id, idImagen,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Agrega un servicio adicional con costo (dueño o administrador).
	 * Ruta: {@code POST /alojamientos/{id}/servicios}.
	 *
	 * @param id             alojamiento
	 * @param datos          nombre y costo
	 * @param authentication usuario autenticado
	 * @return 201 con el servicio; 400, 403 o 404
	 */
	@Operation(summary = "Agregar servicio adicional con costo (propietario o ADMIN)")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "201", description = "Servicio adicional creado")
	@ApiResponse(responseCode = "400", description = "El servicio adicional necesita un nombre y un costo positivo.")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe.")
	@PostMapping("/{id}/servicios")
	public ResponseEntity<Object> agregarServicio(@PathVariable Long id, @RequestBody ServicioAdicionalDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.agregarServicio(id, datos,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)), HttpStatus.CREATED);
	}

	/**
	 * Modifica un servicio adicional (dueño o administrador). Ruta:
	 * {@code PUT /alojamientos/{id}/servicios/{idServicio}}.
	 *
	 * @param id             alojamiento
	 * @param idServicio     servicio
	 * @param datos          nombre y costo nuevos
	 * @param authentication usuario autenticado
	 * @return 200 con el servicio; 400, 403 o 404
	 */
	@Operation(summary = "Modificar servicio adicional (propietario o ADMIN)")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Servicio adicional actualizado")
	@ApiResponse(responseCode = "400", description = "El servicio adicional necesita un nombre y un costo positivo.")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe. | El servicio adicional no existe.")
	@PutMapping("/{id}/servicios/{idServicio}")
	public ResponseEntity<Object> actualizarServicio(@PathVariable Long id, @PathVariable Long idServicio,
			@RequestBody ServicioAdicionalDTO datos, Authentication authentication) {
		return RespuestaUtil.desde(publicacionService.actualizarServicio(id, idServicio, datos,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Elimina un servicio adicional (dueño o administrador). Ruta:
	 * {@code DELETE /alojamientos/{id}/servicios/{idServicio}}.
	 *
	 * @param id             alojamiento
	 * @param idServicio     servicio
	 * @param authentication usuario autenticado
	 * @return 200 si se eliminó; 403 o 404
	 */
	@Operation(summary = "Eliminar servicio adicional (propietario o ADMIN)")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Servicio adicional eliminado.")
	@ApiResponse(responseCode = "403", description = "Solo el anfitrión propietario o un administrador puede modificar "
			+ "este alojamiento.")
	@ApiResponse(responseCode = "404", description = "El alojamiento no existe. | El servicio adicional no existe.")
	@DeleteMapping("/{id}/servicios/{idServicio}")
	public ResponseEntity<Object> eliminarServicio(@PathVariable Long id, @PathVariable Long idServicio,
			Authentication authentication) {
		Resultado<Void> resultado = publicacionService.eliminarServicio(id, idServicio,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication));
		return resultado.esExito() ? RespuestaUtil.mensaje(HttpStatus.OK, "Servicio adicional eliminado.")
				: RespuestaUtil.desde(resultado);
	}

	/**
	 * Deja en la auditoría el intento de crear una publicación (HU-26).
	 *
	 * @param idAnfitrion   anfitrión que la creó
	 * @param idAlojamiento alojamiento creado; null si se rechazó
	 * @param resultado     EXITOSO o RECHAZADO
	 * @param detalle       motivo del rechazo; null si se creó
	 */
	private void auditar(Long idAnfitrion, Long idAlojamiento, ResultadoAuditoria resultado, String detalle) {
		auditoriaService.registrar(idAnfitrion, AuditoriaService.ALOJAMIENTO_CREADO, AuditoriaService.ENTIDAD_ALOJAMIENTO,
				idAlojamiento, resultado, detalle);
	}
}
