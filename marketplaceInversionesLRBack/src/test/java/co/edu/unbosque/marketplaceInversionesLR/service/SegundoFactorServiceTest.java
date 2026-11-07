package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplaceInversionesLR.dto.SegundoFactorDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import co.edu.unbosque.marketplaceInversionesLR.util.TotpUtil;

/**
 * Pruebas unitarias del segundo factor (TOTP) que pide el inicio de sesión. La
 * base de datos y la auditoría se simulan.
 */
@ExtendWith(MockitoExtension.class)
class SegundoFactorServiceTest {

	/** Id del usuario de prueba. */
	private static final Long ID = 7L;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private AuditoriaService auditoriaService;

	private SegundoFactorService servicio;

	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new SegundoFactorService(usuarioRepo, auditoriaService);
		usuario = new Usuario();
		usuario.setIdUsuario(ID);
		usuario.setCorreo(AESUtil.encrypt("ana@correo.com"));
		usuario.setTotpHabilitado(false);
	}

	@Test
	@DisplayName("Activar genera un secreto cifrado y la URI otpauth, pendiente de confirmar")
	void activarGeneraElSecreto() {
		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));

		Resultado<SegundoFactorDTO> resultado = servicio.activar(ID);

		assertThat(resultado.esExito()).isTrue();
		SegundoFactorDTO dto = resultado.getDato();
		assertThat(dto.getSecreto()).isNotBlank();
		assertThat(dto.getUri()).startsWith("otpauth://totp/MarketplaceInversionesLR:ana@correo.com?secret=")
				.contains(dto.getSecreto());
		assertThat(dto.getCodigo()).isNull();
		// El secreto se guarda cifrado y aún no queda habilitado
		assertThat(usuario.getSecretoTotp()).isNotEqualTo(dto.getSecreto());
		assertThat(AESUtil.decrypt(usuario.getSecretoTotp())).isEqualTo(dto.getSecreto());
		assertThat(SegundoFactorService.estaHabilitado(usuario)).isFalse();
		verify(usuarioRepo).save(usuario);
	}

	@Test
	@DisplayName("No se activa si el usuario no existe o ya lo tiene habilitado")
	void activarCasosInvalidos() {
		when(usuarioRepo.findById(ID)).thenReturn(Optional.empty());
		assertThat(servicio.activar(ID).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);

		usuario.setTotpHabilitado(true);
		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));
		assertThat(servicio.activar(ID).getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
	}

	@Test
	@DisplayName("Confirmar con un código válido habilita el segundo factor")
	void confirmarConCodigoValido() {
		String secreto = conSecreto(false);

		Resultado<Void> resultado = servicio.confirmar(ID, codigoActual(secreto));

		assertThat(resultado.esExito()).isTrue();
		assertThat(SegundoFactorService.estaHabilitado(usuario)).isTrue();
		verify(auditoriaService).registrar(ID, AuditoriaService.SEGUNDO_FACTOR, AuditoriaService.ENTIDAD_USUARIO, ID,
				ResultadoAuditoria.EXITOSO, "Segundo factor habilitado");
	}

	@Test
	@DisplayName("Confirmar con un código inválido o sin activación pendiente se rechaza")
	void confirmarCasosInvalidos() {
		when(usuarioRepo.findById(ID)).thenReturn(Optional.empty());
		assertThat(servicio.confirmar(ID, "123456").getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);

		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));
		assertThat(servicio.confirmar(ID, "123456").getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);

		conSecreto(false);
		assertThat(servicio.confirmar(ID, "abc").getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(SegundoFactorService.estaHabilitado(usuario)).isFalse();
		verify(auditoriaService).registrar(ID, AuditoriaService.SEGUNDO_FACTOR, AuditoriaService.ENTIDAD_USUARIO, ID,
				ResultadoAuditoria.RECHAZADO, "Código inválido al confirmar");
	}

	@Test
	@DisplayName("Desactivar con un código válido quita el secreto")
	void desactivarConCodigoValido() {
		String secreto = conSecreto(true);

		assertThat(servicio.desactivar(ID, codigoActual(secreto)).esExito()).isTrue();

		assertThat(SegundoFactorService.estaHabilitado(usuario)).isFalse();
		assertThat(usuario.getSecretoTotp()).isNull();
		verify(usuarioRepo).save(usuario);
	}

	@Test
	@DisplayName("Desactivar se rechaza si no existe, no está habilitado o el código no sirve")
	void desactivarCasosInvalidos() {
		when(usuarioRepo.findById(ID)).thenReturn(Optional.empty());
		assertThat(servicio.desactivar(ID, "123456").getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);

		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));
		assertThat(servicio.desactivar(ID, "123456").getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);

		conSecreto(true);
		assertThat(servicio.desactivar(ID, "abc").getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(SegundoFactorService.estaHabilitado(usuario)).isTrue();
		verify(usuarioRepo, never()).save(usuario);
		verify(auditoriaService).registrar(eq(ID), eq(AuditoriaService.SEGUNDO_FACTOR),
				eq(AuditoriaService.ENTIDAD_USUARIO), eq(ID), eq(ResultadoAuditoria.RECHAZADO), anyString());
	}

	@Test
	@DisplayName("Verificar compara el código con el secreto de la cuenta")
	void verificar() {
		assertThat(servicio.verificar(usuario, "123456")).isFalse();

		String secreto = TotpUtil.generarSecreto();
		usuario.setSecretoTotp(AESUtil.encrypt(secreto));
		assertThat(servicio.verificar(usuario, codigoActual(secreto))).isTrue();
		assertThat(servicio.verificar(usuario, "abc")).isFalse();
	}

	@Test
	@DisplayName("El segundo factor solo está habilitado si la bandera es verdadera")
	void estaHabilitado() {
		usuario.setTotpHabilitado(null);
		assertThat(SegundoFactorService.estaHabilitado(usuario)).isFalse();
		usuario.setTotpHabilitado(true);
		assertThat(SegundoFactorService.estaHabilitado(usuario)).isTrue();
	}

	/**
	 * Le asigna al usuario un secreto nuevo y hace que el repositorio lo
	 * encuentre.
	 *
	 * @param habilitado si el segundo factor queda habilitado
	 * @return secreto en claro
	 */
	private String conSecreto(boolean habilitado) {
		String secreto = TotpUtil.generarSecreto();
		usuario.setSecretoTotp(AESUtil.encrypt(secreto));
		usuario.setTotpHabilitado(habilitado);
		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));
		return secreto;
	}

	/**
	 * Código TOTP válido en este momento.
	 *
	 * @param secreto secreto en claro
	 * @return código de 6 dígitos
	 */
	private static String codigoActual(String secreto) {
		return TotpUtil.codigo(secreto, Instant.now());
	}
}
