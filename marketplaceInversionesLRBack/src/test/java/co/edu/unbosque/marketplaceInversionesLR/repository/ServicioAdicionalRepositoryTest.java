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
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.ServicioAdicional;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas del repositorio de servicios adicionales sobre un PostgreSQL
 * temporal en Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ServicioAdicionalRepositoryTest {

	@Autowired
	private ServicioAdicionalRepository repositorio;

	@Autowired
	private AlojamientoRepository alojamientoRepositorio;

	@Autowired
	private UsuarioRepository usuarioRepositorio;

	@Test
	@DisplayName("Lista los servicios del alojamiento en el orden en que se crearon")
	void listaEnOrden() {
		Long idAlojamiento = alojamiento();
		Long idOtro = alojamiento();
		repositorio.save(servicio(idAlojamiento, "Desayuno"));
		repositorio.save(servicio(idAlojamiento, "Traslado"));
		repositorio.saveAndFlush(servicio(idOtro, "Parqueadero"));

		assertThat(repositorio.findByIdAlojamientoOrderByIdServicio(idAlojamiento))
				.extracting(ServicioAdicional::getNombre).containsExactly("Desayuno", "Traslado");
	}

	@Test
	@DisplayName("Solo encuentra el servicio dentro de su propio alojamiento")
	void buscaDentroDelAlojamiento() {
		Long idAlojamiento = alojamiento();
		Long idOtro = alojamiento();
		Long idServicio = repositorio.saveAndFlush(servicio(idAlojamiento, "Desayuno")).getIdServicio();

		ServicioAdicional encontrado = repositorio.findByIdServicioAndIdAlojamiento(idServicio, idAlojamiento)
				.orElseThrow();

		assertThat(encontrado.getCosto()).isEqualByComparingTo("20000");
		assertThat(encontrado.getIdAlojamiento()).isEqualTo(idAlojamiento);
		assertThat(repositorio.findByIdServicioAndIdAlojamiento(idServicio, idOtro)).isEmpty();
	}

	/**
	 * Servicio de prueba.
	 *
	 * @param idAlojamiento alojamiento que lo ofrece
	 * @param nombre        nombre del servicio
	 * @return servicio sin guardar
	 */
	private static ServicioAdicional servicio(Long idAlojamiento, String nombre) {
		ServicioAdicional servicio = new ServicioAdicional();
		servicio.setIdAlojamiento(idAlojamiento);
		servicio.setNombre(nombre);
		servicio.setCosto(new BigDecimal("20000"));
		return servicio;
	}

	/**
	 * Guarda un alojamiento con un anfitrión propio (llaves foráneas).
	 *
	 * @return id del alojamiento
	 */
	private Long alojamiento() {
		String correo = "anfitrion" + System.nanoTime() + "@correo.com";
		Usuario usuario = new Usuario();
		usuario.setNombre("Ana");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("hash");
		usuario.setRol(Role.ANFITRION);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);

		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa");
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);
		alojamiento.setIdAnfitrion(usuarioRepositorio.save(usuario).getIdUsuario());
		alojamiento.setFechaCreacion(LocalDateTime.now());
		alojamiento.setFechaActualizacion(alojamiento.getFechaCreacion());
		return alojamientoRepositorio.save(alojamiento).getIdAlojamiento();
	}
}
