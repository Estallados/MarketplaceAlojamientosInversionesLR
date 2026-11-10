package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas unitarias de la consulta y actualización del perfil propio (HU-09).
 * La base de datos y la auditoría se simulan.
 */
@ExtendWith(MockitoExtension.class)
class PerfilUsuarioServiceTest {

	/** Correo de la cuenta de prueba. */
	private static final String CORREO = "ana@correo.com";

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private VerificacionCorreoService verificacionCorreoService;

	@Mock
	private NotificacionService notificacionService;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UsuarioService servicio;

	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new UsuarioService(usuarioRepo, auditoriaService, verificacionCorreoService, passwordEncoder,
				new ModelMapper(), notificacionService);
		usuario = new Usuario();
		usuario.setIdUsuario(3L);
		usuario.setNombre("Ana");
		usuario.setCorreo(AESUtil.encrypt(CORREO));
		usuario.setCorreoHash(UsuarioService.hashCorreo(CORREO));
		usuario.setTelefono("3001234567");
		usuario.setContrasenia("hash-de-la-clave");
		usuario.setSecretoTotp("secreto-cifrado");
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setTotpHabilitado(false);
		usuario.setCorreoVerificado(true);
		usuario.setFechaRegistro(LocalDateTime.of(2026, 11, 1, 9, 0));
	}

	@Test
	@DisplayName("CA-042 El perfil trae la información básica de la cuenta, con el correo en claro y sin datos sensibles")
	void consultaElPerfil_CA042() {
		cuentaExistente();

		UsuarioDTO perfil = servicio.obtenerUsuarioPorCorreo(" ANA@correo.com ");

		assertThat(perfil.getIdUsuario()).isEqualTo(3L);
		assertThat(perfil.getNombre()).isEqualTo("Ana");
		assertThat(perfil.getCorreo()).isEqualTo(CORREO);
		assertThat(perfil.getTelefono()).isEqualTo("3001234567");
		assertThat(perfil.getRol()).isEqualTo(Role.HUESPED);
		assertThat(perfil.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(perfil.getCorreoVerificado()).isTrue();
		assertThat(perfil.getTotpHabilitado()).isFalse();
		assertThat(perfil.getFechaRegistro()).isEqualTo(LocalDateTime.of(2026, 11, 1, 9, 0));
		assertThat(perfil.getContrasenia()).isNull();
		assertThat(perfil.getCorreoHash()).isNull();
		assertThat(perfil.getSecretoTotp()).isNull();
	}

	@Test
	@DisplayName("Sin correo o con una cuenta que no existe no hay perfil")
	void perfilInexistente() {
		when(usuarioRepo.findByCorreoHash(any())).thenReturn(Optional.empty());

		assertThat(servicio.obtenerUsuarioPorCorreo("nadie@correo.com")).isNull();
		assertThat(servicio.obtenerUsuarioPorCorreo(null)).isNull();
		assertThat(servicio.obtenerUsuarioPorCorreo(" ")).isNull();
	}

	@Test
	@DisplayName("CA-046 Con datos válidos actualiza el nombre y el teléfono normalizado y lo audita")
	void actualizaElPerfil_CA046() {
		cuentaExistente();

		assertThat(servicio.actualizarPerfil(CORREO, "  Ana María  ", "+57 (300) 765-4321")).isZero();

		assertThat(usuario.getNombre()).isEqualTo("Ana María");
		assertThat(usuario.getTelefono()).isEqualTo("+573007654321");
		verify(usuarioRepo).save(usuario);
		verify(auditoriaService).registrar(3L, AuditoriaService.PERFIL_ACTUALIZADO, AuditoriaService.ENTIDAD_USUARIO,
				3L, ResultadoAuditoria.EXITOSO, "Campos: nombre telefono");
	}

	@Test
	@DisplayName("Los campos que no se envían se dejan como están")
	void soloLoEnviado() {
		cuentaExistente();

		assertThat(servicio.actualizarPerfil(CORREO, null, "3109876543")).isZero();
		assertThat(usuario.getNombre()).isEqualTo("Ana");
		assertThat(usuario.getTelefono()).isEqualTo("3109876543");

		assertThat(servicio.actualizarPerfil(CORREO, "Ana Prieto", null)).isZero();
		assertThat(usuario.getTelefono()).isEqualTo("3109876543");
		verify(auditoriaService).registrar(3L, AuditoriaService.PERFIL_ACTUALIZADO, AuditoriaService.ENTIDAD_USUARIO,
				3L, ResultadoAuditoria.EXITOSO, "Campos: nombre");
	}

	@Test
	@DisplayName("CA-047 Con datos inválidos o incompletos no se guarda nada")
	void rechazaDatosInvalidos_CA047() {
		assertThat(servicio.actualizarPerfil(CORREO, null, null)).isEqualTo(2);
		assertThat(servicio.actualizarPerfil(CORREO, "   ", null)).isEqualTo(2);
		assertThat(servicio.actualizarPerfil(CORREO, "x".repeat(256), null)).isEqualTo(2);
		assertThat(servicio.actualizarPerfil(CORREO, null, "123")).isEqualTo(2);
		assertThat(servicio.actualizarPerfil(CORREO, null, "abc-defg-hij")).isEqualTo(2);
		assertThat(servicio.actualizarPerfil(CORREO, "Ana", "1234567890123456")).isEqualTo(2);

		verifyNoInteractions(usuarioRepo, auditoriaService);
	}

	@Test
	@DisplayName("Si la cuenta del token ya no existe responde que no se encontró")
	void cuentaInexistente() {
		when(usuarioRepo.findByCorreoHash(any())).thenReturn(Optional.empty());

		assertThat(servicio.actualizarPerfil("nadie@correo.com", "Ana", null)).isEqualTo(1);
		assertThat(servicio.actualizarPerfil(" ", "Ana", null)).isEqualTo(1);
		verify(usuarioRepo, never()).save(any());
	}

	@Test
	@DisplayName("CA-050 Una cuenta activa pasa a INACTIVO al desactivarla")
	void desactivaLaCuenta_CA050() {
		cuentaExistente();

		assertThat(servicio.desactivarCuenta(" ANA@correo.com ")).isZero();

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
		verify(usuarioRepo).save(usuario);
		verify(auditoriaService).registrar(3L, AuditoriaService.CUENTA_DESACTIVADA, AuditoriaService.ENTIDAD_USUARIO,
				3L, ResultadoAuditoria.EXITOSO, null);
	}

	@Test
	@DisplayName("CA-053 Solo se busca y se desactiva la cuenta del correo que viene en el token")
	void soloDesactivaLaCuentaDelToken_CA053() {
		cuentaExistente();

		servicio.desactivarCuenta(CORREO);

		verify(usuarioRepo).findByCorreoHash(UsuarioService.hashCorreo(CORREO));
		verify(usuarioRepo, never()).findById(any());
		verify(usuarioRepo).save(usuario);
	}

	@Test
	@DisplayName("Sin correo o con una cuenta que no existe responde que no se encontró")
	void desactivarCuentaInexistente() {
		when(usuarioRepo.findByCorreoHash(any())).thenReturn(Optional.empty());

		assertThat(servicio.desactivarCuenta("nadie@correo.com")).isEqualTo(1);
		assertThat(servicio.desactivarCuenta(null)).isEqualTo(1);
		assertThat(servicio.desactivarCuenta(" ")).isEqualTo(1);
		verify(usuarioRepo, never()).save(any());
	}

	@Test
	@DisplayName("Un administrador no puede desactivar su cuenta")
	void administradorNoSeDesactiva() {
		cuentaExistente();
		usuario.setRol(Role.ADMIN);

		assertThat(servicio.desactivarCuenta(CORREO)).isEqualTo(2);

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		verify(usuarioRepo, never()).save(any());
	}

	@Test
	@DisplayName("Una cuenta inactiva o restringida no se puede desactivar")
	void soloSeDesactivaUnaCuentaActiva() {
		cuentaExistente();

		usuario.setEstado(EstadoUsuario.INACTIVO);
		assertThat(servicio.desactivarCuenta(CORREO)).isEqualTo(2);
		usuario.setEstado(EstadoUsuario.RESTRINGIDO);
		assertThat(servicio.desactivarCuenta(CORREO)).isEqualTo(2);

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		verify(usuarioRepo, never()).save(any());
	}

	/** Hace que el repositorio encuentre la cuenta de prueba por su correo. */
	private void cuentaExistente() {
		when(usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(CORREO))).thenReturn(Optional.of(usuario));
	}
}
