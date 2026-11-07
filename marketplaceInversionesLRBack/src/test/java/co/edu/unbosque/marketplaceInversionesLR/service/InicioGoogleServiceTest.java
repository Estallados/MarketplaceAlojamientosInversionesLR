package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.GoogleTokenVerificador;
import co.edu.unbosque.marketplaceInversionesLR.security.GoogleTokenVerificador.CuentaGoogle;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas unitarias del inicio de sesión con Google. El verificador del token,
 * la base de datos y la auditoría se simulan.
 */
@ExtendWith(MockitoExtension.class)
class InicioGoogleServiceTest {

	/** Token cualquiera; el verificador simulado decide si es válido. */
	private static final String TOKEN = "id-token";

	/** Correo de la cuenta de Google. */
	private static final String CORREO = "ana@gmail.com";

	@Mock
	private GoogleTokenVerificador verificador;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private SegundoFactorService segundoFactorService;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private PasswordEncoder passwordEncoder;

	private InicioGoogleService servicio;

	@BeforeEach
	void preparar() {
		servicio = new InicioGoogleService(verificador, usuarioRepo, segundoFactorService, auditoriaService,
				passwordEncoder);
	}

	@Test
	@DisplayName("Entrega el client ID del verificador")
	void clientId() {
		when(verificador.getClientId()).thenReturn("cliente");

		assertThat(servicio.clientId()).isEqualTo("cliente");
	}

	@Test
	@DisplayName("Sin client ID configurado no se inicia sesión")
	void noConfigurado() {
		when(verificador.estaConfigurado()).thenReturn(false);

		assertThat(servicio.iniciar(TOKEN, null, null).codigo()).isEqualTo(InicioGoogleService.NO_CONFIGURADO);
	}

	@Test
	@DisplayName("Un token inválido se rechaza y se audita")
	void tokenInvalido() {
		when(verificador.estaConfigurado()).thenReturn(true);
		when(verificador.verificar(TOKEN)).thenReturn(Optional.empty());

		assertThat(servicio.iniciar(TOKEN, null, null).codigo()).isEqualTo(InicioGoogleService.TOKEN_INVALIDO);
		verify(auditoriaService).registrar(null, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, null,
				ResultadoAuditoria.RECHAZADO, "Token de Google inválido");
	}

	@Test
	@DisplayName("La primera vez crea la cuenta verificada como HUESPED con el nombre de Google")
	void creaLaCuenta() {
		cuentaDeGoogle("  Ana María  ");
		sinCuenta();
		when(passwordEncoder.encode(anyString())).thenReturn("hash");
		when(usuarioRepo.save(any(Usuario.class))).thenAnswer(invocacion -> {
			Usuario guardado = invocacion.getArgument(0);
			guardado.setIdUsuario(9L);
			return guardado;
		});

		InicioGoogleService.Inicio inicio = servicio.iniciar(TOKEN, null, null);

		assertThat(inicio.codigo()).isEqualTo(InicioGoogleService.INICIADA);
		assertThat(inicio.cuentaNueva()).isTrue();
		Usuario usuario = inicio.usuario();
		assertThat(usuario.getNombre()).isEqualTo("Ana María");
		assertThat(usuario.getRol()).isEqualTo(Role.HUESPED);
		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(usuario.esCorreoVerificado()).isTrue();
		assertThat(usuario.getTotpHabilitado()).isFalse();
		assertThat(AESUtil.decrypt(usuario.getCorreo())).isEqualTo(CORREO);
		assertThat(usuario.getCorreoHash()).isEqualTo(UsuarioService.hashCorreo(CORREO));
		assertThat(usuario.getContrasenia()).isEqualTo("hash");
		verify(auditoriaService).registrar(9L, AuditoriaService.REGISTRO_USUARIO, AuditoriaService.ENTIDAD_USUARIO,
				9L, ResultadoAuditoria.EXITOSO, "Rol HUESPED, con Google");
	}

