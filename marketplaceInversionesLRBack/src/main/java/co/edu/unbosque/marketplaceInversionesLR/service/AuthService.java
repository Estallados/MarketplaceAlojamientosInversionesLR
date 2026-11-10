package co.edu.unbosque.marketplaceInversionesLR.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.TokenRecuperacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.exception.ExceptionCheker;
import co.edu.unbosque.marketplaceInversionesLR.repository.TokenRecuperacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.CorreoUtil;

/**
 * Operaciones de cuenta que no requieren sesión: recuperar la contraseña y
 * reactivar una cuenta que su dueño desactivó.
 * <p>
 * Recuperación: el token se envía por correo al usuario, vence a los 30
 * minutos y sirve una sola vez. Si el correo no se puede enviar (por ejemplo,
 * en desarrollo sin MAIL_PASSWORD), el token se escribe en el log para no
 * dejar al usuario sin salida.
 * </p>
 */
@Service
public class AuthService {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

	/** Vigencia del token en minutos (se informa en el correo). */
	private static final int MINUTOS_VIGENCIA = 30;

	/** Vigencia del token como duración. */
	private static final Duration VIGENCIA_TOKEN = Duration.ofMinutes(MINUTOS_VIGENCIA);

	/** Largo del token en bytes (256 bits). */
	private static final int BYTES_TOKEN = 32;

	/** Generador de los tokens. */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Almacén de los tokens de recuperación. */
	private final TokenRecuperacionRepository tokenRepo;

	/** Codificador de contraseñas. */
	private final PasswordEncoder passwordEncoder;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/** Envía el correo con el token. */
	private final CorreoUtil correoUtil;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo repositorio de usuarios
	 * @param tokenRepo almacén de tokens de recuperación
	 * @param passwordEncoder codificador de contraseñas
	 * @param auditoriaService servicio de auditoría
	 * @param correoUtil utilidad de correo
	 */
	public AuthService(UsuarioRepository usuarioRepo, TokenRecuperacionRepository tokenRepo,
			PasswordEncoder passwordEncoder, AuditoriaService auditoriaService, CorreoUtil correoUtil) {
		this.usuarioRepo = usuarioRepo;
		this.tokenRepo = tokenRepo;
		this.passwordEncoder = passwordEncoder;
		this.auditoriaService = auditoriaService;
		this.correoUtil = correoUtil;
	}

	/**
	 * Genera un token de recuperación para el correo indicado y se lo envía
	 * por correo. Invalida los tokens anteriores sin usar del mismo usuario.
	 * Siempre responde igual exista o no el correo, para no revelar qué
	 * correos están registrados.
	 *
	 * @param correo correo de la cuenta
	 * @return 0 si la solicitud se recibió; 2 si el correo está vacío
	 */
	@Transactional
	public int solicitarRecuperacion(String correo) {
		if (correo == null || correo.isBlank()) {
			return 2;
		}

		Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(correo));
		if (usuarioOpt.isEmpty()) {
			auditoriaService.registrar(null, AuditoriaService.RECUPERACION_SOLICITADA, AuditoriaService.ENTIDAD_USUARIO,
					null, ResultadoAuditoria.RECHAZADO, "Correo no registrado");
			return 0;
		}

		Usuario usuario = usuarioOpt.get();
		for (TokenRecuperacion anterior : tokenRepo.findByIdUsuarioAndUsadoFalse(usuario.getIdUsuario())) {
			anterior.setUsado(true);
			tokenRepo.save(anterior);
		}

		String token = generarToken();
		TokenRecuperacion registro = new TokenRecuperacion();
		registro.setIdUsuario(usuario.getIdUsuario());
		registro.setTokenHash(AESUtil.hash(token));
		registro.setFechaExpiracion(LocalDateTime.now().plus(VIGENCIA_TOKEN));
		registro.setUsado(false);
		tokenRepo.save(registro);

