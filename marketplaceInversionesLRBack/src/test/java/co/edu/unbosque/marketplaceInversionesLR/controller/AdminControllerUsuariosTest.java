package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import co.edu.unbosque.marketplaceInversionesLR.dto.AlojamientoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MotivoDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.ReservaDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.UsuarioDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.PublicacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReservaService;
import co.edu.unbosque.marketplaceInversionesLR.service.TarifaService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de las rutas de gestión de usuarios de
 * {@link AdminController} (HU-12): cada resultado del servicio se convierte en
 * la respuesta HTTP del contrato.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerUsuariosTest {

	/** Administrador autenticado. */
	private static final Long ADMIN = 1L;

	@Mock
	private TarifaService tarifaService;

	@Mock
	private PublicacionService publicacionService;

	@Mock
	private UsuarioService usuarioService;

	@Mock
	private ReservaService reservaService;

	private AdminController controlador;

	/** Sesión del administrador. */
	private Authentication sesion;

	@BeforeEach
	void preparar() {
		controlador = new AdminController(tarifaService, publicacionService, usuarioService, reservaService);
		Usuario admin = new Usuario();
		admin.setIdUsuario(ADMIN);
		sesion = new UsernamePasswordAuthenticationToken(admin, null, List.of());
	}

	@Test
	@DisplayName("CA-058 Restringir responde 200, 400, 403 o 404 según el servicio")
	void restringir_CA058() {
		MotivoDTO datos = new MotivoDTO();
		datos.setMotivo("Spam");
		UsuarioDTO restringido = new UsuarioDTO();
		when(usuarioService.restringir(7L, "Spam", ADMIN)).thenReturn(Resultado.exito(restringido),
				Resultado.error(Resultado.DATOS_INVALIDOS, UsuarioService.MOTIVO_INVALIDO),
				Resultado.error(Resultado.SIN_PERMISO, UsuarioService.NO_RESTRINGIR_ADMIN),
				Resultado.error(Resultado.NO_ENCONTRADO, UsuarioService.NO_ENCONTRADO));

		ResponseEntity<Object> ok = controlador.restringir(7L, datos, sesion);
		assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(ok.getBody()).isSameAs(restringido);
		assertThat(controlador.restringir(7L, datos, sesion).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		ResponseEntity<Object> admin = controlador.restringir(7L, datos, sesion);
		assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(admin.getBody()).isEqualTo(Map.of("message", UsuarioService.NO_RESTRINGIR_ADMIN));
		assertThat(controlador.restringir(7L, datos, sesion).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("Liberar acepta el cuerpo vacío y responde 200 o 409 si ya estaba activa")
	void liberar() {
		when(usuarioService.liberar(7L, null, ADMIN)).thenReturn(Resultado.exito(new UsuarioDTO()),
				Resultado.error(Resultado.ESTADO_INVALIDO, UsuarioService.YA_ACTIVA));

		assertThat(controlador.liberar(7L, null, sesion).getStatusCode()).isEqualTo(HttpStatus.OK);
		ResponseEntity<Object> activa = controlador.liberar(7L, null, sesion);
		assertThat(activa.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(activa.getBody()).isEqualTo(Map.of("message", UsuarioService.YA_ACTIVA));
	}

	@Test
	@DisplayName("El listado de usuarios se pide ordenado por id y con el tamaño limitado")
	void listarUsuarios() {
		ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
		PaginaDTO<UsuarioDTO> resultado = new PaginaDTO<>();
		when(usuarioService.listarPaginado(pagina.capture())).thenReturn(resultado);

		assertThat(controlador.usuarios(-1, 1000).getBody()).isSameAs(resultado);

		assertThat(pagina.getValue().getPageNumber()).isZero();
		assertThat(pagina.getValue().getPageSize()).isEqualTo(AdminController.TAMANIO_MAXIMO);
		assertThat(pagina.getValue().getSort().getOrderFor("idUsuario")).isNotNull();
	}

	@Test
	@DisplayName("El detalle de usuario lista sus reservas y alojamientos, o 404 si no existe")
	void detalleDeUsuario() {
		PaginaDTO<ReservaDTO> reservas = new PaginaDTO<>();
		PaginaDTO<AlojamientoDTO> alojamientos = new PaginaDTO<>();
		when(usuarioService.existe(7L)).thenReturn(true);
		when(usuarioService.existe(99L)).thenReturn(false);
		when(reservaService.listarDeHuesped(eq(7L), any(Pageable.class))).thenReturn(reservas);
		when(publicacionService.listarDeAnfitrion(eq(7L), any(Pageable.class))).thenReturn(alojamientos);

		assertThat(controlador.reservasDeUsuario(7L, 0, 20).getBody()).isSameAs(reservas);
		assertThat(controlador.alojamientosDeUsuario(7L, 0, 20).getBody()).isSameAs(alojamientos);
		ResponseEntity<Object> sinUsuario = controlador.reservasDeUsuario(99L, 0, 20);
		assertThat(sinUsuario.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(sinUsuario.getBody()).isEqualTo(Map.of("message", UsuarioService.NO_ENCONTRADO));
		assertThat(controlador.alojamientosDeUsuario(99L, 0, 20).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		verify(reservaService, never()).listarDeHuesped(eq(99L), any(Pageable.class));
	}
}
