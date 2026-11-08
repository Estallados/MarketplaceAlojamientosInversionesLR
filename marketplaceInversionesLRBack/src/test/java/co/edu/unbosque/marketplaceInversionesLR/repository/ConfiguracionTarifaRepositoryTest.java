package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.ConfiguracionTarifa;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas del repositorio de la configuración de tarifas sobre un PostgreSQL
 * temporal en Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ConfiguracionTarifaRepositoryTest {

	@Autowired
	private ConfiguracionTarifaRepository repositorio;

	@Autowired
	private UsuarioRepository usuarioRepositorio;

	/** Administrador que hace los cambios (llave foránea). */
	private Long idAdmin;

	@BeforeEach
	void preparar() {
		Usuario admin = new Usuario();
		admin.setNombre("Admin");
		admin.setCorreo(AESUtil.encrypt("admin@correo.com"));
		admin.setCorreoHash(UsuarioService.hashCorreo("admin@correo.com"));
		admin.setContrasenia("hash");
		admin.setRol(Role.ADMIN);
		admin.setEstado(EstadoUsuario.ACTIVO);
		admin.setFechaRegistro(LocalDateTime.now());
		admin.setCorreoVerificado(true);
		admin.setTotpHabilitado(false);
		idAdmin = usuarioRepositorio.save(admin).getIdUsuario();
	}

	@Test
	@DisplayName("Sin configuraciones guardadas no hay vigente")
	void sinConfiguracion() {
		assertThat(repositorio.findTopByOrderByFechaDescIdConfiguracionDesc()).isEmpty();
	}

	@Test
	@DisplayName("La vigente es la de fecha más reciente, aunque se haya guardado antes")
	void vigenteEsLaMasReciente() {
		LocalDateTime ahora = LocalDateTime.now();
		repositorio.save(configuracion("5", "10", ahora.minusDays(1)));
		repositorio.save(configuracion("8", "15", ahora));
		repositorio.save(configuracion("3", "6", ahora.minusDays(2)));

		ConfiguracionTarifa vigente = repositorio.findTopByOrderByFechaDescIdConfiguracionDesc().orElseThrow();

		assertThat(vigente.getPorcentajeLimpieza()).isEqualByComparingTo("8");
		assertThat(vigente.getPorcentajeServicio()).isEqualByComparingTo("15");
		assertThat(vigente.getIdUsuario()).isEqualTo(idAdmin);
		assertThat(repositorio.count()).isEqualTo(3);
	}

	@Test
	@DisplayName("Con la misma fecha, la vigente es la de id mayor (la última guardada)")
	void empateDeFechaGanaElIdMayor() {
		LocalDateTime fecha = LocalDateTime.of(2026, 11, 7, 12, 0);
		repositorio.save(configuracion("5", "10", fecha));
		ConfiguracionTarifa ultima = repositorio.save(configuracion("6", "11", fecha));

		assertThat(repositorio.findTopByOrderByFechaDescIdConfiguracionDesc()).get()
				.extracting(ConfiguracionTarifa::getIdConfiguracion).isEqualTo(ultima.getIdConfiguracion());
	}

	/**
	 * Configuración de prueba del administrador.
	 *
	 * @return configuración sin guardar
	 */
	private ConfiguracionTarifa configuracion(String limpieza, String servicio, LocalDateTime fecha) {
		ConfiguracionTarifa configuracion = new ConfiguracionTarifa();
		configuracion.setPorcentajeLimpieza(new BigDecimal(limpieza));
		configuracion.setPorcentajeServicio(new BigDecimal(servicio));
		configuracion.setIdUsuario(idAdmin);
		configuracion.setFecha(fecha);
		return configuracion;
	}
}
