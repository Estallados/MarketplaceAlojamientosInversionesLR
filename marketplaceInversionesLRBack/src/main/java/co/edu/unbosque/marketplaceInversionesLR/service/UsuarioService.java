package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.modelmapper.ModelMapper;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.exception.CapitalException;
import co.edu.unbosque.marketplaceInversionesLR.exception.CharacterException;
import co.edu.unbosque.marketplaceInversionesLR.exception.ExceptionCheker;
import co.edu.unbosque.marketplaceInversionesLR.exception.MailException;
import co.edu.unbosque.marketplaceInversionesLR.exception.NumberException;
import co.edu.unbosque.marketplaceInversionesLR.exception.SmallException;
import co.edu.unbosque.marketplaceInversionesLR.exception.SymbolException;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;

/**
 * Reglas de negocio de los usuarios. Por ahora, el registro (HU-06, RF-001):
 * cada operación queda en la auditoría.
 */
@Service
public class UsuarioService {

	/** Resultado de {@link #create(UsuarioDTO)}: el usuario se creó. */
	public static final int CREADO = 0;

	/** El correo ya está registrado. */
	public static final int CORREO_DUPLICADO = 1;

	/** Faltan datos o no son válidos. */
	public static final int DATOS_INVALIDOS = 2;

	/** La contraseña no cumple las reglas. */
	public static final int CONTRASENIA_DEBIL = 3;

	/** Se pidió un rol que no se puede elegir (ADMIN). */
	public static final int ROL_NO_PERMITIDO = 4;

	/** Falló el guardado. */
	public static final int ERROR_INTERNO = 5;

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(UsuarioService.class);

	/** Teléfono ya sin separadores: 7 a 15 dígitos, con + opcional. */
	private static final Pattern PATRON_TELEFONO = Pattern.compile("^\\+?\\d{7,15}$");

	/** Separadores que se aceptan al escribir el teléfono y se quitan antes de guardarlo. */
	private static final Pattern SEPARADORES_TELEFONO = Pattern.compile("[\\s().-]");

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Servicio de auditoría. */
	private final AuditoriaService auditoriaService;

	/** Envía el código de verificación al registrarse. */
	private final VerificacionCorreoService verificacionCorreoService;

	/** Codificador de contraseñas. */
	private final PasswordEncoder passwordEncoder;

	/** Copia los datos de la entidad al DTO. */
	private final ModelMapper modelMapper;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo               repositorio de usuarios
	 * @param auditoriaService          servicio de auditoría
	 * @param verificacionCorreoService servicio de verificación del correo
	 * @param passwordEncoder           codificador de contraseñas
	 * @param modelMapper               copiador de entidades a DTO
	 */
	public UsuarioService(UsuarioRepository usuarioRepo, AuditoriaService auditoriaService,
			VerificacionCorreoService verificacionCorreoService, PasswordEncoder passwordEncoder,
			ModelMapper modelMapper) {
		this.usuarioRepo = usuarioRepo;
		this.auditoriaService = auditoriaService;
		this.verificacionCorreoService = verificacionCorreoService;
		this.passwordEncoder = passwordEncoder;
		this.modelMapper = modelMapper;
	}

	/**
	 * Forma única de comparar correos en todo el sistema: sin espacios a los
	 * lados y en minúsculas.
	 *
	 * @param correo correo como lo escribió el usuario
	 * @return correo normalizado
	 */
	public static String normalizarCorreo(String correo) {
		return correo == null ? null : correo.trim().toLowerCase();
	}

	/**
	 * Hash con el que se busca un correo en la base de datos (el correo se
	 * guarda cifrado y no se puede buscar directamente).
	 *
	 * @param correo correo; se normaliza antes de calcular el hash
	 * @return hash del correo
	 */
	public static String hashCorreo(String correo) {
		return AESUtil.hash(normalizarCorreo(correo));
	}

