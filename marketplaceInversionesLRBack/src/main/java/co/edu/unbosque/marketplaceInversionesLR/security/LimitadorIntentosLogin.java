package co.edu.unbosque.marketplaceInversionesLR.security;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Limita los intentos fallidos de inicio de sesión ({@code POST /auth/login})
 * por cuenta y por dirección IP. Cada intento verifica la contraseña con
 * BCrypt, que cuesta CPU a propósito: sin límite, un ataque de fuerza bruta
 * deja sin servicio a todos los usuarios.
 * <p>
 * Si la cuenta o la IP superaron el límite de intentos fallidos en su ventana
 * de tiempo, responde 429 con {@code Retry-After} <b>antes</b> de verificar la
 * contraseña. Solo cuentan como fallidos los 401 por credenciales inválidas;
 * el 401 que pide el código del segundo factor es parte del flujo normal. Un
 * inicio de sesión correcto borra los fallos de su cuenta.
 * </p>
 * Los contadores viven en memoria de la instancia.
 */
@Component
public class LimitadorIntentosLogin extends OncePerRequestFilter {

	/** Mensaje cuando se superan los intentos. */
	public static final String DEMASIADOS_INTENTOS = "Demasiados intentos fallidos de inicio de sesión. "
			+ "Espera unos minutos e intenta de nuevo.";

	/** Ruta protegida. */
	private static final String RUTA = "/auth/login";

	/** Cantidad de claves a partir de la cual se limpian las que ya vencieron. */
	private static final int LIMPIAR_DESDE = 10_000;

	/** Si el límite está activo. */
	private final boolean habilitado;

	/** Fallos permitidos por cuenta en su ventana. */
	private final int fallosPorCuenta;

	/** Ventana de los fallos por cuenta. */
	private final Duration ventanaCuenta;

	/** Fallos permitidos por IP en su ventana. */
	private final int fallosPorIp;

	/** Ventana de los fallos por IP. */
	private final Duration ventanaIp;

	/** Reloj (se reemplaza en las pruebas). */
	private final Clock reloj;

	/** Lee el correo del cuerpo de la petición. */
	private final ObjectMapper json = JsonMapper.builder().build();

	/** Fallos recientes por hash del correo. */
	private final Map<String, Fallos> porCuenta = new ConcurrentHashMap<>();

	/** Fallos recientes por IP. */
	private final Map<String, Fallos> porIp = new ConcurrentHashMap<>();

	/**
	 * Crea el limitador con la configuración de la aplicación.
	 *
	 * @param habilitado      {@code app.login.limite.habilitado}
	 * @param fallosPorCuenta fallos permitidos por cuenta
	 * @param minutosCuenta   ventana de los fallos por cuenta, en minutos
	 * @param fallosPorIp     fallos permitidos por IP
	 * @param minutosIp       ventana de los fallos por IP, en minutos
	 */
	@Autowired
	public LimitadorIntentosLogin(@Value("${app.login.limite.habilitado:true}") boolean habilitado,
			@Value("${app.login.limite.fallos-por-cuenta:5}") int fallosPorCuenta,
			@Value("${app.login.limite.minutos-cuenta:15}") long minutosCuenta,
			@Value("${app.login.limite.fallos-por-ip:30}") int fallosPorIp,
			@Value("${app.login.limite.minutos-ip:1}") long minutosIp) {
		this(habilitado, fallosPorCuenta, Duration.ofMinutes(minutosCuenta), fallosPorIp, Duration.ofMinutes(minutosIp),
				Clock.systemUTC());
	}

