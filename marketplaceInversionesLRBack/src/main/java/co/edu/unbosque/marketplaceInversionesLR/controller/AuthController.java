package co.edu.unbosque.marketplaceInversionesLR.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.InicioGoogleDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.LoginDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.RestablecerContraseniaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.VerificacionCorreoDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.AuthService;
import co.edu.unbosque.marketplaceInversionesLR.service.InicioGoogleService;
import co.edu.unbosque.marketplaceInversionesLR.service.SegundoFactorService;
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
 * código y entrega el token de sesión. El inicio de sesión
 * ({@code POST /auth/login}) devuelve un token JWT listo para usar.
 * </p>
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticación", description = "Registro, verificación del correo e inicio de sesión")
public class AuthController {

	/** Mensaje cuando la contraseña no cumple las reglas. */
	private static final String MENSAJE_CONTRASENIA_DEBIL = "La contraseña debe tener entre 8 y 64 caracteres, "
			+ "una mayúscula, una minúscula, un número y un símbolo.";

	/** Valida las credenciales contra la base de datos (BCrypt). */
	private final AuthenticationManager authenticationManager;

	/** Reglas de negocio de usuarios. */
	private final UsuarioService usuarioService;

	/** Verificación del correo con código. */
	private final VerificacionCorreoService verificacionCorreoService;

	/** Genera el token JWT. */
	private final JwtUtil jwtUtil;

	/** Carga el usuario verificado para emitir su token. */
	private final UserDetailsService userDetailsService;

	/** Registro de auditoría de los inicios de sesión. */
	private final AuditoriaService auditoriaService;

	/** Inicio de sesión y registro con una cuenta de Google. */
	private final InicioGoogleService inicioGoogleService;

	/** Recuperación de contraseña. */
	private final AuthService authService;

	/**
	 * Crea el controlador.
	 *
	 * @param authenticationManager     gestor de autenticación de Spring Security
	 * @param usuarioService            servicio de usuarios
	 * @param verificacionCorreoService servicio de verificación del correo
	 * @param jwtUtil                   utilidad de tokens JWT
	 * @param userDetailsService        servicio que carga usuarios por correo
	 * @param auditoriaService          servicio de auditoría
	 * @param inicioGoogleService       servicio de inicio de sesión con Google
	 * @param authService               servicio de recuperación de contraseña
	 */
	public AuthController(AuthenticationManager authenticationManager, UsuarioService usuarioService,
			VerificacionCorreoService verificacionCorreoService, JwtUtil jwtUtil, UserDetailsService userDetailsService,
			AuditoriaService auditoriaService, InicioGoogleService inicioGoogleService, AuthService authService) {
		this.authenticationManager = authenticationManager;
		this.usuarioService = usuarioService;
		this.verificacionCorreoService = verificacionCorreoService;
		this.jwtUtil = jwtUtil;
		this.userDetailsService = userDetailsService;
		this.auditoriaService = auditoriaService;
		this.inicioGoogleService = inicioGoogleService;
		this.authService = authService;
	}

