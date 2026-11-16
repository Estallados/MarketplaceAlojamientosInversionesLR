package co.edu.unbosque.marketplaceInversionesLR.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

/**
 * Pruebas del límite de intentos fallidos de inicio de sesión.
 */
class LimitadorIntentosLoginTest {

	/** Reloj que se adelanta a mano. */
	private final RelojManual reloj = new RelojManual();

	/** Veces que la petición llegó al controlador (y a BCrypt). */
	private final AtomicInteger verificaciones = new AtomicInteger();

	/** Límite de 3 fallos por cuenta en 15 min y 5 por IP en 1 min. */
	private final LimitadorIntentosLogin limitador = new LimitadorIntentosLogin(true, 3, Duration.ofMinutes(15), 5,
			Duration.ofMinutes(1), reloj);

	@Test
	@DisplayName("Superados los fallos de una cuenta responde 429 con Retry-After sin verificar la contraseña")
	void bloqueaLaCuenta() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(login("ana@correo.com", "1.1.1." + i, 401, "{\"message\":\"Correo o contraseña inválidos.\"}")
					.getStatus()).isEqualTo(401);
		}

		MockHttpServletResponse bloqueada = login("ANA@correo.com ", "2.2.2.2", 200, "{\"token\":\"t\"}");

		assertThat(bloqueada.getStatus()).isEqualTo(429);
		assertThat(bloqueada.getHeader("Retry-After")).isEqualTo("900");
		assertThat(bloqueada.getContentAsString(StandardCharsets.UTF_8))
				.contains(LimitadorIntentosLogin.DEMASIADOS_INTENTOS);
		assertThat(verificaciones.get()).isEqualTo(3);
		// Otra cuenta desde otra IP no se ve afectada
		assertThat(login("beto@correo.com", "3.3.3.3", 200, "{\"token\":\"t\"}").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Superados los fallos de una IP, cualquier cuenta desde esa IP recibe 429")
	void bloqueaLaIp() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("usuario" + i + "@correo.com", "9.9.9.9", 401, "{}");
		}

		assertThat(login("otra@correo.com", "9.9.9.9", 200, "{}").getStatus()).isEqualTo(429);
		assertThat(login("otra@correo.com", "8.8.8.8", 200, "{}").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("El 401 que pide el código del segundo factor no cuenta como fallo")
	void elCodigoDelSegundoFactorNoEsFallo() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("ana@correo.com", "1.1.1.1", 401, "{\"message\":\"Te enviamos un código\",\"requiereCodigoCorreo\":true}");
		}

		assertThat(login("ana@correo.com", "1.1.1.1", 200, "{}").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Un inicio de sesión correcto borra los fallos de la cuenta, y los fallos vencen con la ventana")
	void exitoYVentanaLiberan() throws Exception {
		login("ana@correo.com", "1.1.1.1", 401, "{}");
		login("ana@correo.com", "1.1.1.2", 401, "{}");
		login("ana@correo.com", "1.1.1.3", 200, "{}");
		login("ana@correo.com", "1.1.1.4", 401, "{}");
		login("ana@correo.com", "1.1.1.5", 401, "{}");
		assertThat(login("ana@correo.com", "1.1.1.6", 200, "{}").getStatus()).isEqualTo(200);

		for (int i = 0; i < 3; i++) {
			login("beto@correo.com", "2.2.2." + i, 401, "{}");
		}
		assertThat(login("beto@correo.com", "2.2.2.9", 200, "{}").getStatus()).isEqualTo(429);
		reloj.avanzar(Duration.ofMinutes(15));
		assertThat(login("beto@correo.com", "2.2.2.9", 200, "{}").getStatus()).isEqualTo(200);
	}

	@Test
	@DisplayName("Deshabilitado u otras rutas no se limitan, y el controlador recibe el cuerpo completo")
	void deshabilitadoYOtrasRutas() throws Exception {
		LimitadorIntentosLogin apagado = new LimitadorIntentosLogin(false, 1, Duration.ofMinutes(1), 1,
				Duration.ofMinutes(1), reloj);
		for (int i = 0; i < 3; i++) {
			assertThat(ejecutar(apagado, peticion("/auth/login", "ana@correo.com", "1.1.1.1"), 401, "{}").getStatus())
					.isEqualTo(401);
		}
		for (int i = 0; i < 6; i++) {
			assertThat(ejecutar(limitador, peticion("/auth/registro", "ana@correo.com", "1.1.1.1"), 401, "{}")
					.getStatus()).isEqualTo(401);
		}
	}

	/**
	 * Inicia sesión a través del limitador.
	 *
	 * @param correo    correo enviado
	 * @param ip        IP del cliente
	 * @param estado    estado que respondería el controlador
	 * @param respuesta cuerpo que respondería el controlador
	 * @return la respuesta final
	 */
	private MockHttpServletResponse login(String correo, String ip, int estado, String respuesta)
			throws ServletException, IOException {
		return ejecutar(limitador, peticion("/auth/login", correo, ip), estado, respuesta);
	}

	/**
	 * Pasa la petición por el filtro con un controlador simulado que comprueba
	 * que recibe el cuerpo original.
	 */
	private MockHttpServletResponse ejecutar(LimitadorIntentosLogin filtro, MockHttpServletRequest peticion, int estado,
			String respuesta) throws ServletException, IOException {
		String esperado = peticion.getContentAsString();
		MockHttpServletResponse salida = new MockHttpServletResponse();
		FilterChain controlador = (req, res) -> {
			verificaciones.incrementAndGet();
			assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(esperado);
			((jakarta.servlet.http.HttpServletResponse) res).setStatus(estado);
			res.getWriter().write(respuesta);
		};
		filtro.doFilter(peticion, salida, controlador);
		return salida;
	}

	/**
	 * Petición POST de inicio de sesión.
	 */
	private static MockHttpServletRequest peticion(String ruta, String correo, String ip) {
		MockHttpServletRequest peticion = new MockHttpServletRequest("POST", ruta);
		peticion.setRemoteAddr(ip);
		peticion.setContentType("application/json");
		peticion.setCharacterEncoding("UTF-8");
		peticion.setContent(("{\"correo\":\"" + correo + "\",\"contrasenia\":\"Clave#123\"}")
				.getBytes(StandardCharsets.UTF_8));
		return peticion;
	}

	/**
	 * Reloj que solo avanza cuando la prueba lo pide.
	 */
	private static final class RelojManual extends Clock {

		/** Instante actual. */
		private Instant ahora = Instant.parse("2026-11-15T12:00:00Z");

		void avanzar(Duration duracion) {
			ahora = ahora.plus(duracion);
		}

		@Override
		public Instant instant() {
			return ahora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}
}
