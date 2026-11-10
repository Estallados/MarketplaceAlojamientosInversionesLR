package co.edu.unbosque.marketplaceInversionesLR.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.exception.ManejadorGlobalExcepciones;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad stateless con JWT (RNF-003). Los roles salen del enum {@code Role}:
 * HUESPED, ANFITRION y ADMIN, que Spring ve como ROLE_HUESPED, ROLE_ANFITRION
 * y ROLE_ADMIN. En {@code hasRole(...)} y {@code @PreAuthorize} se escriben sin
 * el prefijo, por ejemplo {@code hasRole('ANFITRION')}.
 * <p>
 * Las rutas {@code /auth/**} son públicas y las demás exigen el token. Los
 * errores 401 y 403 responden en JSON con el mismo formato del resto de la
 * API.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/** Filtro que lee el JWT de cada petición y autentica al usuario. */
	private final JwtAuthenticationFilter jwtAuthFilter;

	/** Carga los usuarios por correo para el inicio de sesión. */
	private final UserDetailsService userDetailsService;

	/** Orígenes permitidos para CORS (el front en Angular). */
	private final List<String> origenesPermitidos;

	/** Registra en la auditoría los accesos denegados. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea la configuración.
	 *
	 * @param jwtAuthFilter      filtro de autenticación por JWT
	 * @param userDetailsService servicio que carga los usuarios
	 * @param origenesPermitidos orígenes permitidos para CORS; por defecto el
	 *                           front local
	 * @param auditoriaService   auditoría de los accesos denegados
	 */
	public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, UserDetailsService userDetailsService,
			@Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> origenesPermitidos,
			AuditoriaService auditoriaService) {
		this.jwtAuthFilter = jwtAuthFilter;
		this.userDetailsService = userDetailsService;
		this.origenesPermitidos = origenesPermitidos;
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Reglas de acceso de la API.
	 *
	 * @param http configuración de Spring Security
	 * @return cadena de filtros de seguridad
	 * @throws Exception si la configuración falla
	 */
	// java:S4502: CSRF se desactiva a propósito. La API no usa sesión ni cookies; el JWT viaja en
	// el header Authorization, que el navegador no envía solo, así que no hay ataque CSRF posible.
	@SuppressWarnings("java:S4502")
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
						// HU-19: va antes de las rutas públicas para que /alojamientos/{id} no la cubra
						.requestMatchers(HttpMethod.GET, "/alojamientos/mios").hasRole("ANFITRION")
						// Políticas de cancelación que el anfitrión elige al crear la publicación (HU-15)
						.requestMatchers(HttpMethod.GET, "/alojamientos/politicas").permitAll()
						// HU-18: el detalle es público; el de una publicación no ACTIVA lo filtra PublicacionService
						.requestMatchers(HttpMethod.GET, "/alojamientos/{id}").permitAll()
						// HU-20: el calendario es público; el servicio solo lo muestra si la publicación está
						// ACTIVA o si quien consulta es su anfitrión o un administrador
						.requestMatchers(HttpMethod.GET, "/alojamientos/*/disponibilidad").permitAll()
						// HU-21: la consulta por fechas también es pública, con la misma regla
						.requestMatchers(HttpMethod.GET, "/alojamientos/*/disponibilidad/consultar").permitAll()
						// HU-24: el anfitrión y el administrador configuran los porcentajes de tarifa
						.requestMatchers("/admin/tarifas").hasAnyRole("ADMIN", "ANFITRION")
						.requestMatchers("/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((req, res, e) -> escribirError(res,
								HttpServletResponse.SC_UNAUTHORIZED, "No autenticado: token ausente, inválido o expirado"))
						.accessDeniedHandler((req, res, e) -> accesoDenegado(req, res)))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationProvider(authenticationProvider())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	/**
	 * Proveedor que autentica con correo y contraseña: carga el usuario con
	 * {@link UserDetailsService} y compara la contraseña con BCrypt.
	 *
	 * @return proveedor de autenticación
	 */
	@Bean
	public AuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	/**
	 * Expone el {@link AuthenticationManager} para que el controlador de
	 * autenticación pueda validar el correo y la contraseña en el login.
	 *
	 * @param config configuración de autenticación de Spring
	 * @return administrador de autenticación
	 * @throws Exception si Spring no puede crearlo
	 */
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
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
	 * Responde 403 cuando el rol del usuario no tiene acceso a la ruta y lo deja
	 * en la auditoría con el usuario que lo intentó (HU-26, CA-137).
	 *
	 * @param req petición rechazada
	 * @param res respuesta HTTP
	 * @throws IOException si no se puede escribir la respuesta
	 */
	private void accesoDenegado(HttpServletRequest req, HttpServletResponse res) throws IOException {
		auditoriaService.registrar(RespuestaUtil.idUsuario(SecurityContextHolder.getContext().getAuthentication()),
				AuditoriaService.ACCESO_DENEGADO, null, null, ResultadoAuditoria.RECHAZADO,
				req.getMethod() + " " + req.getRequestURI());
		escribirError(res, HttpServletResponse.SC_FORBIDDEN, ManejadorGlobalExcepciones.SIN_PERMISO);
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