	/**
	 * Inicia sesión (HU-07, RF-008). Ruta: {@code POST /auth/login}.
	 *
	 * @param loginRequest DTO con {@code correo}, {@code contrasenia} y,
	 *                     opcionalmente, {@code codigo}
	 * @return 200 con un JSON {token, rol, correo} (CA-034); 400 si faltan datos;
	 *         401 si las credenciales son inválidas (CA-035) o falta o es
	 *         incorrecto el código del segundo factor o del correo; 429 si se
	 *         agotaron los intentos del código del correo; 403 si la cuenta está
	 *         restringida o desactivada, o si el correo no está verificado (con
	 *         {@code requiereVerificacion=true})
	 */
	@Operation(summary = "Iniciar sesión", description = "Devuelve {token, rol, correo}. Si la cuenta tiene "
			+ "el segundo factor habilitado (totpHabilitado), se envía un código al correo: sin codigo responde "
			+ "401 con requiereCodigoCorreo=true y hay que repetir la petición con ese codigo. Sin segundo "
			+ "factor (valor por defecto) inicia sesión directamente. "
			+ "Si el correo no está verificado responde 403 con requiereVerificacion=true.")
	@ApiResponse(responseCode = "200", description = "Sesión iniciada: {token, rol, correo}")
	@ApiResponse(responseCode = "400", description = "Debes enviar el correo y la contraseña.")
	@ApiResponse(responseCode = "401", description = "Correo o contraseña inválidos. | Te enviamos un código de "
			+ "verificación a tu correo. | El código del correo no es válido o ya venció.")
	@ApiResponse(responseCode = "429", description = "Superaste los intentos permitidos para este código del correo.")
	@ApiResponse(responseCode = "403", description = "Debes verificar tu correo con el código que te enviamos antes de "
			+ "iniciar sesión. | La cuenta está restringida o desactivada.")
	@PostMapping("/login")
	public ResponseEntity<Object> login(@RequestBody LoginDTO loginRequest) {
		if (loginRequest.getCorreo() == null || loginRequest.getCorreo().isBlank()
				|| loginRequest.getContrasenia() == null || loginRequest.getContrasenia().isBlank()) {
			return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el correo y la contraseña.");
		}
		try {
			String correo = UsuarioService.normalizarCorreo(loginRequest.getCorreo());
			Authentication authentication = authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(correo, loginRequest.getContrasenia()));

			UserDetails usuario = (UserDetails) authentication.getPrincipal();
			Long idUsuario = usuario instanceof Usuario u ? u.getIdUsuario() : null;

			// Correo sin verificar: la contraseña es correcta pero aún no se
			// entrega el token
			if (usuario instanceof Usuario u && !u.esCorreoVerificado()) {
				auditoriaService.registrar(idUsuario, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO,
						idUsuario, ResultadoAuditoria.RECHAZADO, "Correo sin verificar");
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
						.body(Map.of("message",
								"Debes verificar tu correo con el código que te enviamos antes de iniciar sesión.",
								"requiereVerificacion", true));
			}

			// Segundo factor (HU-30): solo las cuentas con totp_habilitado reciben
			// un código por correo, y sin él aún no se entrega el token. Las demás
			// inician sesión directamente (CA-177); el valor por defecto es false.
			if (usuario instanceof Usuario u && SegundoFactorService.estaHabilitado(u)) {
				ResponseEntity<Object> pendiente = codigoInicioPorCorreo(u, correo, loginRequest.getCodigo());
				if (pendiente != null) {
					return pendiente;
				}
			}

			auditoriaService.registrar(idUsuario, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, idUsuario,
					ResultadoAuditoria.EXITOSO, null);
			return respuestaConToken(HttpStatus.OK, usuario, null);
		} catch (LockedException | DisabledException e) {
			auditoriaService.registrar(null, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.RECHAZADO, "Cuenta restringida o desactivada");
			return RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, "La cuenta está restringida o desactivada.");
		} catch (AuthenticationException e) {
			auditoriaService.registrar(null, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.RECHAZADO, "Credenciales inválidas");
			// Mensaje genérico: no revela si el correo existe ni el detalle técnico del
			// error
			return RespuestaUtil.mensaje(HttpStatus.UNAUTHORIZED, "Correo o contraseña inválidos.");
		}
	}

	/**
	 * Paso del código por correo del inicio de sesión. Sin código, envía uno
	 * nuevo al correo; con código, lo comprueba.
	 *
	 * @param usuario cuenta con las credenciales ya validadas
	 * @param correo  correo normalizado de la cuenta
	 * @param codigo  código recibido en el correo, o {@code null}
	 * @return {@code null} si el código es correcto (se entrega el token); si
	 *         no, la respuesta 401/429 con {@code requiereCodigoCorreo=true}
	 */
	private ResponseEntity<Object> codigoInicioPorCorreo(Usuario usuario, String correo, String codigo) {
		Long idUsuario = usuario.getIdUsuario();
		if (codigo == null || codigo.isBlank()) {
			String texto = verificacionCorreoService.enviarCodigoInicio(usuario,
					correo) == VerificacionCorreoService.ESPERAR
							? "Ya te enviamos un código al correo hace menos de un minuto. Revisa tu bandeja de entrada."
							: "Te enviamos un código de verificación a tu correo; vence en "
									+ VerificacionCorreoService.MINUTOS_VIGENCIA + " minutos.";
			return respuestaCodigoCorreo(HttpStatus.UNAUTHORIZED, texto);
		}
		int resultado = verificacionCorreoService.verificarCodigoInicio(usuario, codigo);
		if (resultado == VerificacionCorreoService.VERIFICADO) {
			return null;
		}
		auditoriaService.registrar(idUsuario, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, idUsuario,
				ResultadoAuditoria.RECHAZADO, "Código de inicio de sesión inválido");
		return resultado == VerificacionCorreoService.INTENTOS_AGOTADOS
				? respuestaCodigoCorreo(HttpStatus.TOO_MANY_REQUESTS,
						"Superaste los intentos permitidos para este código del correo. Vuelve a iniciar sesión "
								+ "para recibir uno nuevo.")
				: respuestaCodigoCorreo(HttpStatus.UNAUTHORIZED,
						"El código del correo no es válido o ya venció. Revísalo o vuelve a iniciar sesión.");
	}

	/**
	 * Respuesta que pide el código enviado al correo para terminar de iniciar
	 * sesión.
	 *
	 * @param status estado HTTP
	 * @param texto  mensaje para el usuario
	 * @return {message, requiereSegundoFactor, requiereCodigoCorreo,
	 *         metodoSegundoFactor=CORREO}
	 */
	private static ResponseEntity<Object> respuestaCodigoCorreo(HttpStatus status, String texto) {
		Map<String, Object> respuesta = new LinkedHashMap<>();
		respuesta.put("message", texto);
		respuesta.put("requiereSegundoFactor", true);
		respuesta.put("requiereCodigoCorreo", true);
		respuesta.put("metodoSegundoFactor", "CORREO");
		return ResponseEntity.status(status).body(respuesta);
	}

	/**
	 * Configuración del botón de Google para el front. Ruta:
	 * {@code GET /auth/google/configuracion}. Así el client ID no se escribe a
	 * mano en Angular.
	 *
	 * @return 200 con {habilitado, clientId}; clientId es null si no está
	 *         configurado
	 */
	@Operation(summary = "Configuración del botón de Google", description = "Devuelve {habilitado, clientId}. El "
			+ "front inicializa Google Identity Services con ese clientId y envía el credential a POST /auth/google.")
	@ApiResponse(responseCode = "200", description = "{habilitado, clientId}")
	@GetMapping("/google/configuracion")
	public ResponseEntity<Object> configuracionGoogle() {
		String clientId = inicioGoogleService.clientId();
		Map<String, Object> respuesta = new LinkedHashMap<>();
		respuesta.put("habilitado", clientId != null);
		respuesta.put("clientId", clientId);
		return ResponseEntity.ok(respuesta);
	}

	/**
	 * Inicia sesión con Google. Si el correo no tiene cuenta, la crea ya
	 * verificada con el rol pedido. Ruta: {@code POST /auth/google}.
	 *
	 * @param datos DTO con {@code idToken} (el credential del botón de Google)
	 *              y, opcionalmente, {@code rol} y {@code codigo}
	 * @return 200 con {message, token, rol, correo, cuentaNueva=false}; 201
	 *         igual con cuentaNueva=true si se creó la cuenta; 401 si el token
	 *         no es válido o falta el código del segundo factor (con
	 *         requiereSegundoFactor=true); 403 si la cuenta está restringida o
	 *         se pide el rol ADMIN; 503 si no está configurado GOOGLE_CLIENT_ID
	 */
	@Operation(summary = "Iniciar sesión o registrarse con Google", description = "Recibe el credential (ID token) del botón de Google. Si el correo ya tiene cuenta inicia "
			+ "sesión; si no, la crea verificada con rol HUESPED o el rol enviado (ANFITRION). Devuelve el "
			+ "token igual que /auth/login, más cuentaNueva.")
	@ApiResponse(responseCode = "200", description = "Sesión iniciada con Google: {token, rol, correo, "
			+ "cuentaNueva=false}")
	@ApiResponse(responseCode = "201", description = "Cuenta creada con Google: {token, rol, correo, cuentaNueva=true}")
	@ApiResponse(responseCode = "400", description = "Debes enviar el idToken que entrega el botón de Google.")
	@ApiResponse(responseCode = "401", description = "No se pudo verificar tu cuenta de Google. Intenta de nuevo. | "
			+ "Ingresa el código de tu aplicación autenticadora. | El código de verificación no es válido.")
	@ApiResponse(responseCode = "403", description = "La cuenta está restringida. | Solo puedes registrarte como "
			+ "HUESPED o ANFITRION.")
	@ApiResponse(responseCode = "503", description = "El inicio de sesión con Google no está configurado.")
	@PostMapping("/google")
	public ResponseEntity<Object> google(@RequestBody InicioGoogleDTO datos) {
		if (datos.getIdToken() == null || datos.getIdToken().isBlank()) {
			return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el idToken que entrega el botón de Google.");
		}
		InicioGoogleService.Inicio inicio = inicioGoogleService.iniciar(datos.getIdToken(), datos.getRol(),
				datos.getCodigo());
		return switch (inicio.codigo()) {
		case InicioGoogleService.INICIADA -> {
			Map<String, Object> respuesta = new LinkedHashMap<>(cuerpoConToken(inicio.usuario(),
					inicio.cuentaNueva() ? "Cuenta creada con Google." : "Sesión iniciada con Google."));
			respuesta.put("cuentaNueva", inicio.cuentaNueva());
			yield ResponseEntity.status(inicio.cuentaNueva() ? HttpStatus.CREATED : HttpStatus.OK).body(respuesta);
		}
		case InicioGoogleService.NO_CONFIGURADO ->
			RespuestaUtil.mensaje(HttpStatus.SERVICE_UNAVAILABLE, "El inicio de sesión con Google no está configurado.");
		case InicioGoogleService.CUENTA_RESTRINGIDA -> RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, "La cuenta está restringida.");
		case InicioGoogleService.ROL_NO_PERMITIDO ->
			RespuestaUtil.mensaje(HttpStatus.FORBIDDEN, "Solo puedes registrarte como HUESPED o ANFITRION.");
		case InicioGoogleService.REQUIERE_SEGUNDO_FACTOR -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
				Map.of("message", "Ingresa el código de tu aplicación autenticadora.", "requiereSegundoFactor", true));
		case InicioGoogleService.SEGUNDO_FACTOR_INVALIDO -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("message", "El código de verificación no es válido.", "requiereSegundoFactor", true));
		default -> RespuestaUtil.mensaje(HttpStatus.UNAUTHORIZED, "No se pudo verificar tu cuenta de Google. Intenta de nuevo.");
		};
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
	 * Solicita la recuperación de contraseña: el token se envía por correo al
	 * usuario y vence a los 30 minutos. Ruta: {@code POST /auth/recuperar}.
	 *
	 * @param solicitud DTO con {@code correo}
	 * @return 200 siempre que el correo venga (exista o no la cuenta, para no
	 *         revelar qué cuentas están registradas); 400 si falta el correo
	 */
	@Operation(summary = "Solicitar recuperación de contraseña", description = "Envía al correo un código de recuperación válido por 30 minutos.")
	@ApiResponse(responseCode = "200", description = "Si el correo está registrado, se generó un enlace de "
			+ "recuperación válido por 30 minutos.")
	@ApiResponse(responseCode = "400", description = "Debes enviar el correo.")
	@PostMapping("/recuperar")
	public ResponseEntity<Object> recuperar(@RequestBody UsuarioDTO solicitud) {
		if (authService.solicitarRecuperacion(solicitud.getCorreo()) != 0) {
			return RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el correo.");
		}
		return RespuestaUtil.mensaje(HttpStatus.OK,
				"Si el correo está registrado, se generó un enlace de recuperación válido por 30 minutos.");
	}

	/**
	 * Cambia la contraseña con un token de recuperación. Ruta:
	 * {@code POST /auth/restablecer}.
	 *
	 * @param datos DTO con {@code token} y {@code contrasenia} nueva
	 * @return 200 si se cambió; 400 si faltan datos, la contraseña es débil o el
	 *         token no sirve
	 */
	@Operation(summary = "Restablecer la contraseña con el token de recuperación")
	@ApiResponse(responseCode = "200", description = "Contraseña actualizada. Ya puedes iniciar sesión.")
	@ApiResponse(responseCode = "400", description = "El enlace de recuperación no es válido o ya venció. | La "
			+ "contraseña debe tener entre 8 y 64 caracteres, una mayúscula, una minúscula, un número y un símbolo. | "
			+ "Debes enviar el token y la contraseña nueva.")
	@PostMapping("/restablecer")
	public ResponseEntity<Object> restablecer(@RequestBody RestablecerContraseniaDTO datos) {
		return switch (authService.restablecerContrasenia(datos.getToken(), datos.getContrasenia())) {
		case 0 -> RespuestaUtil.mensaje(HttpStatus.OK, "Contraseña actualizada. Ya puedes iniciar sesión.");
		case 1 -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "El enlace de recuperación no es válido o ya venció.");
		case 3 -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, MENSAJE_CONTRASENIA_DEBIL);
		default -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el token y la contraseña nueva.");
		};
	}

	/**
	 * Reactiva una cuenta desactivada por su dueño e inicia sesión (HU-10,
	 * CA-050). Ruta: {@code POST /auth/reactivar}.
	 *
	 * @param credenciales DTO con {@code correo} y {@code contrasenia}
	 * @return 200 con {message, token, rol, correo, estado}; 400 si faltan
	 *         datos; 401 si las credenciales son inválidas; 403 si un
	 *         administrador restringió la cuenta; 409 si la cuenta ya estaba
	 *         activa
	 */
	@Operation(summary = "Reactivar la cuenta propia", description = "Una cuenta restringida por un administrador no puede reactivarse por esta vía.")
	@ApiResponse(responseCode = "200", description = "Cuenta reactivada: {message, token, rol, correo, estado}")
	@ApiResponse(responseCode = "400", description = "Debes enviar el correo y la contraseña.")
	@ApiResponse(responseCode = "401", description = "Correo o contraseña inválidos.")
	@ApiResponse(responseCode = "403", description = "La cuenta fue restringida por un administrador y no puede "
			+ "reactivarse.")
	@ApiResponse(responseCode = "409", description = "La cuenta ya está activa.")
	@PostMapping("/reactivar")
	public ResponseEntity<Object> reactivar(@RequestBody UsuarioDTO credenciales) {
		return switch (authService.reactivarCuenta(credenciales.getCorreo(), credenciales.getContrasenia())) {
		case 0 -> respuestaReactivada(
				userDetailsService.loadUserByUsername(UsuarioService.normalizarCorreo(credenciales.getCorreo())));
		case 1 -> RespuestaUtil.mensaje(HttpStatus.UNAUTHORIZED, "Correo o contraseña inválidos.");
		case 3 -> RespuestaUtil.mensaje(HttpStatus.FORBIDDEN,
				"La cuenta fue restringida por un administrador y no puede reactivarse.");
		case 4 -> RespuestaUtil.mensaje(HttpStatus.CONFLICT, "La cuenta ya está activa.");
		default -> RespuestaUtil.mensaje(HttpStatus.BAD_REQUEST, "Debes enviar el correo y la contraseña.");
		};
	}

	/**
	 * Respuesta de una cuenta reactivada, con su nuevo estado. Si la cuenta
	 * tiene segundo factor no se entrega token aquí: debe iniciar sesión con
	 * su código.
	 *
	 * @param usuario cuenta reactivada
	 * @return 200 con {message, token, rol, correo, estado}, o sin token si
	 *         tiene segundo factor
	 */
	private ResponseEntity<Object> respuestaReactivada(UserDetails usuario) {
		if (usuario instanceof Usuario u && SegundoFactorService.estaHabilitado(u)) {
			return ResponseEntity.ok(Map.of("message", "Cuenta reactivada correctamente. Inicia sesión con tu código.",
					"estado", EstadoUsuario.ACTIVO.name()));
		}
		Map<String, String> respuesta = cuerpoConToken(usuario, "Cuenta reactivada correctamente.");
		respuesta.put("estado", EstadoUsuario.ACTIVO.name());
		return ResponseEntity.ok(respuesta);
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
		if (usuario instanceof Usuario u && SegundoFactorService.estaHabilitado(u)) {
			return RespuestaUtil.mensaje(HttpStatus.OK, "Correo verificado. Inicia sesión con tu código.");
		}
		return respuestaConToken(HttpStatus.OK, usuario, "Correo verificado correctamente.");
	}

	/**
	 * Respuesta de sesión iniciada: {message?, token, rol, correo}. La usan el
	 * login y la verificación, para que el frontend guarde el token siempre
	 * igual.
	 *
	 * @param status      estado HTTP
	 * @param userDetails usuario que inicia sesión
	 * @param texto       mensaje opcional
	 * @return respuesta con el token
	 */
	private ResponseEntity<Object> respuestaConToken(HttpStatus status, UserDetails userDetails, String texto) {
		return ResponseEntity.status(status).body(cuerpoConToken(userDetails, texto));
	}

	/**
	 * Arma el cuerpo de la respuesta con el token: genera el JWT y agrega el
	 * rol (sin el prefijo {@code ROLE_}) y el correo.
	 *
	 * @param userDetails usuario que inicia sesión
	 * @param texto       mensaje opcional; si es {@code null} no se incluye
	 * @return cuerpo de la respuesta
	 */
	private Map<String, String> cuerpoConToken(UserDetails userDetails, String texto) {
		String rol = userDetails.getAuthorities().stream().findFirst()
				.map(a -> a.getAuthority().replaceFirst("^ROLE_", "")).orElse(null);

		Map<String, String> respuesta = new LinkedHashMap<>();
		if (texto != null) {
			respuesta.put("message", texto);
		}
		respuesta.put("token", jwtUtil.generateToken(userDetails));
		respuesta.put("rol", rol);
		respuesta.put("correo", userDetails.getUsername());
		return respuesta;
	}
}
