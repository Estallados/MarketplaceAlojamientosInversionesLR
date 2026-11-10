package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST de usuarios. Ruta base: {@code /usuarios}. Todas sus rutas
 * exigen token JWT, y la identidad del usuario sale siempre del token, nunca
 * del cuerpo de la petición (CA-049). Contrato:
 * {@code docs/openapi/identidad.yaml}.
 */
@RestController
@RequestMapping("/usuarios")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Usuarios", description = "Perfil propio")
public class UsuarioController {

	/** Mensaje cuando el usuario no existe. */
	private static final String USUARIO_NO_ENCONTRADO = "Usuario no encontrado.";

	/** Servicio de usuarios. */
	private final UsuarioService usuarioService;

	/**
	 * Crea el controlador.
	 *
	 * @param usuarioService servicio de usuarios
	 */
	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	/**
	 * Consulta el perfil propio (HU-09, CA-042). Sin sesión responde 401
	 * (CA-044). Ruta: {@code GET /usuarios/perfil}.
	 *
	 * @param authentication usuario autenticado
	 * @return 200 con el perfil; 404 si no existe
	 */
	@Operation(summary = "Consultar el perfil propio")
	@ApiResponse(responseCode = "200", description = "Perfil del usuario autenticado")
	@ApiResponse(responseCode = "404", description = "Usuario no encontrado.")
	@GetMapping("/perfil")
	public ResponseEntity<Object> miPerfil(Authentication authentication) {
		UsuarioDTO usuario = usuarioService.obtenerUsuarioPorCorreo(authentication.getName());

		if (usuario == null) {
			return RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, USUARIO_NO_ENCONTRADO);
		}

		return ResponseEntity.ok(usuario);
	}

	/**
	 * Actualiza el perfil propio (HU-09, CA-046); solo cambia el nombre y el
	 * teléfono, los demás campos se ignoran. Ruta: {@code PUT /usuarios/perfil}.
	 *
	 * @param cambios        DTO con {@code nombre} y/o {@code telefono}
	 * @param authentication usuario autenticado
	 * @return 200 si se actualizó; 400 si los datos no son válidos (CA-047);
	 *         404 si no existe
	 */
	@Operation(summary = "Actualizar el perfil propio", description = "Solo cambia nombre y teléfono; los demás campos enviados se ignoran.")
	@ApiResponse(responseCode = "200", description = "Perfil actualizado correctamente.")
	@ApiResponse(responseCode = "400", description = "Envía un nombre no vacío (máximo 255 caracteres) y/o un teléfono de 7 a 15 dígitos.")
	@ApiResponse(responseCode = "404", description = "Usuario no encontrado.")
	@PutMapping("/perfil")
	public ResponseEntity<Object> actualizarPerfil(@RequestBody UsuarioDTO cambios, Authentication authentication) {

		return switch (usuarioService.actualizarPerfil(authentication.getName(), cambios.getNombre(),
				cambios.getTelefono())) {
		case 0 -> RespuestaUtil.mensaje(HttpStatus.OK, "Perfil actualizado correctamente.");
		case 1 -> RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, USUARIO_NO_ENCONTRADO);
		default -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST,
				"Envía un nombre no vacío (máximo 255 caracteres) y/o un teléfono de 7 a 15 dígitos.");
		};
	}

	/**
	 * Desactiva la cuenta propia (HU-10, CA-050); solo actúa sobre la cuenta del
	 * token (CA-053). Se puede reactivar luego con {@code POST /auth/reactivar}.
	 * Ruta: {@code PATCH /usuarios/perfil/desactivar}.
	 *
	 * @param authentication usuario autenticado
	 * @return 200 con {message, estado}; 403 si la cuenta no se puede
	 *         desactivar; 404 si no existe
	 */
	@Operation(summary = "Desactivar la cuenta propia", description = "Responde {message, estado}. Se puede reactivar luego con POST /auth/reactivar.")
	@ApiResponse(responseCode = "200", description = "Cuenta desactivada correctamente: {message, estado}")
	@ApiResponse(responseCode = "403", description = "Esta cuenta no se puede desactivar.")
	@ApiResponse(responseCode = "404", description = "Usuario no encontrado.")
	@PatchMapping("/perfil/desactivar")
	public ResponseEntity<Object> desactivarCuenta(Authentication authentication) {
		return switch (usuarioService.desactivarCuenta(authentication.getName())) {
		case 0 -> ResponseEntity
				.ok(Map.of("message", "Cuenta desactivada correctamente.", "estado", EstadoUsuario.INACTIVO.name()));
		case 1 -> RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, USUARIO_NO_ENCONTRADO);
		default -> RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, "Esta cuenta no se puede desactivar.");
		};
	}
}
