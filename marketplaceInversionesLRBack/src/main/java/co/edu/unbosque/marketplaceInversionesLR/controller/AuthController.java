package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.VerificacionCorreoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.service.VerificacionCorreoService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Autenticación (servicio S01). Ruta base {@code /auth}; todas sus rutas son
 * públicas. Contrato: {@code docs/openapi/identidad.yaml}.
 * <p>
 * Crear una cuenta tiene dos pasos: {@code POST /auth/register} crea la cuenta
 * y envía un código al correo, y {@code POST /auth/verificar} confirma el
 * código y entrega el token de sesión.
 * </p>
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro y verificación del correo")
public class AuthController {

	/** Mensaje cuando la contraseña no cumple las reglas. */
	private static final String MENSAJE_CONTRASENIA_DEBIL = "La contraseña debe tener entre 8 y 64 caracteres, "
			+ "una mayúscula, una minúscula, un número y un símbolo.";

	/** Reglas de negocio de usuarios. */
	private final UsuarioService usuarioService;

	/** Verificación del correo con código. */
	private final VerificacionCorreoService verificacionCorreoService;

	/** Genera el token JWT. */
	private final JwtUtil jwtUtil;

	/** Carga el usuario verificado para emitir su token. */
	private final UserDetailsService userDetailsService;

	/**
	 * Crea el controlador.
	 *
	 * @param usuarioService            servicio de usuarios
	 * @param verificacionCorreoService servicio de verificación del correo
	 * @param jwtUtil                   utilidad de tokens JWT
	 * @param userDetailsService        servicio que carga usuarios por correo
	 */
	public AuthController(UsuarioService usuarioService, VerificacionCorreoService verificacionCorreoService,
			JwtUtil jwtUtil, UserDetailsService userDetailsService) {
		this.usuarioService = usuarioService;
		this.verificacionCorreoService = verificacionCorreoService;
		this.jwtUtil = jwtUtil;
		this.userDetailsService = userDetailsService;
	}

