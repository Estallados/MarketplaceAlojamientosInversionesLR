package co.edu.unbosque.marketplaceInversionesLR.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.modelmapper.ModelMapper;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
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
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Reglas de negocio de los usuarios: registro (HU-06), perfil propio (HU-09,
 * HU-10) y la gestión del administrador, que consulta, restringe y libera
 * cuentas (HU-12). Cada operación queda en la auditoría.
 * <p>
 * Una cuenta RESTRINGIDO no puede iniciar sesión, sus tokens dejan de servir
 * ({@code JwtAuthenticationFilter}) y no puede reactivarse sola (CA-059).
 * </p>
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

	/** Mensaje cuando el usuario no existe. */
	public static final String NO_ENCONTRADO = "Usuario no encontrado.";

	/** Mensaje cuando falta el motivo de la restricción o es muy largo (CA-058). */
	public static final String MOTIVO_INVALIDO = "El motivo de la restricción es obligatorio y puede tener máximo "
			+ LimitesTexto.MOTIVO + " caracteres.";

	/** Mensaje cuando el motivo opcional de la liberación es muy largo. */
	public static final String MOTIVO_LARGO = "El motivo puede tener máximo " + LimitesTexto.MOTIVO + " caracteres.";

	/** Mensaje cuando se intenta restringir a un administrador. */
	public static final String NO_RESTRINGIR_ADMIN = "No se puede restringir a un administrador.";

	/** Mensaje cuando se intenta liberar una cuenta que ya está activa. */
	public static final String YA_ACTIVA = "La cuenta ya está activa.";

	/** Inicio del aviso al usuario restringido; sigue el motivo. */
	public static final String AVISO_RESTRINGIDA = "Tu cuenta fue restringida por un administrador. Motivo: ";

	/** Aviso al usuario cuando se le habilita la cuenta. */
	public static final String AVISO_LIBERADA = "Tu cuenta fue habilitada de nuevo por un administrador.";

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

	/** Avisa al usuario cuando se restringe o se libera su cuenta (HU-12). */
	private final NotificacionService notificacionService;

	/**
	 * Crea el servicio.
	 *
	 * @param usuarioRepo               repositorio de usuarios
	 * @param auditoriaService          servicio de auditoría
	 * @param verificacionCorreoService servicio de verificación del correo
	 * @param passwordEncoder           codificador de contraseñas
	 * @param modelMapper               copiador de entidades a DTO
	 * @param notificacionService       avisos al usuario
	 */
	public UsuarioService(UsuarioRepository usuarioRepo, AuditoriaService auditoriaService,
			VerificacionCorreoService verificacionCorreoService, PasswordEncoder passwordEncoder,
			ModelMapper modelMapper, NotificacionService notificacionService) {
		this.usuarioRepo = usuarioRepo;
		this.auditoriaService = auditoriaService;
		this.verificacionCorreoService = verificacionCorreoService;
		this.passwordEncoder = passwordEncoder;
		this.modelMapper = modelMapper;
		this.notificacionService = notificacionService;
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
		return registrar(data, rol, null);
	}

	/**
	 * Crea otro administrador. Solo lo puede pedir un ADMIN (lo controla
	 * {@code UsuarioController} con {@code @PreAuthorize}); el registro público
	 * nunca crea administradores. Tiene las mismas validaciones del registro,
	 * pero el correo queda verificado y no se envía código: el nuevo
	 * administrador puede iniciar sesión de inmediato.
	 *
	 * @param data    datos del nuevo administrador; el rol enviado se ignora
	 * @param idActor administrador que lo crea
	 * @return los mismos códigos de {@link #create(UsuarioDTO)}, excepto
	 *         {@link #ROL_NO_PERMITIDO}
	 */
	public int crearAdministrador(UsuarioDTO data, Long idActor) {
		return registrar(data, Role.ADMIN, idActor);
	}

	/**
	 * Validaciones y guardado del registro. Si la cuenta la crea un
	 * administrador, el correo queda verificado y no se envía código.
	 *
	 * @param data    datos del usuario
	 * @param rol     rol con el que se crea
	 * @param idActor administrador que lo crea; {@code null} en el registro
	 *                público
	 * @return código del resultado
	 */
	private int registrar(UsuarioDTO data, Role rol, Long idActor) {
		if (data == null || esVacio(data.getNombre()) || esVacio(data.getCorreo()) || esVacio(data.getContrasenia())
				|| !telefonoValido(data.getTelefono()) || LimitesTexto.excede(data.getNombre(), LimitesTexto.CORTO)
				|| LimitesTexto.excede(data.getCorreo(), LimitesTexto.CORREO)) {
			auditarRechazo(idActor, "Datos incompletos o inválidos");
			return DATOS_INVALIDOS;
		}

		String correo = normalizarCorreo(data.getCorreo());
		try {
			ExceptionCheker.checkerMail(correo);
		} catch (MailException e) {
			auditarRechazo(idActor, "Correo con formato inválido");
			return DATOS_INVALIDOS;
		}
		try {
			ExceptionCheker.checkerPasword(data.getContrasenia());
		} catch (CharacterException | CapitalException | SmallException | NumberException | SymbolException e) {
			auditarRechazo(idActor, "Contraseña débil");
			return CONTRASENIA_DEBIL;
		}

		boolean creadaPorAdmin = idActor != null;
		Usuario usuario;
		try {
			String correoHash = AESUtil.hash(correo);
			if (usuarioRepo.existsByCorreoHash(correoHash)) {
				auditarRechazo(idActor, "Correo ya registrado");
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
			// La cuenta que crea un administrador no necesita verificar el correo
			usuario.setCorreoVerificado(creadaPorAdmin);
			usuario.setTotpHabilitado(false);

			usuario = usuarioRepo.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Dos registros simultáneos con el mismo correo: lo frena el índice único
			auditarRechazo(idActor, "Correo ya registrado");
			return CORREO_DUPLICADO;
		} catch (RuntimeException e) {
			LOG.error("Error al registrar el usuario", e);
			auditoriaService.registrar(idActor, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO,
					null, ResultadoAuditoria.ERROR, e.getClass().getSimpleName());
			return ERROR_INTERNO;
		}

		auditoriaService.registrar(idActor == null ? usuario.getIdUsuario() : idActor, AuditoriaService.REGISTRO_USUARIO,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO, "Rol " + rol);

		// Correo de bienvenida con el código de verificación (solo en el registro público)
		if (!creadaPorAdmin) {
			verificacionCorreoService.enviarCodigo(usuario, correo, usuario.getNombre(), true);
		}
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
	 * @param idActor administrador que lo intentó; {@code null} en el registro
	 *                público
	 * @param motivo  causa del rechazo
	 */
	private void auditarRechazo(Long idActor, String motivo) {
		auditoriaService.registrar(idActor, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO, null,
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
	 * Desactiva la cuenta propia (queda INACTIVO). Se reactiva con
	 * {@code POST /auth/reactivar}. Un administrador no puede desactivarse.
	 *
	 * @param correo correo del usuario autenticado (sale del token)
	 * @return 0 si se desactivó; 1 si el usuario no existe; 2 si es ADMIN o la
	 *         cuenta no está ACTIVO
	 */
	public int desactivarCuenta(String correo) {
		if (esVacio(correo)) {
			return 1;
		}

		Optional<Usuario> usuarioOpt = usuarioRepo.findByCorreoHash(hashCorreo(correo));
		if (usuarioOpt.isEmpty()) {
			return 1;
		}

		Usuario usuario = usuarioOpt.get();
		if (usuario.getRol() == Role.ADMIN || usuario.getEstado() != EstadoUsuario.ACTIVO) {
			return 2;
		}

		usuario.setEstado(EstadoUsuario.INACTIVO);
		usuarioRepo.save(usuario);

		auditoriaService.registrar(usuario.getIdUsuario(), AuditoriaService.CUENTA_DESACTIVADA,
				AuditoriaService.ENTIDAD_USUARIO, usuario.getIdUsuario(), ResultadoAuditoria.EXITOSO, null);
		return 0;
	}

	/**
	 * Usuarios paginados, con su estado y el motivo de la restricción si la
	 * tienen (HU-12, CA-058).
	 *
	 * @param pagina página y orden
	 * @return página de usuarios
	 */
	@Transactional(readOnly = true)
	public PaginaDTO<UsuarioDTO> listarPaginado(Pageable pagina) {
		return PaginaDTO.de(usuarioRepo.findAll(pagina), this::convertirDTO);
	}

	/**
	 * Todos los usuarios, ordenados por id (HU-12).
	 *
	 * @return lista de usuarios
	 */
	@Transactional(readOnly = true)
	public List<UsuarioDTO> listarTodos() {
		return usuarioRepo.findAll(Sort.by("idUsuario")).stream().map(this::convertirDTO).toList();
	}

	/**
	 * Busca un usuario por id.
	 *
	 * @param idUsuario usuario
	 * @return el usuario, o {@code null} si no existe
	 */
	@Transactional(readOnly = true)
	public UsuarioDTO obtenerPorId(Long idUsuario) {
		return idUsuario == null ? null : usuarioRepo.findById(idUsuario).map(this::convertirDTO).orElse(null);
	}

	/**
	 * Indica si un usuario existe.
	 *
	 * @param idUsuario usuario
	 * @return {@code true} si existe
	 */
	public boolean existe(Long idUsuario) {
		return idUsuario != null && usuarioRepo.existsById(idUsuario);
	}

	/**
	 * Restringe una cuenta con un motivo (administrador, HU-12, CA-058). Queda
	 * RESTRINGIDO con el motivo, que se ve al consultar el usuario y se le
	 * notifica. Desde ese momento no puede iniciar sesión, sus tokens dejan de
	 * servir y no puede reactivarse sola (CA-059). A un administrador no se le
	 * puede restringir. Si ya estaba restringida, se actualiza el motivo.
	 *
	 * @param idUsuario usuario a restringir
	 * @param motivo    motivo obligatorio, de máximo {@value LimitesTexto#MOTIVO}
	 *                  caracteres
	 * @param idAdmin   administrador que restringe
	 * @return el usuario; DATOS_INVALIDOS si el motivo falta o es muy largo;
	 *         NO_ENCONTRADO si no existe; SIN_PERMISO si es administrador
	 */
	@Transactional
	public Resultado<UsuarioDTO> restringir(Long idUsuario, String motivo, Long idAdmin) {
		if (esVacio(motivo) || LimitesTexto.excede(motivo, LimitesTexto.MOTIVO)) {
			auditoriaService.registrar(idAdmin, AuditoriaService.USUARIO_RESTRINGIDO, AuditoriaService.ENTIDAD_USUARIO,
					idUsuario, ResultadoAuditoria.RECHAZADO, MOTIVO_INVALIDO);
			return Resultado.error(Resultado.DATOS_INVALIDOS, MOTIVO_INVALIDO);
		}
		Optional<Usuario> encontrado = idUsuario == null ? Optional.empty() : usuarioRepo.findById(idUsuario);
		if (encontrado.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_ENCONTRADO);
		}
		Usuario usuario = encontrado.get();
		if (usuario.getRol() == Role.ADMIN) {
			auditoriaService.registrar(idAdmin, AuditoriaService.USUARIO_RESTRINGIDO, AuditoriaService.ENTIDAD_USUARIO,
					idUsuario, ResultadoAuditoria.RECHAZADO, NO_RESTRINGIR_ADMIN);
			return Resultado.error(Resultado.SIN_PERMISO, NO_RESTRINGIR_ADMIN);
		}

		String limpio = motivo.trim();
		usuario.setEstado(EstadoUsuario.RESTRINGIDO);
		usuario.setMotivoRestriccion(limpio);
		Usuario guardado = usuarioRepo.save(usuario);

		notificacionService.enviar(idUsuario, NotificacionService.CUENTA_RESTRINGIDA, AVISO_RESTRINGIDA + limpio);
		auditoriaService.registrar(idAdmin, AuditoriaService.USUARIO_RESTRINGIDO, AuditoriaService.ENTIDAD_USUARIO,
				idUsuario, ResultadoAuditoria.EXITOSO, limpio);
		return Resultado.exito(convertirDTO(guardado));
	}

	/**
	 * Habilita una cuenta restringida o inactiva (administrador, HU-12): queda
	 * ACTIVO y sin motivo de restricción, y se le avisa al usuario.
	 * <p>
	 * Igual que el proyecto base, el motivo es opcional: devolver el acceso no
	 * perjudica a nadie, así que solo queda en la auditoría si se envía.
	 * </p>
	 *
	 * @param idUsuario usuario a habilitar
	 * @param motivo    motivo opcional, de máximo {@value LimitesTexto#MOTIVO}
	 *                  caracteres
	 * @param idAdmin   administrador que la habilita
	 * @return el usuario; DATOS_INVALIDOS si el motivo es muy largo;
	 *         NO_ENCONTRADO si no existe; ESTADO_INVALIDO si ya estaba activa
	 */
	@Transactional
	public Resultado<UsuarioDTO> liberar(Long idUsuario, String motivo, Long idAdmin) {
		if (LimitesTexto.excede(motivo, LimitesTexto.MOTIVO)) {
			return Resultado.error(Resultado.DATOS_INVALIDOS, MOTIVO_LARGO);
		}
		Optional<Usuario> encontrado = idUsuario == null ? Optional.empty() : usuarioRepo.findById(idUsuario);
		if (encontrado.isEmpty()) {
			return Resultado.error(Resultado.NO_ENCONTRADO, NO_ENCONTRADO);
		}
		Usuario usuario = encontrado.get();
		if (usuario.getEstado() == EstadoUsuario.ACTIVO) {
			auditoriaService.registrar(idAdmin, AuditoriaService.USUARIO_LIBERADO, AuditoriaService.ENTIDAD_USUARIO,
					idUsuario, ResultadoAuditoria.RECHAZADO, YA_ACTIVA);
			return Resultado.error(Resultado.ESTADO_INVALIDO, YA_ACTIVA);
		}

		EstadoUsuario anterior = usuario.getEstado();
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setMotivoRestriccion(null);
		Usuario guardado = usuarioRepo.save(usuario);

		notificacionService.enviar(idUsuario, NotificacionService.CUENTA_LIBERADA, AVISO_LIBERADA);
		auditoriaService.registrar(idAdmin, AuditoriaService.USUARIO_LIBERADO, AuditoriaService.ENTIDAD_USUARIO,
				idUsuario, ResultadoAuditoria.EXITOSO,
				"Estaba " + anterior + (esVacio(motivo) ? "" : ". Motivo: " + motivo.trim()));
		return Resultado.exito(convertirDTO(guardado));
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
