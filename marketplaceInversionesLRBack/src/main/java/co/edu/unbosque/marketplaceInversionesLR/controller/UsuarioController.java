package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST de usuarios. Ruta base: {@code /usuarios}. Todas sus rutas
 * exigen token JWT, y la identidad del usuario sale siempre del token, nunca
 * del cuerpo de la petición (CA-049). El administrador además consulta
 * cualquier usuario, con su estado y el motivo de la restricción (HU-12).
 * Contratos: {@code docs/openapi/identidad.yaml} y
 * {@code docs/openapi/administracion-reportes.yaml}.
 */
@RestController
@RequestMapping("/usuarios")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Usuarios", description = "Perfil propio y consulta de usuarios")
public class UsuarioController {

	/** Mensaje cuando el usuario no existe. */
	private static final String USUARIO_NO_ENCONTRADO = UsuarioService.NO_ENCONTRADO;

	/** Mensaje cuando se creó el administrador. */
	static final String ADMIN_CREADO = "Administrador creado correctamente. Ya puede iniciar sesión.";

	/** Mensaje cuando no se pudo guardar el administrador. */
	static final String ADMIN_NO_CREADO = "No se pudo crear el administrador.";

	/** Mensaje cuando el correo ya está registrado. */
	static final String CORREO_DUPLICADO = "Ya existe un usuario con ese correo electrónico.";

	/** Mensaje cuando faltan datos o no son válidos (igual que en el registro). */
	static final String DATOS_INVALIDOS = "Datos incompletos o inválidos: nombre (máximo 255 caracteres), correo "
			+ "válido (máximo 150) y contraseña son obligatorios; el teléfono, si se envía, debe tener de 7 a 15 "
			+ "dígitos.";

	/** Mensaje cuando la contraseña no es segura (igual que en el registro). */
	static final String CONTRASENIA_DEBIL = "La contraseña debe tener entre 8 y 64 caracteres, una mayúscula, una "
			+ "minúscula, un número y un símbolo.";

	/** Mensaje cuando un usuario consulta a otro sin ser administrador. */
	public static final String SIN_PERMISO_CONSULTA = "No tienes permisos para consultar este usuario.";

	/** Mensaje cuando falta el correo a buscar. */
	public static final String FALTA_CORREO = "Falta el parámetro correo.";

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

