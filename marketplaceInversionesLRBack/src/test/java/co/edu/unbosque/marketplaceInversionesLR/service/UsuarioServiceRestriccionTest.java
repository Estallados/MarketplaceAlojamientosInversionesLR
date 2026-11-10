package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-12 Restringir cuentas de usuarios: restringir,
 * liberar y consultar usuarios desde el administrador. La base de datos se
 * simula.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceRestriccionTest {

	/** Usuario de prueba. */
	private static final Long ID = 7L;

	/** Administrador que actúa. */
	private static final Long ADMIN = 1L;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private AuditoriaService auditoriaService;

	@Mock
	private VerificacionCorreoService verificacionCorreoService;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private NotificacionService notificacionService;

	private UsuarioService servicio;

	/** Huésped activo de prueba. */
	private Usuario usuario;

	@BeforeEach
	void preparar() {
		servicio = new UsuarioService(usuarioRepo, auditoriaService, verificacionCorreoService, passwordEncoder,
				new ModelMapper(), notificacionService);
		usuario = new Usuario();
		usuario.setIdUsuario(ID);
		usuario.setNombre("Ana");
		usuario.setCorreo(AESUtil.encrypt("ana@correo.com"));
		usuario.setCorreoHash("hash-secreto");
		usuario.setContrasenia("$2a$10$hash");
		usuario.setSecretoTotp("secreto-cifrado");
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
	}

	@Test
	@DisplayName("CA-058 Restringir guarda el estado y el motivo, los muestra, notifica y audita")
	void restringe_CA058() {
		guardar();

		Resultado<UsuarioDTO> resultado = servicio.restringir(ID, "  Publicó contenido falso  ", ADMIN);

		assertThat(resultado.esExito()).isTrue();
		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		assertThat(usuario.getMotivoRestriccion()).isEqualTo("Publicó contenido falso");
		UsuarioDTO dto = resultado.getDato();
		assertThat(dto.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		assertThat(dto.getMotivoRestriccion()).isEqualTo("Publicó contenido falso");
		// Nunca se exponen los datos sensibles
		assertThat(dto.getCorreo()).isEqualTo("ana@correo.com");
		assertThat(dto.getContrasenia()).isNull();
		assertThat(dto.getCorreoHash()).isNull();
		assertThat(dto.getSecretoTotp()).isNull();
		verify(notificacionService).enviar(ID, NotificacionService.CUENTA_RESTRINGIDA,
				UsuarioService.AVISO_RESTRINGIDA + "Publicó contenido falso");
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.USUARIO_RESTRINGIDO,
				AuditoriaService.ENTIDAD_USUARIO, ID, ResultadoAuditoria.EXITOSO, "Publicó contenido falso");
	}

	@Test
	@DisplayName("CA-058 El motivo es obligatorio y de máximo 500 caracteres")
	void motivoInvalido_CA058() {
		for (String motivo : new String[] { null, "", "   ", "x".repeat(501) }) {
			Resultado<UsuarioDTO> resultado = servicio.restringir(ID, motivo, ADMIN);
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
			assertThat(resultado.getMensaje()).isEqualTo(UsuarioService.MOTIVO_INVALIDO);
		}
		guardar();
		assertThat(servicio.restringir(ID, "x".repeat(500), ADMIN).esExito()).isTrue();
		verify(usuarioRepo).save(any());
	}

	@Test
	@DisplayName("A un administrador no se le puede restringir y el intento queda auditado")
	void noRestringeAdmin() {
		usuario.setRol(Role.ADMIN);
		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));

		Resultado<UsuarioDTO> resultado = servicio.restringir(ID, "Motivo", ADMIN);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(resultado.getMensaje()).isEqualTo(UsuarioService.NO_RESTRINGIR_ADMIN);
		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		verify(usuarioRepo, never()).save(any());
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.USUARIO_RESTRINGIDO,
				AuditoriaService.ENTIDAD_USUARIO, ID, ResultadoAuditoria.RECHAZADO, UsuarioService.NO_RESTRINGIR_ADMIN);
	}

	@Test
	@DisplayName("Restringir o liberar a un usuario que no existe responde no encontrado")
	void noExiste() {
		when(usuarioRepo.findById(99L)).thenReturn(Optional.empty());

		assertThat(servicio.restringir(99L, "Motivo", ADMIN).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.liberar(99L, null, ADMIN).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.restringir(null, "Motivo", ADMIN).getMensaje()).isEqualTo(UsuarioService.NO_ENCONTRADO);
		verify(notificacionService, never()).enviar(any(), anyString(), anyString());
	}

	@Test
	@DisplayName("Restringir de nuevo actualiza el motivo; una cuenta inactiva también se puede restringir")
	void restringeDeNuevo() {
		usuario.setEstado(EstadoUsuario.INACTIVO);
		guardar();

		assertThat(servicio.restringir(ID, "Primero", ADMIN).esExito()).isTrue();
		assertThat(servicio.restringir(ID, "Segundo", ADMIN).esExito()).isTrue();

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		assertThat(usuario.getMotivoRestriccion()).isEqualTo("Segundo");
	}

	@Test
	@DisplayName("Liberar deja la cuenta ACTIVO sin motivo, notifica y audita el estado anterior")
	void libera() {
		usuario.setEstado(EstadoUsuario.RESTRINGIDO);
		usuario.setMotivoRestriccion("Publicó contenido falso");
		guardar();

		Resultado<UsuarioDTO> resultado = servicio.liberar(ID, " Revisado ", ADMIN);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		assertThat(resultado.getDato().getMotivoRestriccion()).isNull();
		assertThat(usuario.getMotivoRestriccion()).isNull();
		verify(notificacionService).enviar(ID, NotificacionService.CUENTA_LIBERADA, UsuarioService.AVISO_LIBERADA);
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.USUARIO_LIBERADO, AuditoriaService.ENTIDAD_USUARIO,
				ID, ResultadoAuditoria.EXITOSO, "Estaba RESTRINGIDO. Motivo: Revisado");
	}

	@Test
	@DisplayName("Liberar sin motivo también funciona y habilita una cuenta inactiva")
	void liberaSinMotivo() {
		usuario.setEstado(EstadoUsuario.INACTIVO);
		guardar();

		assertThat(servicio.liberar(ID, null, ADMIN).esExito()).isTrue();

		assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.USUARIO_LIBERADO, AuditoriaService.ENTIDAD_USUARIO,
				ID, ResultadoAuditoria.EXITOSO, "Estaba INACTIVO");
	}

	@Test
	@DisplayName("Liberar una cuenta activa responde estado inválido; un motivo muy largo, datos inválidos")
	void liberarInvalido() {
		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));

		Resultado<UsuarioDTO> activa = servicio.liberar(ID, null, ADMIN);
		Resultado<UsuarioDTO> largo = servicio.liberar(ID, "x".repeat(501), ADMIN);

		assertThat(activa.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(activa.getMensaje()).isEqualTo(UsuarioService.YA_ACTIVA);
		assertThat(largo.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(largo.getMensaje()).isEqualTo(UsuarioService.MOTIVO_LARGO);
		verify(usuarioRepo, never()).save(any());
		verify(notificacionService, never()).enviar(any(), anyString(), anyString());
	}

	@Test
	@DisplayName("CA-058 Al consultar un usuario restringido se ven su estado y el motivo")
	void consulta_CA058() {
		usuario.setEstado(EstadoUsuario.RESTRINGIDO);
		usuario.setMotivoRestriccion("Spam");
		Pageable pagina = PageRequest.of(0, 20, Sort.by("idUsuario"));
		when(usuarioRepo.findById(ID)).thenReturn(Optional.of(usuario));
		when(usuarioRepo.findAll(pagina)).thenReturn(new PageImpl<>(List.of(usuario), pagina, 1));
		when(usuarioRepo.findAll(Sort.by("idUsuario"))).thenReturn(List.of(usuario));
		when(usuarioRepo.existsById(ID)).thenReturn(true);

		UsuarioDTO porId = servicio.obtenerPorId(ID);
		PaginaDTO<UsuarioDTO> paginados = servicio.listarPaginado(pagina);
		List<UsuarioDTO> todos = servicio.listarTodos();

		assertThat(porId.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		assertThat(porId.getMotivoRestriccion()).isEqualTo("Spam");
		assertThat(paginados.getTotalElementos()).isEqualTo(1);
		assertThat(paginados.getContenido().get(0).getMotivoRestriccion()).isEqualTo("Spam");
		assertThat(todos).singleElement().satisfies(u -> assertThat(u.getContrasenia()).isNull());
		assertThat(servicio.existe(ID)).isTrue();
		assertThat(servicio.existe(null)).isFalse();
		assertThat(servicio.obtenerPorId(null)).isNull();
	}

	/** El repositorio guarda y devuelve el mismo usuario. */
	private void guardar() {
		when(usuarioRepo.findById(eq(ID))).thenReturn(Optional.of(usuario));
		when(usuarioRepo.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
	}
}
