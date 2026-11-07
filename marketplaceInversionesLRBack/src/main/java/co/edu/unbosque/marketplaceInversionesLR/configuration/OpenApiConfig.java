package co.edu.unbosque.marketplaceInversionesLR.configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Configuración de Swagger UI ({@code /swagger-ui.html}).
 * <p>
 * Define el título de la API y el esquema {@code bearerAuth} (token JWT), y
 * completa cada operación con las respuestas de error comunes (400, 401, 403,
 * 500) y su cuerpo real {@code {"message": "..."}}, para que Swagger muestre
 * todo lo que puede responder cada ruta.
 * </p>
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Marketplace de alojamientos", version = "1.0", description = "API del "
		+ "Marketplace de alojamientos de Inversiones LR. Todos los errores responden {\"message\": \"...\"}. "
		+ "Códigos que puede dar cualquier ruta: 404 ruta inexistente, 405 método no permitido, 409 el registro ya "
		+ "existe, 415 tipo de contenido no soportado."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

	/** Separa varios mensajes de una misma respuesta en su descripción. */
	public static final String SEPARADOR = " | ";

	/** Nombre del esquema del cuerpo de error. */
	private static final String ESQUEMA_ERROR = "MensajeError";

	/** Tipo de contenido JSON. */
	private static final String JSON = "application/json";

	/** Mensaje del 401. */
	private static final String NO_AUTENTICADO = "No autenticado: token ausente, inválido o expirado";

	/** Mensaje del 403. */
	private static final String SIN_PERMISO = "No tienes permisos para esta operación";

	/** Mensaje del 400 por un cuerpo que no es JSON. */
	private static final String JSON_INVALIDO = "El cuerpo de la petición no es un JSON válido o tiene un valor no permitido.";

	/** Mensaje del 500. */
	private static final String ERROR_INTERNO = "Ocurrió un error inesperado. Intenta de nuevo.";

	/**
	 * Agrega a los componentes el esquema del cuerpo de error.
	 *
	 * @return personalización del documento
	 */
	@Bean
	public OpenApiCustomizer esquemaDeError() {
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			Schema<?> esquema = new ObjectSchema().description("Respuesta de error de la API").addProperty("message",
					new StringSchema().description("Causa del error, lista para mostrar"));
			openApi.getComponents().addSchemas(ESQUEMA_ERROR, esquema);
		};
	}

	/**
	 * Completa cada operación con las respuestas de error comunes.
	 *
	 * @return personalización de las operaciones
	 */
	@Bean
	public OperationCustomizer respuestasComunes() {
		return (operacion, metodo) -> {
			ApiResponses respuestas = operacion.getResponses() == null ? new ApiResponses() : operacion.getResponses();
			quitarExitoGenerico(respuestas, metodo);

			// @SecurityRequirements vacío en el método: es público aunque la clase pida token
			SecurityRequirements publico = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(),
					SecurityRequirements.class);
			if (publico != null && publico.value().length == 0) {
				operacion.setSecurity(new ArrayList<>());
			}
			boolean conToken = operacion.getSecurity() != null && !operacion.getSecurity().isEmpty();
			if (conToken) {
				agregar(respuestas, "401", NO_AUTENTICADO);
				if (exigeRol(metodo)) {
					agregar(respuestas, "403", SIN_PERMISO);
				}
			}
			boolean recibeJson = operacion.getRequestBody() != null && operacion.getRequestBody().getContent() != null
					&& operacion.getRequestBody().getContent().containsKey(JSON);
			if (recibeJson) {
				agregar(respuestas, "400", JSON_INVALIDO);
			}
			if (operacion.getParameters() != null) {
				operacion.getParameters().stream().filter(OpenApiConfig::puedeSerInvalido)
						.forEach(p -> agregar(respuestas, "400", "El valor de " + p.getName() + " no es válido."));
			}
			agregar(respuestas, "500", ERROR_INTERNO);

			// Ordenadas por código y con el cuerpo de error real
			Map<String, ApiResponse> ordenadas = new TreeMap<>(respuestas);
			ApiResponses resultado = new ApiResponses();
			ordenadas.forEach((codigo, respuesta) -> resultado.addApiResponse(codigo, conCuerpoDeError(codigo, respuesta)));
			operacion.setResponses(resultado);
			return operacion;
		};
	}

	/**
	 * Quita el 200 que springdoc agrega por defecto cuando la ruta responde otro
	 * código de éxito (por ejemplo 201) y no declara un 200 propio.
	 *
	 * @param respuestas respuestas de la operación
	 * @param metodo     método del controlador
	 */
	private static void quitarExitoGenerico(ApiResponses respuestas, HandlerMethod metodo) {
		boolean declara200 = Arrays
				.stream(metodo.getMethod().getAnnotationsByType(io.swagger.v3.oas.annotations.responses.ApiResponse.class))
				.anyMatch(a -> "200".equals(a.responseCode()));
		boolean otroExito = respuestas.keySet().stream().anyMatch(c -> c.startsWith("2") && !"200".equals(c));
		if (otroExito && !declara200) {
			respuestas.remove("200");
		}
	}

	/**
	 * Indica si un parámetro mal escrito puede responder 400 (número, fecha o
	 * valor de una lista).
	 *
	 * @param parametro parámetro de la operación
	 * @return {@code true} si puede ser inválido
	 */
	private static boolean puedeSerInvalido(Parameter parametro) {
		Schema<?> esquema = parametro.getSchema();
		if (esquema == null || "array".equals(esquema.getType())) {
			return false;
		}
		return !"string".equals(esquema.getType()) || esquema.getFormat() != null || esquema.getEnum() != null;
	}

	/**
	 * Indica si la ruta exige un rol.
	 *
	 * @param metodo método del controlador
	 * @return {@code true} si tiene {@code @PreAuthorize}
	 */
	private static boolean exigeRol(HandlerMethod metodo) {
		return AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), PreAuthorize.class)
				|| AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), PreAuthorize.class);
	}

	/**
	 * Agrega una respuesta o suma su mensaje a la que ya existe con ese código.
	 *
	 * @param respuestas respuestas de la operación
	 * @param codigo     código HTTP
	 * @param mensaje    mensaje de la respuesta
	 */
	private static void agregar(ApiResponses respuestas, String codigo, String mensaje) {
		ApiResponse existente = respuestas.get(codigo);
		if (existente == null) {
			respuestas.addApiResponse(codigo, new ApiResponse().description(mensaje));
		} else if (existente.getDescription() == null || !existente.getDescription().contains(mensaje)) {
			existente.setDescription(
					existente.getDescription() == null ? mensaje : existente.getDescription() + SEPARADOR + mensaje);
		}
	}

	/**
	 * A las respuestas de error les pone el cuerpo {@code {"message": ...}} con un
	 * ejemplo por cada mensaje posible.
	 *
	 * @param codigo    código HTTP
	 * @param respuesta respuesta de la operación
	 * @return la misma respuesta, con el cuerpo de error
	 */
	private static ApiResponse conCuerpoDeError(String codigo, ApiResponse respuesta) {
		if (codigo.isEmpty() || codigo.charAt(0) < '4' || respuesta.getDescription() == null) {
			return respuesta;
		}
		String[] mensajes = respuesta.getDescription().split(Pattern.quote(SEPARADOR));
		MediaType tipo = new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ESQUEMA_ERROR));
		for (String mensaje : mensajes) {
			String nombre = mensaje.length() > 60 ? mensaje.substring(0, 57) + "..." : mensaje;
			tipo.addExamples(nombre, new Example().value(Map.of("message", mensaje)));
		}
		respuesta.setContent(new Content().addMediaType(JSON, tipo));
		respuesta.setDescription(mensajes.length == 1 ? mensajes[0] : "- " + String.join("\n- ", mensajes));
		return respuesta;
	}
}
