package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

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
import co.edu.unbosque.marketplaceInversionesLR.entity.HistorialEstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de los repositorios de alojamientos, políticas de cancelación e
 * historial de estados sobre un PostgreSQL temporal en Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class AlojamientoRepositoryTest {

	@Autowired
	private AlojamientoRepository alojamientoRepositorio;

	@Autowired
	private PoliticaCancelacionRepository politicaRepositorio;

	@Autowired
	private HistorialEstadoAlojamientoRepository historialRepositorio;

	@Autowired
	private UsuarioRepository usuarioRepositorio;

	@Test
	@DisplayName("Guarda el alojamiento con todos sus datos y sus amenidades")
	void guardaElAlojamiento() {
		Long idPolitica = politicaRepositorio.save(politica("Flexible")).getIdPolitica();
		Alojamiento alojamiento = alojamiento(anfitrion());
		alojamiento.setIdPolitica(idPolitica);

		Long id = alojamientoRepositorio.saveAndFlush(alojamiento).getIdAlojamiento();
		Alojamiento guardado = alojamientoRepositorio.findById(id).orElseThrow();

		assertThat(guardado.getTitulo()).isEqualTo("Casa en el lago");
		assertThat(guardado.getEstado()).isEqualTo(EstadoAlojamiento.BORRADOR);
		assertThat(guardado.getHoraEntrada()).isEqualTo(LocalTime.of(15, 0));
		assertThat(guardado.getPrecioNoche()).isEqualByComparingTo("250000");
		assertThat(guardado.getIdPolitica()).isEqualTo(idPolitica);
		assertThat(guardado.getAmenidades()).containsExactlyInAnyOrder("wifi", "piscina");
	}

	@Test
	@DisplayName("Lista las políticas y las busca por nombre")
	void politicas() {
		politicaRepositorio.save(politica("Flexible"));
		politicaRepositorio.save(politica("Estricta"));

		assertThat(politicaRepositorio.findByNombre("Estricta")).isPresent();
		assertThat(politicaRepositorio.findByNombre("Otra")).isEmpty();
		assertThat(politicaRepositorio.findAll()).extracting(PoliticaCancelacion::getNombre).contains("Flexible",
				"Estricta");
	}

	@Test
	@DisplayName("Guarda el historial de estados y lo lista del más antiguo al más reciente")
	void historial() {
		Long idAnfitrion = anfitrion();
		Long id = alojamientoRepositorio.save(alojamiento(idAnfitrion)).getIdAlojamiento();
		LocalDateTime ahora = LocalDateTime.now();
		historialRepositorio.save(cambio(id, EstadoAlojamiento.BORRADOR, EstadoAlojamiento.PENDIENTE_REVISION,
				idAnfitrion, ahora));
		historialRepositorio.save(cambio(id, null, EstadoAlojamiento.BORRADOR, idAnfitrion, ahora.minusHours(1)));

		List<HistorialEstadoAlojamiento> historial = historialRepositorio
				.findByIdAlojamientoOrderByFechaAscIdHistorialAsc(id);

		assertThat(historial).extracting(HistorialEstadoAlojamiento::getEstadoNuevo)
				.containsExactly(EstadoAlojamiento.BORRADOR, EstadoAlojamiento.PENDIENTE_REVISION);
		assertThat(historial.get(0).getEstadoAnterior()).isNull();
		assertThat(historial.get(0).getIdUsuario()).isEqualTo(idAnfitrion);
		assertThat(historial.get(0).getIdHistorial()).isNotNull();
		assertThat(historial.get(0).getMotivo()).isNull();
		assertThat(historial.get(0).getFecha()).isNotNull();
	}

	/**
	 * Guarda un anfitrión (llave foránea).
	 *
	 * @return id del anfitrión
	 */
	private Long anfitrion() {
		Usuario usuario = new Usuario();
		usuario.setNombre("Ana");
		usuario.setCorreo(AESUtil.encrypt("ana@correo.com"));
		usuario.setCorreoHash(UsuarioService.hashCorreo("ana@correo.com"));
		usuario.setContrasenia("hash");
		usuario.setRol(Role.ANFITRION);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepositorio.save(usuario).getIdUsuario();
	}

	/**
	 * Alojamiento en BORRADOR del anfitrión.
	 *
	 * @param idAnfitrion dueño
	 * @return alojamiento sin guardar
	 */
	private static Alojamiento alojamiento(Long idAnfitrion) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setDescripcion("Vista al lago");
		alojamiento.setUbicacion("Calle 1");
		alojamiento.setCiudad("Guatapé");
		alojamiento.setLatitud(6.2);
		alojamiento.setLongitud(-75.1);
		alojamiento.setTipo("Casa");
		alojamiento.setCapacidad(4);
		alojamiento.setHabitaciones(2);
		alojamiento.setCamas(3);
		alojamiento.setBanos(2);
		alojamiento.setHoraEntrada(LocalTime.of(15, 0));
		alojamiento.setHoraSalida(LocalTime.of(11, 0));
		alojamiento.setPrecioNoche(new BigDecimal("250000"));
		alojamiento.setPorcentajeLimpieza(new BigDecimal("5"));
		alojamiento.setPorcentajeServicio(new BigDecimal("10"));
		alojamiento.setReglas("No fumar");
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);
		alojamiento.setIdAnfitrion(idAnfitrion);
		alojamiento.setFechaCreacion(LocalDateTime.now());
		alojamiento.setFechaActualizacion(alojamiento.getFechaCreacion());
		alojamiento.setAmenidades(Set.of("wifi", "piscina"));
		return alojamiento;
	}

	/**
	 * Política de cancelación de prueba.
	 *
	 * @param nombre nombre de la política
	 * @return política sin guardar
	 */
	private static PoliticaCancelacion politica(String nombre) {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre(nombre);
		politica.setCondiciones("Condiciones de " + nombre);
		politica.setDiasReembolsoTotal(7);
		politica.setDiasReembolsoParcial(2);
		politica.setPorcentajeParcial(50);
		return politica;
	}

	/**
	 * Registro de historial de prueba.
	 *
	 * @return registro sin guardar
	 */
	private static HistorialEstadoAlojamiento cambio(Long idAlojamiento, EstadoAlojamiento anterior,
			EstadoAlojamiento nuevo, Long idUsuario, LocalDateTime fecha) {
		HistorialEstadoAlojamiento historial = new HistorialEstadoAlojamiento();
		historial.setIdAlojamiento(idAlojamiento);
		historial.setEstadoAnterior(anterior);
		historial.setEstadoNuevo(nuevo);
		historial.setIdUsuario(idUsuario);
		historial.setFecha(fecha);
		return historial;
	}
}