	/**
	 * Forma única de guardar los teléfonos: sin espacios, guiones, puntos ni
	 * paréntesis. Así "+57 300 000 0000" queda "+573000000000".
	 *
	 * @param telefono teléfono como lo escribió el usuario
	 * @return el teléfono normalizado, o {@code null} si viene vacío
	 */
	public static String normalizarTelefono(String telefono) {
		if (esVacio(telefono)) {
			return null;
		}
		return SEPARADORES_TELEFONO.matcher(telefono.trim()).replaceAll("");
	}

	/**
	 * Registra un usuario nuevo. La cuenta queda ACTIVA pero con el correo sin
	 * verificar: se le envía un código en el correo de bienvenida y no puede
	 * iniciar sesión hasta confirmarlo. Solo se permiten los roles HUESPED (por
	 * defecto) y ANFITRION, nunca ADMIN.
	 *
	 * @param data datos del usuario nuevo
	 * @return {@link #CREADO}, {@link #CORREO_DUPLICADO},
	 *         {@link #DATOS_INVALIDOS}, {@link #CONTRASENIA_DEBIL},
	 *         {@link #ROL_NO_PERMITIDO} o {@link #ERROR_INTERNO}
	 */
	public int create(UsuarioDTO data) {
		Role rol = data == null || data.getRol() == null ? Role.HUESPED : data.getRol();
		if (rol == Role.ADMIN) {
			auditoriaService.registrar(null, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.RECHAZADO, "Intento de registro público como ADMIN");
			return ROL_NO_PERMITIDO;
		}
		return registrar(data, rol);
	}

	/**
	 * Validaciones y guardado del registro.
	 *
	 * @param data datos del usuario
	 * @param rol  rol con el que se crea
	 * @return código del resultado
	 */
	private int registrar(UsuarioDTO data, Role rol) {
		if (data == null || esVacio(data.getNombre()) || esVacio(data.getCorreo()) || esVacio(data.getContrasenia())
				|| !telefonoValido(data.getTelefono()) || LimitesTexto.excede(data.getNombre(), LimitesTexto.CORTO)
				|| LimitesTexto.excede(data.getCorreo(), LimitesTexto.CORREO)) {
			auditarRechazo("Datos incompletos o inválidos");
			return DATOS_INVALIDOS;
		}

		String correo = normalizarCorreo(data.getCorreo());
		try {
			ExceptionCheker.checkerMail(correo);
		} catch (MailException e) {
			auditarRechazo("Correo con formato inválido");
			return DATOS_INVALIDOS;
		}
		try {
			ExceptionCheker.checkerPasword(data.getContrasenia());
		} catch (CharacterException | CapitalException | SmallException | NumberException | SymbolException e) {
			auditarRechazo("Contraseña débil");
			return CONTRASENIA_DEBIL;
		}

		Usuario usuario;
		try {
			String correoHash = AESUtil.hash(correo);
			if (usuarioRepo.existsByCorreoHash(correoHash)) {
				auditarRechazo("Correo ya registrado");
				return CORREO_DUPLICADO;
			}

			// La entidad se arma campo por campo para que el cliente no pueda fijar
			// id, estado ni otros campos internos
			usuario = new Usuario();
			usuario.setNombre(data.getNombre().trim());
			usuario.setCorreo(AESUtil.encrypt(correo));
			usuario.setCorreoHash(correoHash);
			usuario.setTelefono(normalizarTelefono(data.getTelefono()));
			usuario.setContrasenia(passwordEncoder.encode(data.getContrasenia()));
			usuario.setRol(rol);
			usuario.setEstado(EstadoUsuario.ACTIVO);
			usuario.setFechaRegistro(LocalDateTime.now());
			usuario.setCorreoVerificado(false);
			usuario.setTotpHabilitado(false);

			usuario = usuarioRepo.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Dos registros simultáneos con el mismo correo: lo frena el índice único
			auditarRechazo("Correo ya registrado");
			return CORREO_DUPLICADO;
		} catch (RuntimeException e) {
			LOG.error("Error al registrar el usuario", e);
			auditoriaService.registrar(null, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO, null,
					ResultadoAuditoria.ERROR, e.getClass().getSimpleName());
			return ERROR_INTERNO;
		}

		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.REGISTRO_USUARIO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO, "Rol " + rol);

