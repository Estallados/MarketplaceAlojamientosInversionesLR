package co.edu.unbosque.marketplaceInversionesLR.util;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;

/**
 * Respuestas con el formato de la API: {@code {"message": "..."}} (esquema
 * {@code Mensaje} de {@code docs/openapi}).
 */
public final class RespuestaUtil {

	/** Autoridad de Spring del rol ADMIN. */
	private static final String ROL_ADMIN = "ROLE_ADMIN";

	/** Tamaño máximo de página que acepta la API. */
	public static final int TAMANIO_MAXIMO = 100;

	private RespuestaUtil() {
		throw new IllegalStateException("Clase utilitaria");
	}

	/**
	 * Respuesta con un mensaje para el usuario.
	 *
	 * @param status estado HTTP
	 * @param texto  mensaje
	 * @return respuesta {@code {"message": texto}}
	 */
	public static ResponseEntity<Object> mensaje(HttpStatus status, String texto) {
		return ResponseEntity.status(status).body(Map.of("message", texto));
	}

	/**
	 * Traduce un {@link Resultado} a HTTP. Si fue exitoso responde 200 con el
	 * dato; si no, responde el estado que corresponde al código y
	 * {@code {"message": causa}}. Cuando el resultado rechazado trae un dato
	 * también lo incluye en el campo {@code "dato"}.
	 *
	 * @param resultado resultado que devolvió el servicio
	 * @return respuesta HTTP equivalente
	 */
	public static ResponseEntity<Object> desde(Resultado<?> resultado) {
		return desde(resultado, HttpStatus.OK);
	}

	/**
	 * Igual que {@link #desde(Resultado)}, pero con el estado de éxito que se le
	 * indique (por ejemplo, 201 al crear un recurso).
	 *
	 * @param resultado   resultado que devolvió el servicio
	 * @param estadoExito estado HTTP que se responde si fue exitoso
	 * @return respuesta HTTP equivalente
	 */
	public static ResponseEntity<Object> desde(Resultado<?> resultado, HttpStatus estadoExito) {
		if (resultado.esExito()) {
			return ResponseEntity.status(estadoExito).body(resultado.getDato());
		}
		HttpStatus estado = estadoDe(resultado.getCodigo());
		if (resultado.getDato() == null) {
			return mensaje(estado, resultado.getMensaje());
		}
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("message", resultado.getMensaje());
		cuerpo.put("dato", resultado.getDato());
		return ResponseEntity.status(estado).body(cuerpo);
	}

	/**
	 * Estado HTTP que corresponde a cada código de {@link Resultado}.
	 *
	 * @param codigo código de error del resultado
	 * @return 404, 400, 403, 409, 402 o 502 según el código; 500 si no lo
	 *         reconoce
	 */
	public static HttpStatus estadoDe(int codigo) {
		return switch (codigo) {
		case Resultado.NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
		case Resultado.DATOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
		case Resultado.SIN_PERMISO -> HttpStatus.FORBIDDEN;
		case Resultado.ESTADO_INVALIDO, Resultado.NO_DISPONIBLE -> HttpStatus.CONFLICT;
		case Resultado.PAGO_RECHAZADO -> HttpStatus.PAYMENT_REQUIRED;
		case Resultado.PAGO_ERROR -> HttpStatus.BAD_GATEWAY;
		default -> HttpStatus.INTERNAL_SERVER_ERROR;
		};
	}

	/**
	 * Id del usuario que hizo la petición. El principal que deja el filtro JWT es
	 * la entidad {@link Usuario}.
	 *
	 * @param authentication autenticación de la petición; puede ser {@code null}
	 * @return id del usuario, o {@code null} si no hay sesión
	 */
	public static Long idUsuario(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof Usuario usuario) {
			return usuario.getIdUsuario();
		}
		return null;
	}

	/**
	 * Indica si quien hizo la petición es administrador.
	 *
	 * @param authentication autenticación de la petición; puede ser {@code null}
	 * @return {@code true} si tiene el rol ADMIN
	 */
	public static boolean esAdmin(Authentication authentication) {
		return authentication != null && authentication.getAuthorities().stream()
				.anyMatch(a -> ROL_ADMIN.equals(a.getAuthority()));
	}

	/**
	 * Arma una página válida a partir de lo que mandó el cliente: la página nunca
	 * es negativa y el tamaño queda entre 1 y {@link #TAMANIO_MAXIMO}. También
	 * limita la página para que el desplazamiento (página × tamaño) quepa en un
	 * {@code int}; si no, la consulta revienta con un 500.
	 *
	 * @param pagina  número de página pedido (desde 0)
	 * @param tamanio cantidad de elementos por página pedida
	 * @param orden   orden de los resultados
	 * @return paginación lista para pasarla al repositorio
	 */
	public static Pageable pagina(int pagina, int tamanio, Sort orden) {
		int tamanioValido = Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO);
		int paginaValida = Math.min(Math.max(pagina, 0), Integer.MAX_VALUE / tamanioValido - 1);
		return PageRequest.of(paginaValida, tamanioValido, orden);
	}
}