		boolean enviado = correoUtil.enviarCorreo(UsuarioService.normalizarCorreo(correo),
				"Recupera tu contraseña - Marketplace Inversiones LR", cuerpoCorreo(usuario.getNombre(), token));
		if (!enviado) {
			// Sin servidor de correo (desarrollo) el token queda en el log
			LOG.warn("[SIMULACION DE CORREO] Token para restablecer la contraseña del usuario {}: {} (vence {})",
					usuario.getIdUsuario(), token, registro.getFechaExpiracion());
		}

		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.RECUPERACION_SOLICITADA,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(),
				enviado ? ResultadoAuditoria.EXITOSO : ResultadoAuditoria.ERROR,
				enviado ? null : "No se pudo enviar el correo");
		return 0;
	}

	/**
	 * Cambia la contraseña usando un token de recuperación vigente.
	 *
	 * @param token token que le llegó al usuario por correo
	 * @param nuevaContrasenia contraseña nueva; debe cumplir las reglas de
	 *                         contraseña
	 * @return 0 si se cambió; 1 si el token es inválido, venció o ya se usó; 2
	 *         si faltan datos; 3 si la contraseña es débil
	 */
	@Transactional
	public int restablecerContrasenia(String token, String nuevaContrasenia) {
		if (token == null || token.isBlank() || nuevaContrasenia == null || nuevaContrasenia.isBlank()) {
			return 2;
		}
		if (!ExceptionCheker.contraseniaValida(nuevaContrasenia)) {
			return 3;
		}

		Optional<TokenRecuperacion> registroOpt = tokenRepo.findByTokenHash(AESUtil.hash(token));
		if (registroOpt.isEmpty() || Boolean.TRUE.equals(registroOpt.get().getUsado())
				|| registroOpt.get().getFechaExpiracion().isBefore(LocalDateTime.now())) {
			auditoriaService.registrar(null, AuditoriaService.CONTRASENIA_RESTABLECIDA,
					AuditoriaService.ENTIDAD_USUARIO, null, ResultadoAuditoria.RECHAZADO,
					"Token inválido, vencido o ya usado");
			return 1;
		}

		TokenRecuperacion registro = registroOpt.get();
		Optional<Usuario> usuarioOpt = usuarioRepo.findById(registro.getIdUsuario());
		if (usuarioOpt.isEmpty()) {
			return 1;
		}

		Usuario usuario = usuarioOpt.get();
		usuario.setContrasenia(passwordEncoder.encode(nuevaContrasenia));
		usuarioRepo.save(usuario);

		registro.setUsado(true);
		tokenRepo.save(registro);

		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CONTRASENIA_RESTABLECIDA,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO, null);
		return 0;
	}

	/**
	 * Reactiva una cuenta que su dueño desactivó, validando sus credenciales.
	 * Una cuenta RESTRINGIDA por un administrador no puede reactivarse sola.
	 *
	 * @param correo correo de la cuenta
	 * @param contrasenia contraseña actual
	 * @return 0 si se reactivó; 1 si las credenciales no son válidas; 2 si
	 *         faltan datos; 3 si un administrador la restringió; 4 si ya estaba
	 *         activa
	 */
	@Transactional
	public int reactivarCuenta(String correo, String contrasenia) {
		if (correo == null || correo.isBlank() || contrasenia == null || contrasenia.isBlank()) {
			return 2;
		}

		Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(correo));
		if (usuarioOpt.isEmpty() || !passwordEncoder.matches(contrasenia, usuarioOpt.get().getContrasenia())) {
			auditoriaService.registrar(null, AuditoriaService.CUENTA_REACTIVADA, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.RECHAZADO, "Credenciales inválidas");
			return 1;
		}

		Usuario usuario = usuarioOpt.get();
		if (usuario.getEstado() == EstadoUsuario.RESTRINGIDO) {
			auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CUENTA_REACTIVADA,
					AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.RECHAZADO,
					"Cuenta restringida por un administrador");
			return 3;
		}
		if (usuario.getEstado() == EstadoUsuario.ACTIVO) {
			return 4;
		}

		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuarioRepo.save(usuario);

		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CUENTA_REACTIVADA,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO, null);
		return 0;
	}

	/**
	 * Arma el texto del correo de recuperación.
	 *
	 * @param nombre nombre del usuario, para el saludo
	 * @param token token de recuperación
	 * @return cuerpo del correo
	 */
	private static String cuerpoCorreo(String nombre, String token) {
		return String.format("Hola %s,%n%n" + "Recibimos una solicitud para restablecer la contraseña de tu cuenta. "
				+ "Usa este código de recuperación:%n%n    %s%n%n"
				+ "El código vence en %d minutos y solo puede usarse una vez. "
				+ "Si no solicitaste el cambio, ignora este mensaje: tu contraseña seguirá igual.%n%n" + "Saludos,%n"
				+ "Equipo Marketplace Inversiones LR", nombre, token, MINUTOS_VIGENCIA);
	}

	/**
	 * Genera un token aleatorio en Base64 URL (sin relleno).
	 *
	 * @return token nuevo
	 */
	private static String generarToken() {
		byte[] bytes = new byte[BYTES_TOKEN];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