		// Correo de bienvenida con el código de verificación
		verificacionCorreoService.enviarCodigo(usuario, correo, usuario.getNombre(), true);
		return CREADO;
	}

	/**
	 * Indica si el teléfono, si viene, tiene de 7 a 15 dígitos.
	 *
	 * @param telefono teléfono como lo escribió el usuario
	 * @return {@code true} si está vacío o es válido
	 */
	private static boolean telefonoValido(String telefono) {
		return esVacio(telefono) || PATRON_TELEFONO.matcher(normalizarTelefono(telefono)).matches();
	}

	/**
	 * Registra en la auditoría un registro rechazado.
	 *
	 * @param motivo causa del rechazo
	 */
	private void auditarRechazo(String motivo) {
		auditoriaService.registrar(null, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO, null,
				ResultadoAuditoria.RECHAZADO, motivo);
	}

	/**
	 * Busca un usuario por su correo.
	 *
	 * @param correo correo a buscar
	 * @return el usuario, o {@code null} si no existe o el correo está vacío
	 */
	public UsuarioDTO obtenerUsuarioPorCorreo(String correo) {
		if (esVacio(correo)) {
			return null;
		}

		return usuarioRepo.findByCorreoHash(hashCorreo(correo)).map(this::convertirDTO).orElse(null);
	}

	/**
	 * Actualiza el perfil propio. Solo se pueden cambiar el nombre y el
	 * teléfono; el correo, el rol, el estado y la contraseña no se tocan por
	 * esta vía. Los campos que lleguen en {@code null} se dejan como están.
	 *
	 * @param correo   correo del usuario autenticado (sale del token)
	 * @param nombre   nombre nuevo; si se envía no puede estar vacío
	 * @param telefono teléfono nuevo: 7 a 15 dígitos, opcionalmente con + y
	 *                 separadores (espacios, guiones, puntos o paréntesis)
	 * @return 0 si se actualizó; 1 si el usuario no existe; 2 si los datos no
	 *         son válidos o no hay nada que actualizar
	 */
	public int actualizarPerfil(String correo, String nombre, String telefono) {
		if ((nombre == null && telefono == null) || (nombre != null && nombre.isBlank())
				|| LimitesTexto.excede(nombre, LimitesTexto.CORTO)
				|| (telefono != null && !telefonoValido(telefono))) {
			return 2;
		}

		Optional<Usuario> usuarioOpt = esVacio(correo) ? Optional.empty()
				: usuarioRepo.findByCorreoHash(hashCorreo(correo));
		if (usuarioOpt.isEmpty()) {
			return 1;
		}

		Usuario usuario = usuarioOpt.get();
		StringBuilder cambios = new StringBuilder();
		if (nombre != null) {
			usuario.setNombre(nombre.trim());
			cambios.append("nombre ");
		}
		if (telefono != null) {
			usuario.setTelefono(normalizarTelefono(telefono));
			cambios.append("telefono");
		}
		usuarioRepo.save(usuario);

		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.PERFIL_ACTUALIZADO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO,
				"Campos: " + cambios.toString().trim());
		return 0;
	}

	/**
	 * Convierte un usuario a DTO: descifra el correo y quita la contraseña, el
	 * hash del correo y el secreto del segundo factor.
	 *
	 * @param usuario usuario
	 * @return usuario para la API
	 */
	private UsuarioDTO convertirDTO(Usuario usuario) {
		UsuarioDTO dto = modelMapper.map(usuario, UsuarioDTO.class);
		dto.setIdUsuario(usuario.getIdUsuario());
		dto.setNombre(usuario.getNombre());
		dto.setCorreo(AESUtil.decrypt(usuario.getCorreo()));
		dto.setContrasenia(null);
		dto.setCorreoHash(null);
		dto.setSecretoTotp(null);

		return dto;
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
