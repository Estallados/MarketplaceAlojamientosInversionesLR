package co.edu.unbosque.marketplaceInversionesLR.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticación JWT que intercepta las solicitudes HTTP para validar
 * el token JWT. Este filtro se ejecuta una vez por cada solicitud y es
 * responsable de:
 * <ul>
 * <li>Verificar la presencia y validez del token JWT en el encabezado de
 * autorización.</li>
 * <li>Extraer el nombre de usuario del token y cargar los detalles del
 * usuario.</li>
 * <li>Establecer la autenticación en el contexto de seguridad de Spring si el
 * token es válido.</li>
 * </ul>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	/** Utilidad para manejar operaciones relacionadas con JWT. */
	private final JwtUtil jwtUtil;

	/** Servicio para cargar los detalles del usuario. */
	private final UserDetailsService userDetailsService;

	/**
	 * Constructor del filtro de autenticación JWT.
	 *
	 * @param jwtUtil            Utilidad para manejar JWT.
	 * @param userDetailsService Servicio para cargar los detalles del usuario.
	 */
	public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
		this.jwtUtil = jwtUtil;
		this.userDetailsService = userDetailsService;
	}

	/**
	 * Método que filtra cada solicitud HTTP para validar el token JWT. Excluye
	 * ciertas rutas públicas y procesa la autenticación para las rutas protegidas.
	 *
	 * @param request     Solicitud HTTP entrante.
	 * @param response    Respuesta HTTP saliente.
	 * @param filterChain Cadena de filtros para continuar el procesamiento de la
	 *                    solicitud.
	 * @throws ServletException Si ocurre un error en el procesamiento del filtro.
	 * @throws IOException      Si ocurre un error de entrada/salida.
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getRequestURI();

		if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/api-docs")
				|| path.startsWith("/swagger-resources") || path.startsWith("/webjars") || path.startsWith("/auth")) {

			filterChain.doFilter(request, response);
			return;
		}

		final String authorizationHeader = request.getHeader("Authorization");
		String username = null;
		String jwt = null;

		if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
			jwt = authorizationHeader.substring(7);
			try {
				// El sujeto del token es el correo en claro (Usuario.getUsername())
				username = jwtUtil.extractUsername(jwt);
			} catch (JwtException | IllegalArgumentException e) {
				// Token mal formado, con firma inválida o expirado: la petición sigue
				// sin autenticar y SecurityConfig responde 401 si la ruta lo exige
				if (logger.isDebugEnabled()) {
					logger.debug("Token JWT inválido: " + e.getMessage());
				}
			}
		}

		if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
				// Un usuario desactivado o restringido no debe seguir usando un token viejo
				if (jwtUtil.validateToken(jwt, userDetails) && userDetails.isEnabled()
						&& userDetails.isAccountNonLocked()) {
					UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
							userDetails, null, userDetails.getAuthorities());
					authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authenticationToken);
				}
			} catch (UsernameNotFoundException e) {
				if (logger.isDebugEnabled()) {
					logger.debug("El usuario del token ya no existe: " + username);
				}
			}
		}

		filterChain.doFilter(request, response);
	}
}
