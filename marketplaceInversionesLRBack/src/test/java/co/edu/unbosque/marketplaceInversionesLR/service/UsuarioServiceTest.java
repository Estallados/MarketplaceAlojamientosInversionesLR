package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas unitarias del registro (HU-06). El repositorio, la auditoría y el
 * envío del código se simulan, así que no necesitan base de datos.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

	/** Contraseña que cumple todas las reglas. */
	private static final String CONTRASENIA = "Clave#2026";

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private VerificacionCorreoService verificacionCorreoService;

	@Mock
	private NotificacionService notificacionService;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	private UsuarioService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new UsuarioService(usuarioRepo, auditoriaService, verificacionCorreoService, passwordEncoder,
				new ModelMapper(), notificacionService);
	}

	@Test
	@DisplayName("CA-030 Crea la cuenta con el rol elegido, sin verificar, y envía el código de bienvenida")
	void creaLaCuentaYEnviaElCodigo_CA030() {
		when(usuarioRepo.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> conId(inv.getArgument(0), 7L));

		int resultado = servicio.create(datos("  Ana Mora ", " Ana@Correo.com ", CONTRASENIA, "+57 300 123-4567", Role.ANFITRION));

		assertThat(resultado).isEqualTo(UsuarioService.CREADO);
		Usuario guardado = capturarGuardado();
		assertThat(guardado.getRol()).isEqualTo(Role.ANFITRION);
		assertThat(guardado.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(guardado.getNombre()).isEqualTo("Ana Mora");
		assertThat(guardado.esCorreoVerificado()).isFalse();
		assertThat(AESUtil.decrypt(guardado.getCorreo())).isEqualTo("ana@correo.com");
		assertThat(guardado.getCorreoHash()).isEqualTo(UsuarioService.hashCorreo("ana@correo.com"));
		assertThat(passwordEncoder.matches(CONTRASENIA, guardado.getContrasenia())).isTrue();
		assertThat(guardado.getTelefono()).isEqualTo("+573001234567");
		verify(auditoriaService).registrar(eq(7L), eq(AuditoriaService.REGISTRO_USUARIO),
				eq(AuditoriaService.ENTIDAD_USUARIO), eq(7L), eq(ResultadoAuditoria.EXITOSO), anyString());
		verify(verificacionCorreoService).enviarCodigo(guardado, "ana@correo.com", "Ana Mora", true);
	}

	@Test
	@DisplayName("CA-030 Sin rol, la cuenta se crea como HUESPED")
	void sinRolUsaHuesped_CA030() {
		when(usuarioRepo.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

		assertThat(servicio.create(datos("Ana", "ana@correo.com", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.CREADO);
		assertThat(capturarGuardado().getRol()).isEqualTo(Role.HUESPED);
	}

	@Test
	@DisplayName("CA-031 Si faltan datos obligatorios no se crea la cuenta")
	void faltanDatosNoRegistra_CA031() {
		assertThat(servicio.create(datos(" ", "ana@correo.com", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.DATOS_INVALIDOS);
		assertThat(servicio.create(datos("Ana", null, CONTRASENIA, null, null))).isEqualTo(UsuarioService.DATOS_INVALIDOS);
		assertThat(servicio.create(datos("Ana", "ana@correo.com", "", null, null))).isEqualTo(UsuarioService.DATOS_INVALIDOS);
		assertThat(servicio.create(null)).isEqualTo(UsuarioService.DATOS_INVALIDOS);

		verify(usuarioRepo, never()).saveAndFlush(any());
		verify(verificacionCorreoService, never()).enviarCodigo(any(), anyString(), anyString(), eq(true));
	}

	@Test
	@DisplayName("CA-031 Un correo con formato inválido, un nombre muy largo o un teléfono inválido se rechazan")
	void datosInvalidosNoRegistra_CA031() {
		assertThat(servicio.create(datos("Ana", "ana-sin-arroba", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.DATOS_INVALIDOS);
		assertThat(servicio.create(datos("A".repeat(256), "ana@correo.com", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.DATOS_INVALIDOS);
		assertThat(servicio.create(datos("Ana", "ana@correo.com", CONTRASENIA, "12-34", null)))
				.isEqualTo(UsuarioService.DATOS_INVALIDOS);

		verify(usuarioRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("CA-135 Una contraseña débil no crea la cuenta")
	void contraseniaDebilNoRegistra_CA135() {
		assertThat(servicio.create(datos("Ana", "ana@correo.com", "clave", null, Role.HUESPED)))
				.isEqualTo(UsuarioService.CONTRASENIA_DEBIL);

		verify(usuarioRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("El registro público no crea administradores y deja el intento en la auditoría")
	void rolAdminNoSePermite() {
		assertThat(servicio.create(datos("Ana", "ana@correo.com", CONTRASENIA, null, Role.ADMIN)))
				.isEqualTo(UsuarioService.ROL_NO_PERMITIDO);

		verify(usuarioRepo, never()).saveAndFlush(any());
		verify(auditoriaService).registrar(isNull(), eq(AuditoriaService.REGISTRO_USUARIO),
				eq(AuditoriaService.ENTIDAD_USUARIO), isNull(), eq(ResultadoAuditoria.RECHAZADO), anyString());
	}

	@Test
	@DisplayName("Si el correo ya existe no se crea otra cuenta")
	void correoExistenteNoRegistra() {
		when(usuarioRepo.existsByCorreoHash(anyString())).thenReturn(true);

		assertThat(servicio.create(datos("Ana", "ana@correo.com", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.CORREO_DUPLICADO);
		verify(usuarioRepo, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("Si dos registros con el mismo correo llegan a la vez, la restricción única responde duplicado")
	void registroSimultaneoRespondeDuplicado() {
		when(usuarioRepo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_usuario_correo_hash"));

		assertThat(servicio.create(datos("Ana", "ana@correo.com", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.CORREO_DUPLICADO);
	}

	@Test
	@DisplayName("Un error al guardar responde error interno y queda en la auditoría")
	void errorAlGuardarRespondeErrorInterno() {
		when(usuarioRepo.saveAndFlush(any())).thenThrow(new IllegalStateException("sin conexión"));

		assertThat(servicio.create(datos("Ana", "ana@correo.com", CONTRASENIA, null, null)))
				.isEqualTo(UsuarioService.ERROR_INTERNO);
		verify(auditoriaService).registrar(isNull(), eq(AuditoriaService.REGISTRO_USUARIO),
				eq(AuditoriaService.ENTIDAD_USUARIO), isNull(), eq(ResultadoAuditoria.ERROR), anyString());
	}

	@Test
	@DisplayName("Los correos se comparan sin espacios y en minúsculas")
	void normalizarCorreo() {
		assertThat(UsuarioService.normalizarCorreo("  Ana@Correo.COM ")).isEqualTo("ana@correo.com");
		assertThat(UsuarioService.normalizarCorreo(null)).isNull();
		assertThat(UsuarioService.normalizarTelefono("  ")).isNull();
	}

	/**
	 * Arma los datos de un registro.
	 *
	 * @return DTO del registro
	 */
	private static UsuarioDTO datos(String nombre, String correo, String contrasenia, String telefono, Role rol) {
		UsuarioDTO dto = new UsuarioDTO();
		dto.setNombre(nombre);
		dto.setCorreo(correo);
		dto.setContrasenia(contrasenia);
		dto.setTelefono(telefono);
		dto.setRol(rol);
		return dto;
	}

	/**
	 * Simula el id que asigna la base de datos.
	 *
	 * @return el mismo usuario con id
	 */
	private static Usuario conId(Usuario usuario, Long id) {
		usuario.setIdUsuario(id);
		return usuario;
	}

	/**
	 * Devuelve el usuario que el servicio mandó a guardar.
	 *
	 * @return usuario guardado
	 */
	private Usuario capturarGuardado() {
		ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarioRepo).saveAndFlush(captor.capture());
		return captor.getValue();
	}
}
