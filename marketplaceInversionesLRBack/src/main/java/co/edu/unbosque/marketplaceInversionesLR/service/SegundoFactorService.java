package co.edu.unbosque.marketplaceInversionesLR.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.SegundoFactorDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import co.edu.unbosque.marketplaceInversionesLR.util.TotpUtil;

/**
 * Segundo factor de autenticación con códigos TOTP.
 * <p>
 * Activarlo tiene dos pasos: generar el secreto (el usuario lo registra en su
 * app autenticadora) y confirmarlo con un código válido. El secreto se guarda
 * cifrado con AES y solo pertenece a su cuenta.
 * </p>
 */
@Service
public class SegundoFactorService {

	/** Mensaje cuando el usuario del token no existe. */
	public static final String NO_ENCONTRADO = "Usuario no encontrado.";

	/** Mensaje al activar cuando ya está habilitado. */
	public static final String YA_HABILITADO = "El segundo factor ya está habilitado.";

	/** Mensaje al confirmar sin haber pedido la activación. */
	public static final String SIN_ACTIVACION = "Primero solicita la activación del segundo factor.";

	/** Mensaje al desactivar cuando no está habilitado. */
	public static final String NO_HABILITADO = "El segundo factor no está habilitado.";

	/** Mensaje cuando el código de la app autenticadora no es válido. */
	public static final String CODIGO_INVALIDO = "El código no es válido.";

	/** Nombre de la aplicación que se ve en la app autenticadora. */
	private static final String EMISOR = "MarketplaceInversionesLR";

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo repositorio de usuarios
	 * @param auditoriaService servicio de auditoría
	 */
	public SegundoFactorService(UsuarioRepository usuarioRepo, AuditoriaService auditoriaService) {
		this.usuarioRepo = usuarioRepo;
		this.auditoriaService = auditoriaService;
	}

	/**
	 * Indica si la cuenta tiene el segundo factor habilitado.
	 *
	 * @param usuario usuario a revisar
	 * @return {@code true} si está habilitado
	 */
	public static boolean estaHabilitado(Usuario usuario) {
		return Boolean.TRUE.equals(usuario.getTotpHabilitado());
	}

	/**
	 * Genera un secreto nuevo para el usuario. Queda pendiente hasta que lo
	 * confirme con un código.
	 *
	 * @param idUsuario usuario autenticado
	 * @return el secreto y la URI otpauth; NO_ENCONTRADO si el usuario no
	 *         existe; ESTADO_INVALIDO si ya está habilitado
	 */
	@Transactional
	public Resultado<SegundoFactorDTO> activar(Long idUsuario) {
		Optional<Usuario> usuario = usuarioRepo.findById(idUsuario);
		if (usuario.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_ENCONTRADO);
		}
		if (estaHabilitado(usuario.get())) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, YA_HABILITADO);
		}
		String secreto = TotpUtil.generarSecreto();
		usuario.get().setSecretoTotp(AESUtil.encrypt(secreto));
		usuario.get().setTotpHabilitado(false);
		usuarioRepo.save(usuario.get());

		SegundoFactorDTO dto = new SegundoFactorDTO();
		dto.setSecreto(secreto);
		dto.setUri(TotpUtil.uri(EMISOR, usuario.get().getUsername(), secreto));
		return Resultado.exito(dto);
	}

	/**
	 * Confirma la activación con un código de la app.
	 *
	 * @param idUsuario usuario autenticado
	 * @param codigo código de 6 dígitos
	 * @return éxito; NO_ENCONTRADO si el usuario no existe; DATOS_INVALIDOS si
	 *         el código no es válido; ESTADO_INVALIDO si no hay activación
	 *         pendiente
	 */
	@Transactional
	public Resultado<Void> confirmar(Long idUsuario, String codigo) {
		Optional<Usuario> usuario = usuarioRepo.findById(idUsuario);
		if (usuario.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_ENCONTRADO);
		}
		if (usuario.get().getSecretoTotp() == null || estaHabilitado(usuario.get())) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, SIN_ACTIVACION);
		}
		if (!verificar(usuario.get(), codigo)) {
			auditar(idUsuario, ResultadoAuditoria.RECHAZADO, "Código inválido al confirmar");
			return Resultado.error(Resultado.DATOS_INVALIDOS, CODIGO_INVALIDO);
		}
		usuario.get().setTotpHabilitado(true);
		usuarioRepo.save(usuario.get());
		auditar(idUsuario, ResultadoAuditoria.EXITOSO, "Segundo factor habilitado");
		return Resultado.exito(null);
	}

	/**
	 * Deshabilita el segundo factor; pide un código actual para confirmar que
	 * es el dueño.
	 *
	 * @param idUsuario usuario autenticado
	 * @param codigo código de 6 dígitos
	 * @return éxito; NO_ENCONTRADO si el usuario no existe; DATOS_INVALIDOS si
	 *         el código no es válido; ESTADO_INVALIDO si no estaba habilitado
	 */
	@Transactional
	public Resultado<Void> desactivar(Long idUsuario, String codigo) {
		Optional<Usuario> usuario = usuarioRepo.findById(idUsuario);
		if (usuario.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_ENCONTRADO);
		}
		if (!estaHabilitado(usuario.get())) {
			return Resultado.error(Resultado.ESTADO_INVALIDO, NO_HABILITADO);
		}
		if (!verificar(usuario.get(), codigo)) {
			auditar(idUsuario, ResultadoAuditoria.RECHAZADO, "Código inválido al deshabilitar");
			return Resultado.error(Resultado.DATOS_INVALIDOS, CODIGO_INVALIDO);
		}
		usuario.get().setTotpHabilitado(false);
		usuario.get().setSecretoTotp(null);
		usuarioRepo.save(usuario.get());
		auditar(idUsuario, ResultadoAuditoria.EXITOSO, "Segundo factor deshabilitado");
		return Resultado.exito(null);
	}

	/**
	 * Verifica un código contra el secreto de la cuenta.
	 *
	 * @param usuario usuario dueño del secreto
	 * @param codigo código que escribió
	 * @return {@code true} si el código es válido
	 */
	public boolean verificar(Usuario usuario, String codigo) {
		if (usuario.getSecretoTotp() == null) {
			return false;
		}
		return TotpUtil.verificar(AESUtil.decrypt(usuario.getSecretoTotp()), codigo);
	}

	/**
	 * Registra en la auditoría una operación del segundo factor.
	 *
	 * @param idUsuario usuario
	 * @param resultado resultado de la operación
	 * @param detalle detalle para la auditoría
	 */
	private void auditar(Long idUsuario, ResultadoAuditoria resultado, String detalle) {
		auditoriaService.registrar(idUsuario, AuditoriaService.SEGUNDO_FACTOR, AuditoriaService.ENTIDAD_USUARIO,
				idUsuario, resultado, detalle);
	}
}
