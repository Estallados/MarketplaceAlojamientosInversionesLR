package co.edu.unbosque.marketplaceInversionesLR.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad de la API (RNF-003): sin sesión, con las rutas {@code /auth/**}
 * públicas y las demás protegidas. Los errores 401 y 403 responden en JSON con
 * el mismo formato del resto de la API.
 */
@Configuration
public class SecurityConfig {

	/** Orígenes permitidos para CORS (el front en Angular). */
	private final List<String> origenesPermitidos;

	/**
	 * Crea la configuración.
	 *
	 * @param origenesPermitidos orígenes permitidos para CORS; por defecto el
	 *                           front local
	 */
	public SecurityConfig(@Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> origenesPermitidos) {
		this.origenesPermitidos = origenesPermitidos;
	}

	/**
	 * Reglas de acceso de la API.
	 *
	 * @param http configuración de Spring Security
	 * @return cadena de filtros de seguridad
	 * @throws Exception si la configuración falla
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				// API REST sin sesión ni cookies: no aplica la protección CSRF
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests(auth -> auth
						// Documentación de la API (Swagger UI)
						.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.requestMatchers("/auth/**").permitAll()
						// Spring reenvía los errores a /error; si se protege, cualquier error en
						// una ruta pública se convierte en un 401 engañoso
						.requestMatchers("/error").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((req, res, e) -> escribirError(res,
								HttpServletResponse.SC_UNAUTHORIZED, "No autenticado: token ausente, inválido o expirado"))
						.accessDeniedHandler((req, res, e) -> escribirError(res, HttpServletResponse.SC_FORBIDDEN,
								"No tienes permisos para esta operación")))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.build();
	}

	/**
	 * Codificador de contraseñas.
	 *
	 * @return BCrypt
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * CORS para el front.
	 *
	 * @return configuración de CORS
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origenesPermitidos.stream().map(String::trim).toList());
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
		config.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);
		return source;
	}

	/**
	 * Escribe el error de seguridad en JSON. Los mensajes son constantes sin
	 * comillas, así que no hace falta escaparlos.
	 *
	 * @param res     respuesta HTTP
	 * @param status  código HTTP (401 o 403)
	 * @param message mensaje para el usuario
	 * @throws IOException si no se puede escribir la respuesta
	 */
	private static void escribirError(HttpServletResponse res, int status, String message) throws IOException {
		res.setStatus(status);
		res.setContentType(MediaType.APPLICATION_JSON_VALUE);
		res.setCharacterEncoding(StandardCharsets.UTF_8.name());
		res.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
	}
}