	/**
	 * Registra un usuario nuevo como HUESPED o ANFITRION (HU-06, RF-001). La
	 * cuenta queda activa pero con el correo sin verificar: se envía un código de
	 * 6 dígitos al correo y el token se entrega al confirmarlo con
	 * {@code POST /auth/verificar}. Ruta: {@code POST /auth/register}.
	 *
	 * @param registerRequest datos del usuario
	 * @return 201 con {message, correo, requiereVerificacion} (CA-030); 400 si
	 *         faltan datos o son inválidos (CA-031) o la contraseña es débil
	 *         (CA-135); 403 si se pide el rol ADMIN; 409 si el correo ya existe;
	 *         500 si falla el guardado
	 */
	@Operation(summary = "Registrarse como HUESPED o ANFITRION", description = "Crea la cuenta y envía un código de "
			+ "verificación al correo. Devuelve {message, correo, requiereVerificacion}; el token se obtiene en "
			+ "/auth/verificar.")
	@ApiResponse(responseCode = "201", description = "Usuario registrado: {message, correo, requiereVerificacion=true}; "
			+ "se envió el código")
	@ApiResponse(responseCode = "400", description = "Datos incompletos o inválidos: nombre, correo válido y contraseña "
			+ "son obligatorios; el teléfono, si se envía, debe tener de 7 a 15 dígitos. | " + MENSAJE_CONTRASENIA_DEBIL)
	@ApiResponse(responseCode = "403", description = "Solo puedes registrarte como HUESPED o ANFITRION.")
	@ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese correo electrónico.")
	@ApiResponse(responseCode = "500", description = "Error desconocido al registrar el usuario.")
	@PostMapping("/register")
	public ResponseEntity<Object> register(@RequestBody UsuarioDTO registerRequest) {
		return switch (usuarioService.create(registerRequest)) {
		case UsuarioService.CREADO -> {
			Map<String, Object> respuesta = new LinkedHashMap<>();
			respuesta.put("message", "Usuario registrado. Te enviamos un código de verificación al correo; "
					+ "vence en " + VerificacionCorreoService.MINUTOS_VIGENCIA + " minutos.");
			respuesta.put("correo", UsuarioService.normalizarCorreo(registerRequest.getCorreo()));
			respuesta.put("requiereVerificacion", true);
			yield ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
		}
		case UsuarioService.CORREO_DUPLICADO ->
			RespuestaUtil.mensaje(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo electrónico.");
		case UsuarioService.DATOS_INVALIDOS -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST,
				"Datos incompletos o inválidos: nombre (máximo 255 caracteres), correo válido (máximo 150) y "
						+ "contraseña son obligatorios; el teléfono, si se envía, debe tener de 7 a 15 dígitos.");
		case UsuarioService.CONTRASENIA_DEBIL -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, MENSAJE_CONTRASENIA_DEBIL);
		case UsuarioService.ROL_NO_PERMITIDO ->
			RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, "Solo puedes registrarte como HUESPED o ANFITRION.");
		default -> RespuestaUtil.mensaje(HttpStatus.INTERNAL_SERVER_ERROR, "Error desconocido al registrar el usuario.");
		};
	}

	/**
	 * Confirma el correo con el código de verificación e inicia la sesión. Ruta:
	 * {@code POST /auth/verificar}.
	 *
	 * @param datos correo y código
	 * @return 200 con {message, token, rol, correo}; 400 si faltan datos o el
	 *         código no es válido o venció; 409 si el correo ya estaba
	 *         verificado; 429 si se agotaron los intentos
	 */
	@Operation(summary = "Verificar el correo con el código recibido", description = "El código vence a los 15 minutos "
			+ "y admite 5 intentos. Devuelve el token de sesión.")
	@ApiResponse(responseCode = "200", description = "Correo verificado: {message, token, rol, correo}")
	@ApiResponse(responseCode = "400", description = "El código no es válido o ya venció. Revísalo o solicita uno nuevo. | "
			+ "Debes enviar el correo y el código.")
	@ApiResponse(responseCode = "409", description = "El correo ya está verificado. Inicia sesión.")
	@ApiResponse(responseCode = "429", description = "Superaste los intentos permitidos para este código. Solicita uno "
			+ "nuevo.")
	@PostMapping("/verificar")
	public ResponseEntity<Object> verificar(@RequestBody VerificacionCorreoDTO datos) {
		return switch (verificacionCorreoService.verificar(datos.getCorreo(), datos.getCodigo())) {
		case VerificacionCorreoService.VERIFICADO ->
			respuestaVerificada(userDetailsService.loadUserByUsername(UsuarioService.normalizarCorreo(datos.getCorreo())));
		case VerificacionCorreoService.CODIGO_INVALIDO ->
			RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "El código no es válido o ya venció. Revísalo o solicita uno nuevo.");
		case VerificacionCorreoService.INTENTOS_AGOTADOS -> RespuestaUtil.mensaje(HttpStatus.TOO_MANY_REQUESTS,
				"Superaste los intentos permitidos para este código. Solicita uno nuevo.");
		case VerificacionCorreoService.YA_VERIFICADO ->
			RespuestaUtil.mensaje(HttpStatus.CONFLICT, "El correo ya está verificado. Inicia sesión.");
		default -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el correo y el código.");
		};
	}

	/**
	 * Envía un código de verificación nuevo. Responde igual exista o no la
	 * cuenta, para no revelarlo. Ruta: {@code POST /auth/reenviarcodigo}.
	 *
	 * @param solicitud DTO con el correo
	 * @return 200 si se procesó; 400 si falta el correo; 429 si el último código
	 *         se pidió hace menos de un minuto
	 */
	@Operation(summary = "Reenviar el código de verificación del correo", description = "Invalida el código anterior. "
			+ "Solo se permite una vez por minuto.")
	@ApiResponse(responseCode = "200", description = "Si la cuenta existe y no está verificada, te enviamos un código "
			+ "nuevo.")
	@ApiResponse(responseCode = "400", description = "Debes enviar el correo.")
	@ApiResponse(responseCode = "429", description = "Espera un minuto antes de pedir otro código.")
	@PostMapping("/reenviarcodigo")
	public ResponseEntity<Object> reenviarCodigo(@RequestBody UsuarioDTO solicitud) {
		return switch (verificacionCorreoService.reenviar(solicitud.getCorreo())) {
		case VerificacionCorreoService.ENVIADO ->
			RespuestaUtil.mensaje(HttpStatus.OK, "Si la cuenta existe y no está verificada, te enviamos un código nuevo.");
		case VerificacionCorreoService.ESPERAR ->
			RespuestaUtil.mensaje(HttpStatus.TOO_MANY_REQUESTS, "Espera un minuto antes de pedir otro código.");
		default -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el correo.");
		};
	}

	/**
	 * Respuesta de un correo recién verificado. Si la cuenta tiene segundo
	 * factor no se entrega token aquí: debe iniciar sesión con su código.
	 *
	 * @param usuario cuenta verificada
	 * @return 200 con {message, token, rol, correo}, o sin token si tiene
	 *         segundo factor
	 */
	private ResponseEntity<Object> respuestaVerificada(UserDetails usuario) {
		if (usuario instanceof Usuario u && Boolean.TRUE.equals(u.getTotpHabilitado())) {
			return RespuestaUtil.mensaje(HttpStatus.OK, "Correo verificado. Inicia sesión con tu código.");
		}
		String rol = usuario.getAuthorities().stream().findFirst()
				.map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).orElse(null);
		Map<String, String> respuesta = new LinkedHashMap<>();
		respuesta.put("message", "Correo verificado correctamente.");
		respuesta.put("token", jwtUtil.generateToken(usuario));
		respuesta.put("rol", rol);
		respuesta.put("correo", usuario.getUsername());
		return ResponseEntity.ok(respuesta);
	}
}
