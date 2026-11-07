package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.CodigoVerificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.CorreoUtil;

/**
 * Pruebas unitarias de la verificación del correo con código. El correo y la
 * base de datos se simulan; el almacén de códigos es el real (en memoria).
 */
@ExtendWith(MockitoExtension.class)
class VerificacionCorreoServiceTest {

	/** Correo de la cuenta de prueba. */
	private static final String CORREO = "ana@correo.com";

	/** Código de 6 dígitos dentro del cuerpo del correo. */
	private static final Pattern CODIGO = Pattern.compile("\\b(\\d{6})\\b");

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private CorreoUtil correoUtil;

	@Mock
	private AuditoriaService auditoriaService;

	private final CodigoVerificacionRepository codigoRepo = new CodigoVerificacionRepository();

	private VerificacionCorreoService servicio;

	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new VerificacionCorreoService(usuarioRepo, codigoRepo, correoUtil, auditoriaService);
		usuario = new Usuario();
		usuario.setIdUsuario(5L);
		usuario.setNombre("Ana");
		usuario.setCorreoVerificado(false);
	}

	@Test
	@DisplayName("Al registrarse se envía el correo de bienvenida con un código de 6 dígitos")
	void enviaElCodigoDeBienvenida() {
		when(correoUtil.enviarCorreo(eq(CORREO), anyString(), anyString())).thenReturn(true);

		assertThat(servicio.enviarCodigo(usuario, CORREO, "Ana", true)).isTrue();

		String cuerpo = cuerpoEnviado();
		assertThat(cuerpo).contains("Hola Ana").contains("Bienvenido").contains("15 minutos");
		assertThat(codigoRepo.findVigente(5L)).isPresent();
		// Solo se guarda el hash del código, nunca el código en claro
		assertThat(codigoRepo.findVigente(5L).get().getCodigoHash()).isNotEqualTo(codigoDe(cuerpo));
		verify(auditoriaService).registrar(5L, AuditoriaService.CODIGO_VERIFICACION_ENVIADO,
				AuditoriaService.ENTIDAD_USUARIO, 5L, ResultadoAuditoria.EXITOSO, null);
	}

	@Test
	@DisplayName("Si el correo no se puede enviar, el código igual queda guardado y se audita el error")
	void correoFallidoGuardaElCodigo() {
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(false);

		assertThat(servicio.enviarCodigo(usuario, CORREO, "Ana", true)).isFalse();

		assertThat(codigoRepo.findVigente(5L)).isPresent();
		verify(auditoriaService).registrar(eq(5L), eq(AuditoriaService.CODIGO_VERIFICACION_ENVIADO),
				eq(AuditoriaService.ENTIDAD_USUARIO), eq(5L), eq(ResultadoAuditoria.ERROR), anyString());
	}

	@Test
	@DisplayName("Con el código correcto el correo queda verificado y el código ya no sirve")
	void codigoCorrectoVerifica() {
		String codigo = enviarYObtenerCodigo();
		cuentaExistente();

		assertThat(servicio.verificar(CORREO, codigo)).isEqualTo(VerificacionCorreoService.VERIFICADO);

		assertThat(usuario.esCorreoVerificado()).isTrue();
		verify(usuarioRepo).save(usuario);
		assertThat(codigoRepo.findVigente(5L)).isEmpty();
	}

	@Test
	@DisplayName("Un código incorrecto se rechaza y al quinto intento el código deja de servir")
	void intentosFallidos() {
		String codigo = enviarYObtenerCodigo();
		cuentaExistente();
		String incorrecto = codigo.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < 4; i++) {
			assertThat(servicio.verificar(CORREO, incorrecto)).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);
		}
		assertThat(servicio.verificar(CORREO, incorrecto)).isEqualTo(VerificacionCorreoService.INTENTOS_AGOTADOS);
		assertThat(servicio.verificar(CORREO, codigo)).isEqualTo(VerificacionCorreoService.INTENTOS_AGOTADOS);
		assertThat(usuario.esCorreoVerificado()).isFalse();
	}

	@Test
	@DisplayName("Un código vencido no verifica el correo")
	void codigoVencido() {
		String codigo = enviarYObtenerCodigo();
		cuentaExistente();
		codigoRepo.findVigente(5L).orElseThrow().setFechaExpiracion(LocalDateTime.now().minusSeconds(1));

		assertThat(servicio.verificar(CORREO, codigo)).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);
		verify(auditoriaService).registrar(eq(5L), eq(AuditoriaService.CORREO_VERIFICADO),
				eq(AuditoriaService.ENTIDAD_USUARIO), eq(5L), eq(ResultadoAuditoria.RECHAZADO), anyString());
	}

	@Test
	@DisplayName("Faltan datos, cuenta inexistente o correo ya verificado")
	void casosSinVerificar() {
		assertThat(servicio.verificar(" ", "123456")).isEqualTo(VerificacionCorreoService.DATOS_INVALIDOS);
		assertThat(servicio.verificar(CORREO, null)).isEqualTo(VerificacionCorreoService.DATOS_INVALIDOS);

		when(usuarioRepo.findByCorreoHash(anyString())).thenReturn(Optional.empty());
		assertThat(servicio.verificar("nadie@correo.com", "123456")).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);

		usuario.setCorreoVerificado(true);
		cuentaExistente();
		assertThat(servicio.verificar(CORREO, "123456")).isEqualTo(VerificacionCorreoService.YA_VERIFICADO);
	}

	@Test
	@DisplayName("Solo se puede pedir un código nuevo una vez por minuto")
	void reenvioUnaVezPorMinuto() {
		enviarYObtenerCodigo();
		cuentaExistente();

		assertThat(servicio.reenviar(CORREO)).isEqualTo(VerificacionCorreoService.ESPERAR);

		codigoRepo.findVigente(5L).orElseThrow().setFechaCreacion(LocalDateTime.now().minusMinutes(2));
		assertThat(servicio.reenviar(CORREO)).isEqualTo(VerificacionCorreoService.ENVIADO);
		verify(correoUtil).enviarCorreo(eq(CORREO), eq("Tu nuevo código de verificación - Marketplace Inversiones LR"),
				anyString());
	}

	@Test
	@DisplayName("Reenviar a una cuenta que no existe responde igual, sin enviar nada")
	void reenvioACuentaInexistente() {
		when(usuarioRepo.findByCorreoHash(anyString())).thenReturn(Optional.empty());

		assertThat(servicio.reenviar("nadie@correo.com")).isEqualTo(VerificacionCorreoService.ENVIADO);
		assertThat(servicio.reenviar(" ")).isEqualTo(VerificacionCorreoService.DATOS_INVALIDOS);
		verify(correoUtil, never()).enviarCorreo(anyString(), anyString(), anyString());
		verifyNoInteractions(auditoriaService);
	}

	@Test
	@DisplayName("Al iniciar sesión se envía un código por correo y solo se reenvía pasado un minuto")
	void enviaElCodigoDeInicio() {
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);

		assertThat(servicio.enviarCodigoInicio(usuario, CORREO)).isEqualTo(VerificacionCorreoService.ENVIADO);
		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		verify(correoUtil).enviarCorreo(eq(CORREO), eq("Tu código de inicio de sesión - Marketplace Inversiones LR"),
				cuerpo.capture());
		assertThat(cuerpo.getValue()).contains("Hola Ana").contains("iniciar sesión").contains("15 minutos");
		verify(auditoriaService).registrar(5L, AuditoriaService.CODIGO_VERIFICACION_ENVIADO,
				AuditoriaService.ENTIDAD_USUARIO, 5L, ResultadoAuditoria.EXITOSO, "Código de inicio de sesión");

		assertThat(servicio.enviarCodigoInicio(usuario, CORREO)).isEqualTo(VerificacionCorreoService.ESPERAR);

		codigoRepo.findVigente(5L).orElseThrow().setFechaCreacion(LocalDateTime.now().minusMinutes(2));
		assertThat(servicio.enviarCodigoInicio(usuario, CORREO)).isEqualTo(VerificacionCorreoService.ENVIADO);
	}

	@Test
	@DisplayName("Si el código de inicio agotó sus intentos se envía otro sin esperar")
	void codigoDeInicioAgotadoSeReemplaza() {
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);
		servicio.enviarCodigoInicio(usuario, CORREO);
		codigoRepo.findVigente(5L).orElseThrow().setIntentos(5);

		assertThat(servicio.enviarCodigoInicio(usuario, CORREO)).isEqualTo(VerificacionCorreoService.ENVIADO);
		assertThat(codigoRepo.findVigente(5L).orElseThrow().getIntentos()).isZero();
	}

	@Test
	@DisplayName("Si el correo de inicio no se puede enviar, el código igual queda guardado y se audita el error")
	void correoDeInicioFallido() {
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(false);

		assertThat(servicio.enviarCodigoInicio(usuario, CORREO)).isEqualTo(VerificacionCorreoService.ENVIADO);

		assertThat(codigoRepo.findVigente(5L)).isPresent();
		verify(auditoriaService).registrar(5L, AuditoriaService.CODIGO_VERIFICACION_ENVIADO,
				AuditoriaService.ENTIDAD_USUARIO, 5L, ResultadoAuditoria.ERROR, "No se pudo enviar el correo");
	}

	@Test
	@DisplayName("El código de inicio correcto se acepta una sola vez")
	void codigoDeInicioCorrecto() {
		String codigo = enviarCodigoInicioYObtenerlo();

		assertThat(servicio.verificarCodigoInicio(usuario, " " + codigo + " "))
				.isEqualTo(VerificacionCorreoService.VERIFICADO);
		assertThat(codigoRepo.findVigente(5L)).isEmpty();
		assertThat(servicio.verificarCodigoInicio(usuario, codigo)).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);
	}

	@Test
	@DisplayName("El código de inicio incorrecto se rechaza y al quinto intento deja de servir")
	void codigoDeInicioIncorrecto() {
		String codigo = enviarCodigoInicioYObtenerlo();
		String incorrecto = codigo.equals("000000") ? "111111" : "000000";

		assertThat(servicio.verificarCodigoInicio(usuario, null)).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);
		for (int i = 0; i < 3; i++) {
			assertThat(servicio.verificarCodigoInicio(usuario, incorrecto))
					.isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);
		}
		assertThat(servicio.verificarCodigoInicio(usuario, incorrecto))
				.isEqualTo(VerificacionCorreoService.INTENTOS_AGOTADOS);
		assertThat(servicio.verificarCodigoInicio(usuario, codigo)).isEqualTo(VerificacionCorreoService.INTENTOS_AGOTADOS);
	}

	@Test
	@DisplayName("El código de inicio vencido o inexistente no sirve")
	void codigoDeInicioVencido() {
		assertThat(servicio.verificarCodigoInicio(usuario, "123456")).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);

		String codigo = enviarCodigoInicioYObtenerlo();
		codigoRepo.findVigente(5L).orElseThrow().setFechaExpiracion(LocalDateTime.now().minusSeconds(1));
		assertThat(servicio.verificarCodigoInicio(usuario, codigo)).isEqualTo(VerificacionCorreoService.CODIGO_INVALIDO);
	}

	/**
	 * Envía el código de inicio de sesión y lo saca del cuerpo del correo.
	 *
	 * @return código enviado
	 */
	private String enviarCodigoInicioYObtenerlo() {
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);
		servicio.enviarCodigoInicio(usuario, CORREO);
		return codigoDe(cuerpoEnviado());
	}

	/**
	 * Envía el código de bienvenida y lo saca del cuerpo del correo.
	 *
	 * @return código enviado
	 */
	private String enviarYObtenerCodigo() {
		when(correoUtil.enviarCorreo(anyString(), anyString(), anyString())).thenReturn(true);
		servicio.enviarCodigo(usuario, CORREO, "Ana", true);
		return codigoDe(cuerpoEnviado());
	}

	/** Hace que el repositorio encuentre la cuenta de prueba por su correo. */
	private void cuentaExistente() {
		when(usuarioRepo.findByCorreoHash(UsuarioService.hashCorreo(CORREO))).thenReturn(Optional.of(usuario));
	}

	/**
	 * Cuerpo del último correo enviado.
	 *
	 * @return cuerpo del correo
	 */
	private String cuerpoEnviado() {
		ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
		verify(correoUtil).enviarCorreo(eq(CORREO), anyString(), cuerpo.capture());
		return cuerpo.getValue();
	}

	/**
	 * Saca el código de 6 dígitos del cuerpo del correo.
	 *
	 * @param cuerpo cuerpo del correo
	 * @return código
	 */
	private static String codigoDe(String cuerpo) {
		Matcher matcher = CODIGO.matcher(cuerpo);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
