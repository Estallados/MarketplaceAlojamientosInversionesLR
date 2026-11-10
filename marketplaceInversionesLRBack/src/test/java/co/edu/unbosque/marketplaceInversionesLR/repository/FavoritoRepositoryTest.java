package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Favorito;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas del repositorio de favoritos sobre un PostgreSQL temporal en Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class FavoritoRepositoryTest {

	@Autowired
	private FavoritoRepository favoritoRepositorio;

	@Autowired
	private AlojamientoRepository alojamientoRepositorio;

	@Autowired
	private UsuarioRepository usuarioRepositorio;

	@Test
	@DisplayName("Pagina solo los favoritos del usuario indicado")
	void buscaPorUsuario() {
		Long idAnfitrion = usuario("anfitrion@correo.com");
		Long idAlojamiento = alojamiento(idAnfitrion);
		Long idAna = usuario("ana@correo.com");
		Long idLuis = usuario("luis@correo.com");
		favoritoRepositorio.saveAndFlush(favorito(idAna, idAlojamiento));

		Page<Favorito> deAna = favoritoRepositorio.findByIdUsuario(idAna, PageRequest.of(0, 20));

		assertThat(deAna.getContent()).extracting(Favorito::getIdAlojamiento).containsExactly(idAlojamiento);
		assertThat(favoritoRepositorio.findByIdUsuario(idLuis, PageRequest.of(0, 20))).isEmpty();
	}

	@Test
	@DisplayName("Encuentra el favorito por usuario y alojamiento")
	void buscaPorUsuarioYAlojamiento() {
		Long idAnfitrion = usuario("anfitrion@correo.com");
		Long idAlojamiento = alojamiento(idAnfitrion);
		Long idAna = usuario("ana@correo.com");
		Favorito guardado = favoritoRepositorio.saveAndFlush(favorito(idAna, idAlojamiento));

		assertThat(favoritoRepositorio.findByIdUsuarioAndIdAlojamiento(idAna, idAlojamiento))
				.contains(guardado);
		assertThat(favoritoRepositorio.findByIdUsuarioAndIdAlojamiento(idAnfitrion, idAlojamiento)).isEmpty();
	}

	private Favorito favorito(Long idUsuario, Long idAlojamiento) {
		Favorito favorito = new Favorito();
		favorito.setIdUsuario(idUsuario);
		favorito.setIdAlojamiento(idAlojamiento);
		favorito.setFechaAgregado(LocalDateTime.now());
		return favorito;
	}

	private Long alojamiento(Long idAnfitrion) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setIdAnfitrion(idAnfitrion);
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepositorio.saveAndFlush(alojamiento).getIdAlojamiento();
	}

	private Long usuario(String correo) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("no-se-usa");
		usuario.setRol(Role.HUESPED);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepositorio.saveAndFlush(usuario).getIdUsuario();
	}
}