	/**
	 * Crea el limitador con un reloj propio.
	 *
	 * @param habilitado      si el límite está activo
	 * @param fallosPorCuenta fallos permitidos por cuenta
	 * @param ventanaCuenta   ventana de los fallos por cuenta
	 * @param fallosPorIp     fallos permitidos por IP
	 * @param ventanaIp       ventana de los fallos por IP
	 * @param reloj           reloj
	 */
	LimitadorIntentosLogin(boolean habilitado, int fallosPorCuenta, Duration ventanaCuenta, int fallosPorIp,
			Duration ventanaIp, Clock reloj) {
		this.habilitado = habilitado;
		this.fallosPorCuenta = fallosPorCuenta;
		this.ventanaCuenta = ventanaCuenta;
		this.fallosPorIp = fallosPorIp;
		this.ventanaIp = ventanaIp;
		this.reloj = reloj;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !habilitado || !"POST".equals(request.getMethod())
				|| !(request.getContextPath() + RUTA).equals(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		byte[] cuerpo = request.getInputStream().readAllBytes();
		String cuenta = cuenta(cuerpo);
		String ip = request.getRemoteAddr();
		Instant ahora = reloj.instant();

		long espera = Math.max(espera(porIp.get(ip), fallosPorIp, ventanaIp, ahora),
				cuenta == null ? 0 : espera(porCuenta.get(cuenta), fallosPorCuenta, ventanaCuenta, ahora));
		if (espera > 0) {
			rechazar(response, espera);
			return;
		}

		ContentCachingResponseWrapper respuesta = new ContentCachingResponseWrapper(response);
		chain.doFilter(new CuerpoLeido(request, cuerpo), respuesta);
		int estado = respuesta.getStatus();
		if (estado == HttpServletResponse.SC_UNAUTHORIZED && !pideCodigo(respuesta.getContentAsByteArray())) {
			registrarFallo(porIp, ip, ahora);
			if (cuenta != null) {
				registrarFallo(porCuenta, cuenta, ahora);
			}
		} else if (estado == HttpServletResponse.SC_OK && cuenta != null) {
			porCuenta.remove(cuenta);
		}
		respuesta.copyBodyToResponse();
	}

	/**
	 * Segundos que faltan para poder intentar de nuevo.
	 *
	 * @param fallos  fallos registrados, o {@code null}
	 * @param maximo  fallos permitidos
	 * @param ventana ventana de tiempo
	 * @param ahora   instante actual
	 * @return 0 si se puede intentar; si no, los segundos de espera
	 */
	private static long espera(Fallos fallos, int maximo, Duration ventana, Instant ahora) {
		return fallos == null ? 0 : fallos.espera(maximo, ventana, ahora);
	}

	/**
	 * Registra un intento fallido.
	 *
	 * @param fallos mapa de fallos
	 * @param clave  cuenta o IP
	 * @param ahora  instante del intento
	 */
	private void registrarFallo(Map<String, Fallos> fallos, String clave, Instant ahora) {
		if (fallos.size() > LIMPIAR_DESDE) {
			Duration ventana = fallos == porCuenta ? ventanaCuenta : ventanaIp;
			fallos.values().removeIf(f -> f.vencidos(ventana, ahora));
		}
		fallos.computeIfAbsent(clave, k -> new Fallos()).registrar(ahora);
	}

	/**
	 * Fallos de una cuenta o una IP, del más antiguo al más reciente. Cada
	 * instancia se sincroniza sobre sí misma.
	 */
	private static final class Fallos {

		/** Instantes de los fallos. */
		private final Deque<Instant> instantes = new ArrayDeque<>();

		/**
		 * Registra un fallo.
		 *
		 * @param ahora instante del fallo
		 */
		synchronized void registrar(Instant ahora) {
			instantes.addLast(ahora);
		}

		/**
		 * Segundos que faltan para que los fallos bajen del máximo.
		 *
		 * @param maximo  fallos permitidos
		 * @param ventana ventana de tiempo
		 * @param ahora   instante actual
		 * @return 0 si se puede intentar; si no, los segundos de espera
		 */
		synchronized long espera(int maximo, Duration ventana, Instant ahora) {
			descartarVencidos(ventana, ahora);
			if (instantes.size() < maximo) {
				return 0;
			}
			return Math.max(1, Duration.between(ahora, instantes.peekFirst().plus(ventana)).toSeconds());
		}

		/**
		 * Quita los fallos vencidos e indica si ya no queda ninguno.
		 *
		 * @param ventana ventana de tiempo
		 * @param ahora   instante actual
		 * @return {@code true} si no quedan fallos en la ventana
		 */
		synchronized boolean vencidos(Duration ventana, Instant ahora) {
			descartarVencidos(ventana, ahora);
			return instantes.isEmpty();
		}

		/**
		 * Quita los fallos que ya salieron de la ventana. Se llama con el
		 * monitor tomado.
		 *
		 * @param ventana ventana de tiempo
		 * @param ahora   instante actual
		 */
		private void descartarVencidos(Duration ventana, Instant ahora) {
			while (!instantes.isEmpty() && !instantes.peekFirst().plus(ventana).isAfter(ahora)) {
				instantes.pollFirst();
			}
		}
	}

	/**
	 * Hash del correo enviado, para no guardar correos en memoria.
	 *
	 * @param cuerpo cuerpo JSON de la petición
	 * @return el hash, o {@code null} si no trae un correo
	 */
	private String cuenta(byte[] cuerpo) {
		try {
			JsonNode correo = json.readTree(cuerpo).get("correo");
			return correo == null || !correo.isString() || correo.asString().isBlank() ? null
					: UsuarioService.hashCorreo(correo.asString());
		} catch (RuntimeException e) {
			// Cuerpo inválido: el controlador responde el 400
			return null;
		}
	}

	/**
	 * Indica si la respuesta pide el código del segundo factor (no es un fallo
	 * de contraseña).
	 *
	 * @param cuerpo cuerpo de la respuesta
	 * @return {@code true} si trae {@code requiereCodigoCorreo}
	 */
	private static boolean pideCodigo(byte[] cuerpo) {
		return new String(cuerpo, StandardCharsets.UTF_8).contains("\"requiereCodigoCorreo\"");
	}

	/**
	 * Responde 429 con el tiempo de espera.
	 *
	 * @param response respuesta
	 * @param segundos segundos de espera
	 * @throws IOException si no se puede escribir
	 */
	private static void rechazar(HttpServletResponse response, long segundos) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(segundos));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"message\":\"" + DEMASIADOS_INTENTOS + "\"}");
	}

	/**
	 * Petición cuyo cuerpo ya se leyó: lo entrega de nuevo al controlador.
	 */
	private static final class CuerpoLeido extends HttpServletRequestWrapper {

		/** Cuerpo de la petición. */
		private final byte[] cuerpo;

		/**
		 * Crea la petición.
		 *
		 * @param request petición original
		 * @param cuerpo  cuerpo ya leído
		 */
		CuerpoLeido(HttpServletRequest request, byte[] cuerpo) {
			super(request);
			this.cuerpo = cuerpo;
		}

		@Override
		public ServletInputStream getInputStream() {
			ByteArrayInputStream datos = new ByteArrayInputStream(cuerpo);
			return new ServletInputStream() {
				@Override
				public int read() {
					return datos.read();
				}

				@Override
				public int read(byte[] b, int desde, int cantidad) {
					return datos.read(b, desde, cantidad);
				}

				@Override
				public boolean isFinished() {
					return datos.available() == 0;
				}

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setReadListener(ReadListener listener) {
					throw new UnsupportedOperationException("Lectura síncrona");
				}
			};
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}
}
