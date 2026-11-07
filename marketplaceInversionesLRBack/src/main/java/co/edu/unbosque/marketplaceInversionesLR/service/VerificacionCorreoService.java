package co.edu.unbosque.marketplaceInversionesLR.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.entity.CodigoVerificacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.CodigoVerificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.CorreoUtil;

/**
 * Verificación del correo con un código de 6 dígitos. Al registrarse, el
 * usuario recibe el código en el correo de bienvenida y no puede iniciar
 * sesión hasta confirmarlo con {@code POST /auth/verificar}.
 * <p>
 * Reglas: el código vence a los 15 minutos, sirve una sola vez y admite 5
 * intentos fallidos; pedir uno nuevo invalida el anterior y solo se permite
 * una vez por minuto. Se guarda únicamente el hash del código. Si el correo no
 * se puede enviar (por ejemplo, en desarrollo sin MAIL_PASSWORD), el código se
 * escribe en el log para no dejar la cuenta bloqueada.
 * </p>
 */
@Service
public class VerificacionCorreoService {

	/** Resultado de {@link #verificar(String, String)}: el correo quedó verificado. */
	public static final int VERIFICADO = 0;

	/** El código no es correcto, venció o la cuenta no existe. */
	public static final int CODIGO_INVALIDO = 1;

	/** Falta el correo o el código. */
	public static final int DATOS_INVALIDOS = 2;

	/** Se agotaron los intentos; hay que pedir otro código. */
	public static final int INTENTOS_AGOTADOS = 3;

	/** El correo ya estaba verificado. */
	public static final int YA_VERIFICADO = 4;

	/** Resultado de {@link #reenviar(String)}: el código se envió. */
	public static final int ENVIADO = 0;

	/** Hay que esperar un minuto antes de pedir otro código. */
	public static final int ESPERAR = 3;

	/** Vigencia del código en minutos (se informa en el correo). */
	public static final int MINUTOS_VIGENCIA = 15;

	/** Vigencia del código como duración. */
	private static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(MINUTOS_VIGENCIA);

	/** Tiempo mínimo entre dos reenvíos. */
	private static final Duration ESPERA_REENVIO = Duration.ofMinutes(1);

	/** Intentos fallidos permitidos por código. */
	private static final int MAXIMO_INTENTOS = 5;

	/** Límite superior para generar el código (6 dígitos). */
	private static final int LIMITE_CODIGO = 1_000_000;

	/** Generador de los códigos. */
	private static final SecureRandom RANDOM = new SecureRandom();

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(VerificacionCorreoService.class);

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Almacén de los códigos de verificación. */
	private final CodigoVerificacionRepository codigoRepo;

	/** Envía el correo con el código. */
	private final CorreoUtil correoUtil;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo      repositorio de usuarios
	 * @param codigoRepo       almacén de códigos
	 * @param correoUtil       utilidad de correo
	 * @param auditoriaService servicio de auditoría
	 */
	public VerificacionCorreoService(UsuarioRepository usuarioRepo, CodigoVerificacionRepository codigoRepo,
			CorreoUtil correoUtil, AuditoriaService auditoriaService) {
		this.usuarioRepo = usuarioRepo;
		this.codigoRepo = codigoRepo;
		this.correoUtil = correoUtil;
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Genera un código nuevo para el usuario y lo envía en el correo de
	 * bienvenida (registro) o en un correo de reenvío. Invalida el código
	 * anterior.
	 *
	 * @param usuario    usuario ya guardado
	 * @param correo     correo en claro del usuario
	 * @param nombre     nombre en claro, para el saludo
	 * @param bienvenida {@code true} en el registro; {@code false} al reenviar
	 * @return {@code true} si el correo se envió
	 */
	public boolean enviarCodigo(Usuario usuario, String correo, String nombre, boolean bienvenida) {
		String codigo = generarCodigo();
		LocalDateTime ahora = LocalDateTime.now();
		CodigoVerificacion registro = new CodigoVerificacion();
		registro.setIdUsuario(usuario.getIdUsuario());
		registro.setCodigoHash(AESUtil.hash(codigo));
		registro.setFechaCreacion(ahora);
		registro.setFechaExpiracion(ahora.plus(VIGENCIA_CODIGO));
		registro.setIntentos(0);
		registro.setUsado(false);
		// Un solo código por usuario: guardar el nuevo reemplaza (invalida) el anterior
		codigoRepo.save(registro);

		boolean enviado = correoUtil.enviarCorreo(correo,
				bienvenida ? "Bienvenido al Marketplace de Inversiones LR: verifica tu correo"
						: "Tu nuevo código de verificación - Marketplace Inversiones LR",
				cuerpoCorreo(nombre, codigo, bienvenida));
		if (!enviado) {
			// Sin servidor de correo (desarrollo) el código queda en el log
			LOG.warn("[SIMULACION DE CORREO] Código de verificación del usuario {}: {} (vence {})",
					usuario.getIdUsuario(), codigo, registro.getFechaExpiracion());
		}
		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CODIGO_VERIFICACION_ENVIADO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(),
				enviado ? ResultadoAuditoria.EXITOSO : ResultadoAuditoria.ERROR,
				enviado ? null : "No se pudo enviar el correo");
		return enviado;
	}

