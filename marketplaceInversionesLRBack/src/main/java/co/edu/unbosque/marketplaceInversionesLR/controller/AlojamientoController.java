package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
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
 * {@code /alojamientos}. Las políticas son públicas; lo demás exige token.
 * Contrato: {@code docs/openapi/alojamientos.yaml}.
 */
@RestController
@RequestMapping("/alojamientos")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alojamientos", description = "Publicaciones de alojamiento")
public class AlojamientoController {

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
