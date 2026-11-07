package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.GoogleTokenVerificador;
import co.edu.unbosque.marketplaceInversionesLR.security.GoogleTokenVerificador.CuentaGoogle;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Inicio de sesión con Google. El frontend muestra el botón de Google y envía
 * el ID token; si es válido:
 * <p>
 * Si el correo ya tiene cuenta, se inicia sesión con ella (aunque se haya
 * creado con contraseña). Como Google ya comprobó el correo, la cuenta queda
 * verificada, y si su dueño la había desactivado se reactiva.
 * </p>
 * <p>
 * Si no tiene cuenta, se crea con el nombre y el correo de Google y el rol
 * pedido (HUESPED por defecto o ANFITRION, nunca ADMIN). No se le pide código
 * de verificación ni contraseña; si después quiere entrar con contraseña, la
 * define con "Olvidé mi contraseña".
 * </p>
 * <p>
 * Una cuenta restringida por un administrador no puede entrar, y una con
 * segundo factor debe enviar también su código.
 * </p>
 */
@Service
public class InicioGoogleService {

	/** Resultado de {@link #iniciar}: se inició sesión. */
	public static final int INICIADA = 0;

	/** El inicio con Google no está configurado. */
	public static final int NO_CONFIGURADO = 1;

	/** El ID token no es válido. */
	public static final int TOKEN_INVALIDO = 2;

	/** La cuenta está restringida por un administrador. */
	public static final int CUENTA_RESTRINGIDA = 3;

	/** Se pidió un rol que no se puede elegir (ADMIN). */
	public static final int ROL_NO_PERMITIDO = 4;

	/** La cuenta tiene segundo factor y no se envió el código. */
	public static final int REQUIERE_SEGUNDO_FACTOR = 5;

	/** El código del segundo factor no es válido. */
	public static final int SEGUNDO_FACTOR_INVALIDO = 6;

	/**
	 * Resultado de un inicio con Google.
	 *
	 * @param codigo      Uno de los resultados de esta clase.
	 * @param usuario     Cuenta con la que se inicia sesión; null si no se pudo.
	 * @param cuentaNueva true si la cuenta se creó en este inicio.
	 */
	public record Inicio(int codigo, Usuario usuario, boolean cuentaNueva) {
	}

	/** Verifica el ID token de Google. */
	private final GoogleTokenVerificador verificador;

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Verifica el código del segundo factor. */
	private final SegundoFactorService segundoFactorService;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/** Codifica la contraseña aleatoria de las cuentas nuevas. */
	private final PasswordEncoder passwordEncoder;

	/**
	 * Crea el servicio.
	 *
	 * @param verificador verificador de tokens de Google
	 * @param usuarioRepo repositorio de usuarios
	 * @param segundoFactorService servicio del segundo factor
	 * @param auditoriaService servicio de auditoría
	 * @param passwordEncoder codificador de contraseñas
	 */
	public InicioGoogleService(GoogleTokenVerificador verificador, UsuarioRepository usuarioRepo,
			SegundoFactorService segundoFactorService, AuditoriaService auditoriaService,
			PasswordEncoder passwordEncoder) {
		this.verificador = verificador;
		this.usuarioRepo = usuarioRepo;
		this.segundoFactorService = segundoFactorService;
		this.auditoriaService = auditoriaService;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Client ID de Google para el botón del frontend.
	 *
	 * @return el client ID, o {@code null} si el inicio con Google no está
	 *         configurado
	 */
	public String clientId() {
		return verificador.getClientId();
	}

	/**
	 * Inicia sesión o crea la cuenta con el ID token de Google.
	 *
	 * @param idToken Token que entregó el botón de Google.
	 * @param rol     Rol de la cuenta si hay que crearla; null es HUESPED.
	 * @param codigo  Código del segundo factor, si la cuenta lo tiene activo.
	 * @return el resultado con la cuenta cuando es {@link #INICIADA}.
	 */
	@Transactional
	public Inicio iniciar(String idToken, Role rol, String codigo) {
		if (!verificador.estaConfigurado()) {
			return new Inicio(NO_CONFIGURADO, null, false);
		}
		Optional<CuentaGoogle> cuenta = verificador.verificar(idToken);
		if (cuenta.isEmpty()) {
			auditoriaService.registrar(null, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.RECHAZADO, "Token de Google inválido");
			return new Inicio(TOKEN_INVALIDO, null, false);
		}

		String correo = UsuarioService.normalizarCorreo(cuenta.get().correo());
		Optional<Usuario> existente = usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(correo));
		if (existente.isPresent()) {
			return iniciarExistente(existente.get(), codigo);
		}
		if (rol == Role.ADMIN) {
			auditoriaService.registrar(null, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.RECHAZADO, "Rol ADMIN pedido con Google");
			return new Inicio(ROL_NO_PERMITIDO, null, false);
		}
		return crear(cuenta.get(), correo, rol == null ? Role.HUESPED : rol);
	}

