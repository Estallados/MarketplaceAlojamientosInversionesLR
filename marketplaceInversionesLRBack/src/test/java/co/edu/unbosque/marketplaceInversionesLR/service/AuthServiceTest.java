package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.TokenRecuperacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.TokenRecuperacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.CorreoUtil;

/**
 * Pruebas unitarias de la recuperación de contraseña. El correo, la base de
 * datos y la auditoría se simulan; el almacén de tokens es el real (en
 * memoria).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	/** Correo de la cuenta de prueba. */
	private static final String CORREO = "ana@correo.com";

	/** Contraseña nueva que cumple las reglas. */
	private static final String NUEVA = "Nueva#2026";

	/** Contraseña actual de la cuenta que se reactiva. */
	private static final String CLAVE = "Segura#2026";

	/** Token dentro del cuerpo del correo (línea con sangría). */
	private static final Pattern TOKEN = Pattern.compile("\\n {4}(\\S+)\\r?\\n");

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private CorreoUtil correoUtil;

	private final TokenRecuperacionRepository tokenRepo = new TokenRecuperacionRepository();

	private AuthService servicio;

	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new AuthService(usuarioRepo, tokenRepo, passwordEncoder, auditoriaService, correoUtil);
		usuario = new Usuario();
		usuario.setIdUsuario(4L);
		usuario.setNombre("Ana");
		usuario.setContrasenia("hash-anterior");
	}

	@Test
	@DisplayName("Con un correo registrado envía un token de un solo uso y guarda solo su hash")
	void solicitaLaRecuperacion() {
		String token = solicitarYObtenerToken();

		TokenRecuperacion registro = tokenRepo.findAll().get(0);
		assertThat(registro.getIdUsuario()).isEqualTo(4L);
		assertThat(registro.getTokenHash()).isEqualTo(AESUtil.hash(token)).isNotEqualTo(token);
		assertThat(registro.getUsado()).isFalse();
		assertThat(registro.getFechaExpiracion()).isAfter(LocalDateTime.now().plusMinutes(29));
		verify(auditoriaService).registrar(4L, AuditoriaService.RECUPERACION_SOLICITADA,
				AuditoriaService.ENTIDAD_USUARIO, 4L, ResultadoAuditoria.EXITOSO, null);
	}

	@Test
	@DisplayName("Pedir otro token invalida el anterior")
	void nuevoTokenInvalidaElAnterior() {
		String primero = solicitarYObtenerToken();
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);
		servicio.solicitarRecuperacion(CORREO);

		assertThat(servicio.restablecerContrasenia(primero, NUEVA)).isEqualTo(1);
		assertThat(tokenRepo.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Con un correo no registrado responde igual, sin enviar nada")
	void correoNoRegistrado() {
		when(usuarioRepo.findByCorreoHash(anyString())).thenReturn(Optional.empty());

		assertThat(servicio.solicitarRecuperacion("nadie@correo.com")).isZero();

		verify(correoUtil, never()).enviarCorreo(anyString(), anyString(), anyString());
		assertThat(tokenRepo.count()).isZero();
		verify(auditoriaService).registrar(null, AuditoriaService.RECUPERACION_SOLICITADA,
				AuditoriaService.ENTIDAD_USUARIO, null, ResultadoAuditoria.RECHAZADO, "Correo no registrado");
	}

	@Test
	@DisplayName("Sin correo se informa que falta el dato")
	void sinCorreo() {
		assertThat(servicio.solicitarRecuperacion(null)).isEqualTo(2);
		assertThat(servicio.solicitarRecuperacion(" ")).isEqualTo(2);
	}

	@Test
	@DisplayName("Si el correo no se puede enviar, el token igual queda guardado y se audita el error")
	void correoFallido() {
		cuentaExistente();
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(false);

		assertThat(servicio.solicitarRecuperacion(CORREO)).isZero();

		assertThat(tokenRepo.count()).isEqualTo(1);
		verify(auditoriaService).registrar(4L, AuditoriaService.RECUPERACION_SOLICITADA,
				AuditoriaService.ENTIDAD_USUARIO, 4L, ResultadoAuditoria.ERROR, "No se pudo enviar el correo");
	}

	@Test
	@DisplayName("Con un token vigente cambia la contraseña y el token ya no sirve")
	void restableceLaContrasenia() {
		String token = solicitarYObtenerToken();
		when(usuarioRepo.findById(4L)).thenReturn(Optional.of(usuario));
		when(passwordEncoder.encode(NUEVA)).thenReturn("hash-nuevo");

		assertThat(servicio.restablecerContrasenia(token, NUEVA)).isZero();

		assertThat(usuario.getContrasenia()).isEqualTo("hash-nuevo");
		verify(usuarioRepo).save(usuario);
		assertThat(tokenRepo.findAll().get(0).getUsado()).isTrue();
		verify(auditoriaService).registrar(4L, AuditoriaService.CONTRASENIA_RESTABLECIDA,
				AuditoriaService.ENTIDAD_USUARIO, 4L, ResultadoAuditoria.EXITOSO, null);
		assertThat(servicio.restablecerContrasenia(token, NUEVA)).isEqualTo(1);
	}

	@Test
	@DisplayName("Un token inexistente o vencido no cambia la contraseña")
	void tokenInvalidoOVencido() {
		assertThat(servicio.restablecerContrasenia("no-existe", NUEVA)).isEqualTo(1);

		String token = solicitarYObtenerToken();
		tokenRepo.findAll().get(0).setFechaExpiracion(LocalDateTime.now().minusSeconds(1));
		assertThat(servicio.restablecerContrasenia(token, NUEVA)).isEqualTo(1);

		verify(passwordEncoder, never()).encode(anyString());
		verify(auditoriaService, org.mockito.Mockito.times(2)).registrar(eq(null),
				eq(AuditoriaService.CONTRASENIA_RESTABLECIDA), eq(AuditoriaService.ENTIDAD_USUARIO), eq(null),
				eq(ResultadoAuditoria.RECHAZADO), anyString());
	}

	@Test
	@DisplayName("Si la cuenta del token ya no existe no se cambia nada")
	void cuentaInexistente() {
		String token = solicitarYObtenerToken();
		when(usuarioRepo.findById(4L)).thenReturn(Optional.empty());

		assertThat(servicio.restablecerContrasenia(token, NUEVA)).isEqualTo(1);
		verify(passwordEncoder, never()).encode(anyString());
	}

	@Test
	@DisplayName("Faltan datos o la contraseña nueva es débil")
	void datosInvalidos() {
		assertThat(servicio.restablecerContrasenia(null, NUEVA)).isEqualTo(2);
		assertThat(servicio.restablecerContrasenia(" ", NUEVA)).isEqualTo(2);
		assertThat(servicio.restablecerContrasenia("token", null)).isEqualTo(2);
		assertThat(servicio.restablecerContrasenia("token", " ")).isEqualTo(2);
		assertThat(servicio.restablecerContrasenia("token", "debil")).isEqualTo(3);
	}

	@Test
	@DisplayName("CA-050 Con credenciales correctas una cuenta inactiva vuelve a ACTIVO")
	void reactivaLaCuenta_CA050() {
		cuentaConCredenciales(EstadoUsuario.INACTIVO, true);

		assertThat(servicio.reactivarCuenta(" ANA@correo.com ", CLAVE)).isZero();

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		verify(usuarioRepo).save(usuario);
		verify(auditoriaService).registrar(4L, AuditoriaService.CUENTA_REACTIVADA, AuditoriaService.ENTIDAD_USUARIO, 4L,
				ResultadoAuditoria.EXITOSO, null);
	}

	@Test
	@DisplayName("Con la contraseña incorrecta no se reactiva la cuenta")
	void reactivarConContraseniaIncorrecta() {
		cuentaConCredenciales(EstadoUsuario.INACTIVO, false);

		assertThat(servicio.reactivarCuenta(CORREO, CLAVE)).isEqualTo(1);

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
		verify(usuarioRepo, never()).save(usuario);
		verify(auditoriaService).registrar(null, AuditoriaService.CUENTA_REACTIVADA, AuditoriaService.ENTIDAD_USUARIO,
				null, ResultadoAuditoria.RECHAZADO, "Credenciales inválidas");
	}

	@Test
	@DisplayName("Un correo no registrado responde como credenciales inválidas")
	void reactivarCorreoNoRegistrado() {
		when(usuarioRepo.findByCorreoHash(anyString())).thenReturn(Optional.empty());

		assertThat(servicio.reactivarCuenta("nadie@correo.com", CLAVE)).isEqualTo(1);
	}

	@Test
	@DisplayName("Una cuenta restringida por un administrador no puede reactivarse sola")
	void cuentaRestringidaNoSeReactiva() {
		cuentaConCredenciales(EstadoUsuario.RESTRINGIDO, true);

		assertThat(servicio.reactivarCuenta(CORREO, CLAVE)).isEqualTo(3);

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		verify(usuarioRepo, never()).save(usuario);
		verify(auditoriaService).registrar(4L, AuditoriaService.CUENTA_REACTIVADA, AuditoriaService.ENTIDAD_USUARIO, 4L,
				ResultadoAuditoria.RECHAZADO, "Cuenta restringida por un administrador");
	}

	@Test
	@DisplayName("Una cuenta que ya está activa no se vuelve a reactivar")
	void cuentaActivaNoSeReactiva() {
		cuentaConCredenciales(EstadoUsuario.ACTIVO, true);

		assertThat(servicio.reactivarCuenta(CORREO, CLAVE)).isEqualTo(4);

		verify(usuarioRepo, never()).save(usuario);
	}

	@Test
	@DisplayName("Sin correo o sin contraseña se informa que faltan datos")
	void reactivarSinDatos() {
		assertThat(servicio.reactivarCuenta(null, CLAVE)).isEqualTo(2);
		assertThat(servicio.reactivarCuenta(" ", CLAVE)).isEqualTo(2);
		assertThat(servicio.reactivarCuenta(CORREO, null)).isEqualTo(2);
		assertThat(servicio.reactivarCuenta(CORREO, " ")).isEqualTo(2);
	}

	/**
	 * Deja la cuenta de prueba en un estado y simula la comparación de su
	 * contraseña.
	 *
	 * @param estado      estado de la cuenta
	 * @param coincide    resultado de comparar la contraseña
	 */
	private void cuentaConCredenciales(EstadoUsuario estado, boolean coincide) {
		usuario.setEstado(estado);
		cuentaExistente();
		when(passwordEncoder.matches(CLAVE, "hash-anterior")).thenReturn(coincide);
	}

	/**
	 * Pide la recuperación para la cuenta de prueba y saca el token del correo.
	 *
	 * @return token enviado
	 */
	private String solicitarYObtenerToken() {
		cuentaExistente();
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);
		assertThat(servicio.solicitarRecuperacion(" ANA@correo.com ")).isZero();

		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		verify(correoUtil).enviarCorreo(eq(CORREO), eq("Recupera tu contraseña - Marketplace Inversiones LR"),
				cuerpo.capture());
		assertThat(cuerpo.getValue()).contains("Hola Ana").contains("30 minutos");
		Matcher matcher = TOKEN.matcher(cuerpo.getValue());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	/** Hace que el repositorio encuentre la cuenta de prueba por su correo. */
	private void cuentaExistente() {
		when(usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(CORREO))).thenReturn(Optional.of(usuario));
	}
}