	/**
	 * Crea otro administrador (solo administradores). El registro público nunca
	 * crea administradores y el rol enviado aquí se ignora. Ruta:
	 * {@code POST /usuarios/crearadministrador}.
	 *
	 * @param datos          nombre, correo, contraseña y, si se quiere, teléfono
	 *                       del nuevo administrador
	 * @param authentication administrador autenticado
	 * @return 201 si se creó; 400 si los datos o la contraseña no son válidos;
	 *         409 si el correo ya existe; 500 si falló el guardado
	 */
	@Operation(summary = "Crear un administrador (solo ADMIN)", description = "El registro público nunca crea "
			+ "administradores; el rol enviado se ignora. El correo del nuevo administrador queda verificado, "
			+ "así que puede iniciar sesión de inmediato.")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "201", description = ADMIN_CREADO)
	@ApiResponse(responseCode = "400", description = DATOS_INVALIDOS + " | " + CONTRASENIA_DEBIL)
	@ApiResponse(responseCode = "409", description = CORREO_DUPLICADO)
	@ApiResponse(responseCode = "500", description = ADMIN_NO_CREADO)
	@PostMapping("/crearadministrador")
	public ResponseEntity<Object> crearAdministrador(@RequestBody UsuarioDTO datos, Authentication authentication) {
		return switch (usuarioService.crearAdministrador(datos, RespuestaUtil.idUsuario(authentication))) {
		case UsuarioService.CREADO -> RespuestaUtil.mensaje(HttpStatus.CREATED, ADMIN_CREADO);
		case UsuarioService.CORREO_DUPLICADO -> RespuestaUtil.mensaje(HttpStatus.CONFLICT, CORREO_DUPLICADO);
		case UsuarioService.CONTRASENIA_DEBIL -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, CONTRASENIA_DEBIL);
		case UsuarioService.DATOS_INVALIDOS -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS);
		default -> RespuestaUtil.mensaje(HttpStatus.INTERNAL_SERVER_ERROR, ADMIN_NO_CREADO);
		};
	}

	/**
	 * Todos los usuarios, ordenados por id, sin paginar (HU-12, solo
	 * administradores). Para listados paginados está
	 * {@code GET /admin/usuarios}. Ruta: {@code GET /usuarios/listar}.
	 *
	 * @return 200 con todos los usuarios
	 */
	@Operation(summary = "Todos los usuarios, sin paginar (ADMIN)", description = "Para listados paginados usa GET "
			+ "/admin/usuarios. Cada usuario trae su estado y, si está RESTRINGIDO, el motivoRestriccion.")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "200", description = "Todos los usuarios")
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@GetMapping("/listar")
	public ResponseEntity<List<UsuarioDTO>> listarTodos() {
		return ResponseEntity.ok(usuarioService.listarTodos());
	}

	/**
	 * Consulta un usuario por id: el propio o, si es administrador, cualquiera,
	 * con su estado y el motivo de la restricción (HU-12, CA-058). Ruta:
	 * {@code GET /usuarios/obtener/{id}}.
	 *
	 * @param id             usuario a consultar
	 * @param authentication usuario autenticado
	 * @return 200 con el usuario; 403 si no puede consultarlo; 404 si no existe
	 */
	@Operation(summary = "Consultar un usuario por id (el propio o ADMIN)", description = "Un usuario consulta solo su "
			+ "propio id; un administrador, cualquiera.")
	@ApiResponse(responseCode = "200", description = "Usuario con su estado y, si está RESTRINGIDO, el motivo")
	@ApiResponse(responseCode = "403", description = SIN_PERMISO_CONSULTA)
	@ApiResponse(responseCode = "404", description = "Usuario no encontrado.")
	@GetMapping("/obtener/{id}")
	public ResponseEntity<Object> obtenerUsuarioPorId(@PathVariable Long id, Authentication authentication) {
		if (!RespuestaUtil.esAdmin(authentication)
				&& !Objects.equals(RespuestaUtil.idUsuario(authentication), id)) {
			return RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, SIN_PERMISO_CONSULTA);
		}
		UsuarioDTO usuario = usuarioService.obtenerPorId(id);
		if (usuario == null) {
			return RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, USUARIO_NO_ENCONTRADO);
		}
		return ResponseEntity.ok(usuario);
	}

	/**
	 * Consulta un usuario por correo, sin distinguir mayúsculas ni espacios en
	 * los extremos (HU-12, solo administradores). Ruta:
	 * {@code GET /usuarios/obtenercorreo?correo=}.
	 *
	 * @param correo correo a buscar
	 * @return 200 con el usuario; 400 si falta el correo; 404 si no existe
	 */
	@Operation(summary = "Consultar un usuario por correo (ADMIN)", description = "El correo se compara sin distinguir "
			+ "mayúsculas ni espacios en los extremos.")
	@PreAuthorize("hasRole('ADMIN')")
	@ApiResponse(responseCode = "200", description = "Usuario con su estado y, si está RESTRINGIDO, el motivo")
	@ApiResponse(responseCode = "400", description = FALTA_CORREO)
	@ApiResponse(responseCode = "403", description = "No tienes permisos para esta operación")
	@ApiResponse(responseCode = "404", description = "Usuario no encontrado.")
	@GetMapping("/obtenercorreo")
	public ResponseEntity<Object> obtenerUsuarioPorCorreo(
			@Parameter(description = "Correo a buscar, por ejemplo ana@correo.com") @RequestParam(required = false) String correo) {
		if (correo == null || correo.isBlank()) {
			return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, FALTA_CORREO);
		}
		UsuarioDTO usuario = usuarioService.obtenerUsuarioPorCorreo(correo);
		if (usuario == null) {
			return RespuestaUtil.mensaje(HttpStatus.NOT_FOUND, USUARIO_NO_ENCONTRADO);
		}
		return ResponseEntity.ok(usuario);
	}
}