	/**
	 * Inicia sesión con una cuenta que ya existe: revisa la restricción y el
	 * segundo factor, la marca como verificada y la reactiva si estaba
	 * inactiva.
	 *
	 * @param usuario cuenta existente
	 * @param codigo código del segundo factor; puede ser {@code null}
	 * @return resultado del inicio
	 */
	private Inicio iniciarExistente(Usuario usuario, String codigo) {
		Long id = usuario.getIdUsuario();
		if (usuario.getEstado() == EstadoUsuario.RESTRINGIDO) {
			auditoriaService.registrar(id, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, id,
					ResultadoAuditoria.RECHAZADO, "Cuenta restringida (Google)");
			return new Inicio(CUENTA_RESTRINGIDA, null, false);
		}
		if (SegundoFactorService.estaHabilitado(usuario)) {
			if (codigo == null || codigo.isBlank()) {
				return new Inicio(REQUIERE_SEGUNDO_FACTOR, null, false);
			}
			if (!segundoFactorService.verificar(usuario, codigo)) {
				auditoriaService.registrar(id, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, id,
						ResultadoAuditoria.RECHAZADO, "Código del segundo factor inválido (Google)");
				return new Inicio(SEGUNDO_FACTOR_INVALIDO, null, false);
			}
		}

		if (!usuario.esCorreoVerificado()) {
			usuario.setCorreoVerificado(true);
			auditoriaService.registrar(id, AuditoriaService.CORREO_VERIFICADO, AuditoriaService.ENTIDAD_USUARIO, id,
					ResultadoAuditoria.EXITOSO, "Verificado por Google");
		}
		if (usuario.getEstado() == EstadoUsuario.INACTIVO) {
			usuario.setEstado(EstadoUsuario.ACTIVO);
			auditoriaService.registrar(id, AuditoriaService.CUENTA_REACTIVADA, AuditoriaService.ENTIDAD_USUARIO, id,
					ResultadoAuditoria.EXITOSO, "Reactivada al iniciar sesión con Google");
		}
		usuarioRepo.save(usuario);
		auditoriaService.registrar(id, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, id,
				ResultadoAuditoria.EXITOSO, "Google");
		return new Inicio(INICIADA, usuario, false);
	}

	/**
	 * Crea la cuenta con los datos de Google. Le pone una contraseña aleatoria
	 * que nadie conoce; para entrar con contraseña, el usuario la define con
	 * la recuperación.
	 *
	 * @param cuenta datos de la cuenta de Google
	 * @param correo correo normalizado
	 * @param rol rol de la cuenta
	 * @return resultado del inicio, con la cuenta nueva
	 */
	private Inicio crear(CuentaGoogle cuenta, String correo, Role rol) {
		String nombre = cuenta.nombre() == null || cuenta.nombre().isBlank() ? correo.substring(0, correo.indexOf('@'))
				: cuenta.nombre().trim();
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		// Contraseña aleatoria que nadie conoce: para entrar con contraseña se define
		// con la recuperación
		usuario.setContrasenia(passwordEncoder.encode(UUID.randomUUID().toString()));
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setTotpHabilitado(false);
		usuario.setCorreoVerificado(true);
		usuario.setFechaRegistro(LocalDateTime.now());
		try {
			usuario = usuarioRepo.save(usuario);
		} catch (DataIntegrityViolationException e) {
			// Dos inicios simultáneos con el mismo correo: lo frena el índice único
			return new Inicio(TOKEN_INVALIDO, null, false);
		}
		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.REGISTRO_USUARIO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO,
				"Rol " + rol + ", con Google");
		return new Inicio(INICIADA, usuario, true);
	}
}
