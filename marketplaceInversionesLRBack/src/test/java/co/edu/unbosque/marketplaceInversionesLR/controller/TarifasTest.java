package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConfiguracionTarifaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-24 Configurar porcentajes de tarifa de limpieza y de servicio
 * sobre la API completa y un PostgreSQL temporal en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class TarifasTest {

	/** Ruta de la configuración global de tarifas. */
	private static final String TARIFAS = "/admin/tarifas";

	/** Mensaje cuando los porcentajes no son válidos. */
	private static final String PORCENTAJES_INVALIDOS = "Los porcentajes de limpieza y servicio son obligatorios y "
			+ "deben estar entre 0 y 100.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private ConfiguracionTarifaRepository configuracionRepository;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/**
	 * Borra las configuraciones antes que los usuarios (llave foránea). También
	 * al terminar, para que las demás pruebas puedan borrar sus usuarios.
	 */
	@BeforeEach
	@AfterEach
	void limpiar() {
		configuracionRepository.deleteAll();
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-084 Sin configuración aplica los porcentajes por defecto y, tras modificarlos, aplica el valor vigente")
	void aplicaElValorVigenteOElPorDefecto_CA084() throws Exception {
		Usuario admin = crear("admin@correo.com", Role.ADMIN);
		String token = jwtUtil.generateToken(admin);

		// Sin configuración guardada: porcentajes por defecto documentados
		consultar(token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.porDefecto").value(true))
				.andExpect(jsonPath("$.porcentajeLimpieza").value(5))
				.andExpect(jsonPath("$.porcentajeServicio").value(10));

		actualizar(token, "{\"porcentajeLimpieza\": 7.5, \"porcentajeServicio\": 12}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.porDefecto").value(false))
				.andExpect(jsonPath("$.porcentajeLimpieza").value(7.5))
				.andExpect(jsonPath("$.porcentajeServicio").value(12))
				.andExpect(jsonPath("$.idUsuario").value(admin.getIdUsuario()))
				.andExpect(jsonPath("$.idConfiguracion").isNumber())
				.andExpect(jsonPath("$.fecha").isNotEmpty());

		// Un cambio recién hecho reemplaza al anterior y queda como vigente
		actualizar(token, "{\"porcentajeLimpieza\": 4, \"porcentajeServicio\": 9}").andExpect(status().isOk());
		consultar(token)
				.andExpect(jsonPath("$.porDefecto").value(false))
				.andExpect(jsonPath("$.porcentajeLimpieza").value(4))
				.andExpect(jsonPath("$.porcentajeServicio").value(9));

		// Se conserva el historial de cambios
		assertThat(configuracionRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("Rechaza porcentajes faltantes o fuera de 0 a 100 sin cambiar la configuración")
	void rechazaPorcentajesInvalidos() throws Exception {
		String token = jwtUtil.generateToken(crear("admin@correo.com", Role.ADMIN));

		actualizar(token, "{\"porcentajeLimpieza\": 5}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(PORCENTAJES_INVALIDOS));
		actualizar(token, "{\"porcentajeLimpieza\": -1, \"porcentajeServicio\": 10}")
				.andExpect(status().isBadRequest());
		actualizar(token, "{\"porcentajeLimpieza\": 5, \"porcentajeServicio\": 101}")
				.andExpect(status().isBadRequest());

		assertThat(configuracionRepository.count()).isZero();
	}

	@Test
	@DisplayName("El anfitrión también puede consultar y cambiar los porcentajes, y el cambio aplica como vigente")
	void anfitrionConfiguraLosPorcentajes() throws Exception {
		Usuario anfitrion = crear("anfitrion@correo.com", Role.ANFITRION);
		String token = jwtUtil.generateToken(anfitrion);

		consultar(token).andExpect(status().isOk()).andExpect(jsonPath("$.porDefecto").value(true));
		actualizar(token, "{\"porcentajeLimpieza\": 6, \"porcentajeServicio\": 11}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idUsuario").value(anfitrion.getIdUsuario()));
		consultar(token)
				.andExpect(jsonPath("$.porcentajeLimpieza").value(6))
				.andExpect(jsonPath("$.porcentajeServicio").value(11));
	}

	@Test
	@DisplayName("Sin sesión o como huésped no se pueden consultar ni cambiar los porcentajes")
	void huespedYSinSesionNoAcceden() throws Exception {
		String huesped = jwtUtil.generateToken(crear("huesped@correo.com", Role.HUESPED));
		String cuerpo = "{\"porcentajeLimpieza\": 5, \"porcentajeServicio\": 10}";

		mockMvc.perform(get(TARIFAS)).andExpect(status().isUnauthorized());
		consultar(huesped).andExpect(status().isForbidden());
		actualizar(huesped, cuerpo).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("No tienes permisos para esta operación"));

		assertThat(configuracionRepository.count()).isZero();
	}

	@Test
	@DisplayName("La documentación de Swagger incluye la configuración de tarifas")
	void documentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/admin/tarifas'].get").exists())
				.andExpect(jsonPath("$.paths['/admin/tarifas'].put.responses['400']").exists());
	}

	/**
	 * Consulta los porcentajes vigentes.
	 *
	 * @param token JWT del usuario
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions consultar(String token) throws Exception {
		return mockMvc.perform(get(TARIFAS).header("Authorization", "Bearer " + token));
	}

	/**
	 * Cambia los porcentajes globales.
	 *
	 * @param token  JWT del usuario
	 * @param cuerpo JSON con los porcentajes
	 * @return resultado de la petición
	 * @throws Exception si falla la petición
	 */
	private ResultActions actualizar(String token, String cuerpo) throws Exception {
		return mockMvc.perform(put(TARIFAS).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	/**
	 * Guarda un usuario activo y verificado con el rol indicado.
	 *
	 * @param correo correo de la cuenta
	 * @param rol    rol de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crear(String correo, Role rol) {
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