	@Test
	@DisplayName("Si Google no trae nombre se usa la parte del correo antes de la @; se puede elegir ANFITRION")
	void creaAnfitrionSinNombre() {
		cuentaDeGoogle(null);
		sinCuenta();
		when(usuarioRepo.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		InicioGoogleService.Inicio inicio = servicio.iniciar(TOKEN, Role.ANFITRION, null);

		assertThat(inicio.usuario().getNombre()).isEqualTo("ana");
		assertThat(inicio.usuario().getRol()).isEqualTo(Role.ANFITRION);
	}

	@Test
	@DisplayName("No se puede crear una cuenta ADMIN con Google")
	void rolAdminNoPermitido() {
		cuentaDeGoogle("Ana");
		sinCuenta();

		assertThat(servicio.iniciar(TOKEN, Role.ADMIN, null).codigo()).isEqualTo(InicioGoogleService.ROL_NO_PERMITIDO);
		verify(usuarioRepo, never()).save(any());
	}

	@Test
	@DisplayName("Dos creaciones simultáneas con el mismo correo: la segunda se rechaza")
	void creacionSimultanea() {
		cuentaDeGoogle("Ana");
		sinCuenta();
		when(usuarioRepo.save(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("duplicado"));

		assertThat(servicio.iniciar(TOKEN, null, null).codigo()).isEqualTo(InicioGoogleService.TOKEN_INVALIDO);
	}

	@Test
	@DisplayName("Con una cuenta existente inicia sesión, la verifica y la reactiva si estaba inactiva")
	void cuentaExistente() {
		cuentaDeGoogle("Ana");
		Usuario usuario = existente(EstadoUsuario.INACTIVO, false, false);

		InicioGoogleService.Inicio inicio = servicio.iniciar(TOKEN, Role.ANFITRION, null);

		assertThat(inicio.codigo()).isEqualTo(InicioGoogleService.INICIADA);
		assertThat(inicio.cuentaNueva()).isFalse();
		assertThat(inicio.usuario()).isSameAs(usuario);
		assertThat(usuario.getRol()).isEqualTo(Role.HUESPED);
		assertThat(usuario.esCorreoVerificado()).isTrue();
		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		verify(usuarioRepo).save(usuario);
		verify(auditoriaService).registrar(3L, AuditoriaService.CUENTA_REACTIVADA, AuditoriaService.ENTIDAD_USUARIO,
				3L, ResultadoAuditoria.EXITOSO, "Reactivada al iniciar sesión con Google");
		verify(auditoriaService).registrar(3L, AuditoriaService.LOGIN, AuditoriaService.ENTIDAD_USUARIO, 3L,
				ResultadoAuditoria.EXITOSO, "Google");
	}

	@Test
	@DisplayName("Una cuenta activa y verificada solo registra el inicio de sesión")
	void cuentaActivaVerificada() {
		cuentaDeGoogle("Ana");
		existente(EstadoUsuario.ACTIVO, true, false);

		assertThat(servicio.iniciar(TOKEN, null, null).codigo()).isEqualTo(InicioGoogleService.INICIADA);
		verify(auditoriaService, never()).registrar(any(), eq(AuditoriaService.CORREO_VERIFICADO), any(), any(),
				any(), any());
		verify(auditoriaService, never()).registrar(any(), eq(AuditoriaService.CUENTA_REACTIVADA), any(), any(),
				any(), any());
	}

	@Test
	@DisplayName("Una cuenta restringida no entra con Google")
	void cuentaRestringida() {
		cuentaDeGoogle("Ana");
		existente(EstadoUsuario.RESTRINGIDO, true, false);

		assertThat(servicio.iniciar(TOKEN, null, null).codigo()).isEqualTo(InicioGoogleService.CUENTA_RESTRINGIDA);
		verify(usuarioRepo, never()).save(any());
	}

	@Test
	@DisplayName("Con segundo factor pide el código, rechaza uno inválido y acepta uno válido")
	void segundoFactor() {
		cuentaDeGoogle("Ana");
		Usuario usuario = existente(EstadoUsuario.ACTIVO, true, true);

		assertThat(servicio.iniciar(TOKEN, null, null).codigo())
				.isEqualTo(InicioGoogleService.REQUIERE_SEGUNDO_FACTOR);
		assertThat(servicio.iniciar(TOKEN, null, " ").codigo()).isEqualTo(InicioGoogleService.REQUIERE_SEGUNDO_FACTOR);

		when(segundoFactorService.verificar(usuario, "000000")).thenReturn(false);
		assertThat(servicio.iniciar(TOKEN, null, "000000").codigo())
				.isEqualTo(InicioGoogleService.SEGUNDO_FACTOR_INVALIDO);

		when(segundoFactorService.verificar(usuario, "123456")).thenReturn(true);
		assertThat(servicio.iniciar(TOKEN, null, "123456").codigo()).isEqualTo(InicioGoogleService.INICIADA);
	}

	/**
	 * Hace que el token sea válido para la cuenta de Google de prueba.
	 *
	 * @param nombre nombre que trae Google; puede ser null
	 */
	private void cuentaDeGoogle(String nombre) {
		when(verificador.estaConfigurado()).thenReturn(true);
		when(verificador.verificar(TOKEN)).thenReturn(Optional.of(new CuentaGoogle("109876", "Ana@Gmail.com ", nombre)));
	}

	/** Hace que el correo no tenga cuenta. */
	private void sinCuenta() {
		when(usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(CORREO))).thenReturn(Optional.empty());
	}

	/**
	 * Hace que el correo ya tenga una cuenta.
	 *
	 * @param estado     estado de la cuenta
	 * @param verificado si el correo ya está verificado
	 * @param totp       si tiene el segundo factor habilitado
	 * @return la cuenta
	 */
	private Usuario existente(EstadoUsuario estado, boolean verificado, boolean totp) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(3L);
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(estado);
		usuario.setCorreoVerificado(verificado);
		usuario.setTotpHabilitado(totp);
		when(usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(CORREO))).thenReturn(Optional.of(usuario));
		return usuario;
	}
}
