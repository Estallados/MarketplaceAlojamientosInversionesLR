package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.FavoritoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.FavoritoService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-29 Gestionar alojamientos favoritos sobre la API completa y un
 * PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class FavoritosTest {

	/** Ruta base de favoritos. */
	private static final String RUTA = "/favoritos";

	/** Id de un alojamiento que no existe. */
	private static final long INEXISTENTE = 999999L;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FavoritoRepository favoritoRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Anfitrión dueño de los alojamientos. */
	private Usuario anfitrion;

	/** Token de la huésped Ana. */
	private String tokenAna;

	/** Token del huésped Luis. */
	private String tokenLuis;

	/** Alojamiento publicado. */
	private Long idAlojamiento;

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = crearUsuario("anfitrion@correo.com", Role.ANFITRION);
		tokenAna = jwtUtil.generateToken(crearUsuario("ana@correo.com", Role.HUESPED));
		tokenLuis = jwtUtil.generateToken(crearUsuario("luis@correo.com", Role.HUESPED));
		idAlojamiento = crearAlojamiento(EstadoAlojamiento.ACTIVA);
	}

	/** Borra en orden de llaves foráneas: los favoritos primero. */
	@AfterEach
	void limpiar() {
		favoritoRepository.deleteAll();
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-170 El huésped sin favoritos recibe la lista vacía")
	void listaVacia_CA170() throws Exception {
		listar(tokenAna).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0))
				.andExpect(jsonPath("$.contenido").isEmpty());
	}

	@Test
	@DisplayName("CA-170 El huésped ve solo sus favoritos, con los datos del alojamiento")
	void soloLosPropios_CA170() throws Exception {
		Long otro = crearAlojamiento(EstadoAlojamiento.ACTIVA);
		agregar(idAlojamiento, tokenAna).andExpect(status().isCreated());
		agregar(otro, tokenLuis).andExpect(status().isCreated());

		listar(tokenAna).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.contenido[0].alojamiento.idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.contenido[0].alojamiento.titulo").value("Casa en el lago"));
	}

	@Test
	@DisplayName("CA-171 Al eliminar un favorito deja de aparecer en la lista")
	void eliminaDeLaLista_CA171() throws Exception {
		agregar(idAlojamiento, tokenAna).andExpect(status().isCreated());

		eliminar(idAlojamiento, tokenAna).andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value(FavoritoController.QUITADO));

		listar(tokenAna).andExpect(jsonPath("$.totalElementos").value(0));
		eliminar(idAlojamiento, tokenAna).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(FavoritoService.NO_ES_FAVORITO));
	}

	@Test
	@DisplayName("CA-171 Un huésped no puede eliminar el favorito de otro")
	void noEliminaElDeOtro_CA171() throws Exception {
		agregar(idAlojamiento, tokenAna).andExpect(status().isCreated());

		eliminar(idAlojamiento, tokenLuis).andExpect(status().isNotFound());

		listar(tokenAna).andExpect(jsonPath("$.totalElementos").value(1));
	}

	@Test
	@DisplayName("CA-173 Agregar lo registra y, si ya estaba, conserva un único favorito")
	void agregaSinDuplicar_CA173() throws Exception {
		Integer primero = JsonPath.read(agregar(idAlojamiento, tokenAna).andExpect(status().isCreated())
				.andExpect(jsonPath("$.idAlojamiento").value(idAlojamiento))
				.andExpect(jsonPath("$.fechaAgregado").isNotEmpty())
				.andReturn().getResponse().getContentAsString(), "$.idFavorito");

		agregar(idAlojamiento, tokenAna).andExpect(status().isOk())
				.andExpect(jsonPath("$.idFavorito").value(primero));

		listar(tokenAna).andExpect(jsonPath("$.totalElementos").value(1));
		assertThat(favoritoRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("CA-173 No se agrega un alojamiento que no existe o que no está publicado")
	void alojamientoNoDisponible_CA173() throws Exception {
		Long borrador = crearAlojamiento(EstadoAlojamiento.BORRADOR);

		agregar(INEXISTENTE, tokenAna).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(FavoritoService.NO_EXISTE));
		agregar(borrador, tokenAna).andExpect(status().isNotFound());

		assertThat(favoritoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Sin token las rutas de favoritos responden 401")
	void sinToken() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		mockMvc.perform(post(RUTA + "/" + idAlojamiento)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete(RUTA + "/" + idAlojamiento)).andExpect(status().isUnauthorized());
	}

	private ResultActions listar(String token) throws Exception {
		return mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token));
	}

	private ResultActions agregar(Long id, String token) throws Exception {
		return mockMvc.perform(post(RUTA + "/" + id).header("Authorization", "Bearer " + token));
	}

	private ResultActions eliminar(Long id, String token) throws Exception {
		return mockMvc.perform(delete(RUTA + "/" + id).header("Authorization", "Bearer " + token));
	}

	private Long crearAlojamiento(EstadoAlojamiento estado) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(estado);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	private Usuario crearUsuario(String correo, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("no-se-usa");
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
