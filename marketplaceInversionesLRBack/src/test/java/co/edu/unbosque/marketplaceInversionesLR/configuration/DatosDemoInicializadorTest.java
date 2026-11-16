package co.edu.unbosque.marketplaceInversionesLR.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.entity.ConfiguracionTarifa;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConfiguracionTarifaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas unitarias de los datos base de la demostración (SCRUM-206): solo
 * agregan lo que falta y no actúan sin {@code DEMO_POBLAR=true}. Los
 * repositorios y el codificador de contraseñas se simulan; el correo se cifra
 * con la clave AES de pruebas que define el {@code pom.xml}.
 */
class DatosDemoInicializadorTest {

	/** Repositorio de usuarios simulado. */
	private UsuarioRepository usuarioRepo;

	/** Repositorio de políticas de cancelación simulado. */
	private PoliticaCancelacionRepository politicaRepo;

	/** Repositorio de configuraciones de tarifa simulado. */
	private ConfiguracionTarifaRepository tarifaRepo;

	/** Codificador de contraseñas simulado: devuelve siempre el mismo hash. */
	private PasswordEncoder passwordEncoder;

	/**
	 * Crea los simulacros antes de cada prueba.
	 */
	@BeforeEach
	void preparar() {
		usuarioRepo = mock(UsuarioRepository.class);
		politicaRepo = mock(PoliticaCancelacionRepository.class);
		tarifaRepo = mock(ConfiguracionTarifaRepository.class);
		passwordEncoder = mock(PasswordEncoder.class);
		when(passwordEncoder.encode(anyString())).thenReturn("hash-bcrypt");
	}

	/**
	 * Crea el inicializador con los simulacros.
	 *
	 * @param habilitado valor de {@code DEMO_POBLAR}
	 * @return el inicializador
	 */
	private DatosDemoInicializador inicializador(boolean habilitado) {
		return new DatosDemoInicializador(habilitado, "Demo#2026", usuarioRepo, politicaRepo, tarifaRepo,
				passwordEncoder);
	}

	/**
	 * Sin {@code DEMO_POBLAR=true} no guarda nada.
	 */
	@Test
	@DisplayName("Sin DEMO_POBLAR no se escribe nada en la base de datos")
	void sinDemoPoblarNoTocaLaBase() {
		inicializador(false).alArrancar();

		verify(usuarioRepo, never()).save(any());
		verify(politicaRepo, never()).save(any());
		verify(tarifaRepo, never()).save(any());
	}

	/**
	 * En una base vacía crea las tres políticas, todos los usuarios (al menos
	 * uno por rol, con el correo cifrado y verificado) y la configuración de
	 * tarifas a nombre del administrador.
	 */
	@Test
	@DisplayName("En una base vacía crea las políticas, un usuario por rol y la configuración de tarifas")
	void enUnaBaseVaciaCreaPoliticasUsuariosYTarifa() {
		when(politicaRepo.findByNombre(anyString())).thenReturn(Optional.empty());
		when(usuarioRepo.existsByCorreoHash(anyString())).thenReturn(false);
		when(tarifaRepo.count()).thenReturn(0L);
		Usuario admin = new Usuario();
		admin.setIdUsuario(1L);
		when(usuarioRepo.findByCorreoHash(AESUtil.hash("andrescastillo@gmail.com"))).thenReturn(Optional.of(admin));

		inicializador(true).alArrancar();

		ArgumentCaptor<PoliticaCancelacion> politicas = ArgumentCaptor.forClass(PoliticaCancelacion.class);
		verify(politicaRepo, times(3)).save(politicas.capture());
		assertThat(politicas.getAllValues()).extracting(PoliticaCancelacion::getNombre)
				.containsExactly("FLEXIBLE", "MODERADA", "ESTRICTA");

		ArgumentCaptor<Usuario> usuarios = ArgumentCaptor.forClass(Usuario.class);
		verify(usuarioRepo, times(DatosDemoInicializador.USUARIOS.size())).save(usuarios.capture());
		assertThat(usuarios.getAllValues()).extracting(Usuario::getRol).contains(Role.ADMIN, Role.ANFITRION,
				Role.HUESPED);
		Usuario laura = usuarios.getAllValues().get(1);
		assertThat(AESUtil.decrypt(laura.getCorreo())).isEqualTo("lauragomez@gmail.com");
		assertThat(laura.getCorreoHash()).isEqualTo(AESUtil.hash("lauragomez@gmail.com"));
		assertThat(laura.getContrasenia()).isEqualTo("hash-bcrypt");
		assertThat(laura.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(laura.esCorreoVerificado()).isTrue();

		ArgumentCaptor<ConfiguracionTarifa> tarifa = ArgumentCaptor.forClass(ConfiguracionTarifa.class);
		verify(tarifaRepo).save(tarifa.capture());
		assertThat(tarifa.getValue().getIdUsuario()).isEqualTo(1L);
		assertThat(tarifa.getValue().getPorcentajeLimpieza()).isEqualByComparingTo("5");
		assertThat(tarifa.getValue().getPorcentajeServicio()).isEqualByComparingTo("10");
	}

	/**
	 * Si las políticas, los usuarios y la configuración de tarifas ya existen, no
	 * guarda nada: no duplica ni modifica datos.
	 */
	@Test
	@DisplayName("Si los datos ya existen no los duplica ni los modifica")
	void noDuplicaNiModificaLoQueYaExiste() {
		when(politicaRepo.findByNombre(anyString())).thenReturn(Optional.of(new PoliticaCancelacion()));
		when(usuarioRepo.existsByCorreoHash(anyString())).thenReturn(true);
		when(tarifaRepo.count()).thenReturn(1L);

		inicializador(true).alArrancar();

		verify(politicaRepo, never()).save(any());
		verify(usuarioRepo, never()).save(any());
		verify(tarifaRepo, never()).save(any());
	}
}