	/**
	 * Confirma el correo con el código recibido. Cada intento fallido cuenta; al
	 * llegar al máximo el código deja de servir y hay que pedir otro.
	 *
	 * @param correo correo de la cuenta
	 * @param codigo código de 6 dígitos
	 * @return {@link #VERIFICADO}, {@link #CODIGO_INVALIDO} (también si la cuenta
	 *         no existe, para no revelarlo), {@link #DATOS_INVALIDOS},
	 *         {@link #INTENTOS_AGOTADOS} o {@link #YA_VERIFICADO}
	 */
	@Transactional
	public int verificar(String correo, String codigo) {
		if (esVacio(correo) || esVacio(codigo)) {
			return DATOS_INVALIDOS;
		}
		Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(correo));
		if (usuarioOpt.isEmpty()) {
			return CODIGO_INVALIDO;
		}
		Usuario usuario = usuarioOpt.get();
		if (usuario.esCorreoVerificado()) {
			return YA_VERIFICADO;
		}

		Optional<CodigoVerificacion> registroOpt = codigoRepo.findVigente(usuario.getIdUsuario());
		if (registroOpt.isEmpty() || registroOpt.get().getFechaExpiracion().isBefore(LocalDateTime.now())) {
			return rechazar(usuario, CODIGO_INVALIDO, "Código vencido o inexistente");
		}
		CodigoVerificacion registro = registroOpt.get();
		if (registro.getIntentos() >= MAXIMO_INTENTOS) {
			return rechazar(usuario, INTENTOS_AGOTADOS, "Intentos agotados");
		}
		if (!AESUtil.hash(codigo.trim()).equals(registro.getCodigoHash())) {
			registro.setIntentos(registro.getIntentos() + 1);
			return rechazar(usuario, registro.getIntentos() >= MAXIMO_INTENTOS ? INTENTOS_AGOTADOS : CODIGO_INVALIDO,
					"Código incorrecto");
		}

		registro.setUsado(true);
		usuario.setCorreoVerificado(true);
		usuarioRepo.save(usuario);
		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CORREO_VERIFICADO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO, null);
		return VERIFICADO;
	}

	/**
	 * Envía un código nuevo a una cuenta sin verificar. Si la cuenta no existe o
	 * ya está verificada responde igual que si lo hubiera enviado, para no
	 * revelar qué correos están registrados.
	 *
	 * @param correo correo de la cuenta
	 * @return {@link #ENVIADO}, {@link #DATOS_INVALIDOS} si falta el correo o
	 *         {@link #ESPERAR} si el último código se pidió hace menos de un
	 *         minuto
	 */
	public int reenviar(String correo) {
		if (esVacio(correo)) {
			return DATOS_INVALIDOS;
		}
		Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(correo));
		if (usuarioOpt.isEmpty() || usuarioOpt.get().esCorreoVerificado()) {
			return ENVIADO;
		}
		Usuario usuario = usuarioOpt.get();
		Optional<CodigoVerificacion> ultimo = codigoRepo.findVigente(usuario.getIdUsuario());
		if (ultimo.isPresent() && ultimo.get().getFechaCreacion().plus(ESPERA_REENVIO).isAfter(LocalDateTime.now())) {
			return ESPERAR;
		}
		enviarCodigo(usuario, UsuarioService.normalizarCorreo(correo), usuario.getNombre(), false);
		return ENVIADO;
	}

	/**
	 * Registra en la auditoría un intento de verificación rechazado.
	 *
	 * @param usuario usuario
	 * @param codigo  resultado a devolver
	 * @param motivo  causa del rechazo
	 * @return el mismo resultado recibido
	 */
	private int rechazar(Usuario usuario, int codigo, String motivo) {
		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CORREO_VERIFICADO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.RECHAZADO, motivo);
		return codigo;
	}

	/**
	 * Arma el texto del correo con el código, de bienvenida o de reenvío.
	 *
	 * @param nombre     nombre del usuario, para el saludo
	 * @param codigo     código de 6 dígitos
	 * @param bienvenida {@code true} si es el correo de registro
	 * @return cuerpo del correo
	 */
	private static String cuerpoCorreo(String nombre, String codigo, boolean bienvenida) {
		String saludo = bienvenida
				? "¡Bienvenido al Marketplace de Alojamientos de Inversiones LR!\n\n"
						+ "Nos alegra que formes parte de nuestra plataforma, "
						+ "donde podrás descubrir, comparar y reservar alojamientos, "
						+ "o publicar tus propiedades como anfitrión.\n\n"
						+ "Para activar tu cuenta, ingresa este código de verificación:\n\n"
				: "Este es tu nuevo código de verificación:\n\n";
		return String.format("Hola %s,%n%n%s    %s%n%n" + "El código vence en %d minutos y solo puede usarse una vez. "
				+ "Si no creaste esta cuenta, ignora este mensaje.%n%n" + "Saludos,%n"
				+ "Equipo Marketplace Inversiones LR", nombre, saludo, codigo, MINUTOS_VIGENCIA);
	}

	/**
	 * Genera un código numérico de 6 dígitos, con ceros a la izquierda si hace
	 * falta.
	 *
	 * @return código nuevo
	 */
	private static String generarCodigo() {
		return String.format("%06d", RANDOM.nextInt(LIMITE_CODIGO));
	}

	/**
	 * Indica si un texto es {@code null} o solo tiene espacios.
	 *
	 * @param texto texto a revisar
	 * @return {@code true} si está vacío
	 */
	private static boolean esVacio(String texto) {
		return texto == null || texto.isBlank();
	}
}
