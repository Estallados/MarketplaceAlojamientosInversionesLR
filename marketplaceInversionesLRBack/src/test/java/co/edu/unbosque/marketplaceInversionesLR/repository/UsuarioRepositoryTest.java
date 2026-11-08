package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas del repositorio de usuarios sobre un PostgreSQL temporal en Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class UsuarioRepositoryTest {

	@Autowired
	private UsuarioRepository repositorio;

	@Test
	@DisplayName("Guarda el usuario y lo encuentra por el hash de su correo")
	void buscaPorHashDelCorreo() {
		Usuario guardado = repositorio.save(usuario("ana@correo.com"));

		assertThat(guardado.getIdUsuario()).isNotNull();
		assertThat(repositorio.findByCorreoHash(UsuarioService.hashCorreo("ana@correo.com")))
				.get().extracting(Usuario::getIdUsuario).isEqualTo(guardado.getIdUsuario());
		assertThat(repositorio.findByCorreoHash(UsuarioService.hashCorreo("nadie@correo.com"))).isEmpty();
	}

	@Test
	@DisplayName("Indica si ya existe un usuario con ese correo")
	void existePorHashDelCorreo() {
		repositorio.save(usuario("luis@correo.com"));

		assertThat(repositorio.existsByCorreoHash(UsuarioService.hashCorreo("luis@correo.com"))).isTrue();
		assertThat(repositorio.existsByCorreoHash(UsuarioService.hashCorreo("otro@correo.com"))).isFalse();
	}

	@Test
	@DisplayName("No permite dos usuarios con el mismo correo")
	void correoUnico() {
		repositorio.saveAndFlush(usuario("sara@correo.com"));

		assertThatThrownBy(() -> repositorio.saveAndFlush(usuario("sara@correo.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * Usuario válido con el correo indicado.
	 *
	 * @param correo correo de la cuenta
	 * @return usuario sin guardar
	 */
	private static Usuario usuario(String correo) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("hash");
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuario;
	}
}
