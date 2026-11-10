package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Pruebas de HU-12 Restringir cuentas de usuarios sobre la API completa y un
 * PostgreSQL temporal en Docker: el administrador restringe y libera cuentas,
 * la restricción se ve al consultar el usuario (CA-058) y el usuario
 * restringido no puede hacer nada que requiera una cuenta habilitada (CA-059).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class RestriccionUsuariosTest {

	/** Contraseña de las cuentas de prueba. */
	private static final String CLAVE = "Clave#2026";

	/** Correo del huésped que se restringe. */
	private static final String CORREO_HUESPED = "huesped.restriccion@correo.com";

	/** Motivo de la restricción. */
	private static final String MOTIVO = "Publicó información falsa en sus mensajes";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private AuditoriaService auditoriaService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Administrador. */
	private Usuario admin;

	/** Token del administrador. */
	private String tokenAdmin;

	/** Huésped que se restringe. */
	private Usuario huesped;

	/** Token del huésped, emitido antes de restringirlo. */
	private String tokenHuesped;

	/** Anfitrión con una publicación. */
	private Usuario anfitrion;

	@BeforeEach
	void preparar() {
		limpiar();
		admin = crearUsuario("admin.restriccion@correo.com", Role.ADMIN);
		tokenAdmin = jwtUtil.generateToken(admin);
		huesped = crearUsuario(CORREO_HUESPED, Role.HUESPED);
		tokenHuesped = jwtUtil.generateToken(huesped);
		anfitrion = crearUsuario("anfitrion.restriccion@correo.com", Role.ANFITRION);

		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa del anfitrión");
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.BORRADOR);
		alojamiento.setPrecioNoche(new BigDecimal("150000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		alojamientoRepository.save(alojamiento);
	}

	/**
	 * Borra en orden de llaves foráneas: notificaciones y alojamientos antes que
	 * los usuarios.
	 */
	@AfterEach
	void limpiar() {
		jdbc.update("DELETE FROM notificacion");
		jdbc.update("DELETE FROM historial_estado_alojamiento");
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-058 El administrador restringe con motivo: se registra, se notifica y se ve al consultar")
	void restringeYSeVeAlConsultar_CA058() throws Exception {
		restringir(huesped.getIdUsuario(), "{\"motivo\": \"  " + MOTIVO + "  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idUsuario").value(huesped.getIdUsuario()))
				.andExpect(jsonPath("$.estado").value("RESTRINGIDO"))
				.andExpect(jsonPath("$.motivoRestriccion").value(MOTIVO))
				.andExpect(jsonPath("$.correo").value(CORREO_HUESPED))
				.andExpect(jsonPath("$.contrasenia").doesNotExist())
				.andExpect(jsonPath("$.correoHash").doesNotExist())
				.andExpect(jsonPath("$.secretoTotp").doesNotExist());

		Usuario guardado = usuarioRepository.findById(huesped.getIdUsuario()).orElseThrow();
		assertThat(guardado.getEstado()).isEqualTo(EstadoUsuario.RESTRINGIDO);
		assertThat(guardado.getMotivoRestriccion()).isEqualTo(MOTIVO);

		// Se ve al consultar el usuario por id, por correo y en el listado
		comoAdmin(get("/usuarios/obtener/" + huesped.getIdUsuario()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RESTRINGIDO"))
				.andExpect(jsonPath("$.motivoRestriccion").value(MOTIVO));
		comoAdmin(get("/usuarios/obtenercorreo").param("correo", "  HUESPED.Restriccion@correo.com "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.motivoRestriccion").value(MOTIVO));
		comoAdmin(get("/admin/usuarios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.contenido[1].estado").value("RESTRINGIDO"))
				.andExpect(jsonPath("$.contenido[1].motivoRestriccion").value(MOTIVO));
		comoAdmin(get("/usuarios/listar"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].estado").value(hasItem("RESTRINGIDO")));

		// Se le notifica el motivo y queda en la auditoría
		assertThat(jdbc.queryForObject("SELECT mensaje FROM notificacion WHERE id_usuario = ? AND tipo = ?",
				String.class, huesped.getIdUsuario(), NotificacionService.CUENTA_RESTRINGIDA))
				.isEqualTo(UsuarioService.AVISO_RESTRINGIDA + MOTIVO);
		assertThat(auditoriaService.consultar(AuditoriaService.USUARIO_RESTRINGIDO, admin.getIdUsuario(), null, null,
				0, 10).getContent()).isNotEmpty();
	}

	@Test
	@DisplayName("CA-059 Restringido no puede usar su token, iniciar sesión ni reactivarse; al liberarlo vuelve a entrar")
	void restringidoNoActua_CA059() throws Exception {
		// Antes de restringir, el token sirve
		mockMvc.perform(get("/usuarios/perfil").header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isOk());

		restringir(huesped.getIdUsuario(), "{\"motivo\": \"" + MOTIVO + "\"}").andExpect(status().isOk());

		// El token que ya tenía deja de servir para cualquier acción con sesión
		mockMvc.perform(get("/usuarios/perfil").header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/cotizaciones").header("Authorization", "Bearer " + tokenHuesped)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
		// No puede iniciar sesión ni reactivarse por su cuenta
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("La cuenta está restringida o desactivada."));
		mockMvc.perform(post("/auth/reactivar").contentType(MediaType.APPLICATION_JSON).content(credenciales()))
				.andExpect(status().isForbidden());

		// El administrador la libera (sin cuerpo): vuelve a quedar ACTIVO y sin motivo
		comoAdmin(patch("/admin/usuarios/" + huesped.getIdUsuario() + "/liberar"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andExpect(jsonPath("$.motivoRestriccion").doesNotExist());
		assertThat(usuarioRepository.findById(huesped.getIdUsuario()).orElseThrow().getMotivoRestriccion()).isNull();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE id_usuario = ? AND tipo = ?",
				Integer.class, huesped.getIdUsuario(), NotificacionService.CUENTA_LIBERADA)).isEqualTo(1);

		// Ya puede usar su sesión y sus credenciales (el login sigue con el código por correo)
		mockMvc.perform(get("/usuarios/perfil").header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isOk());
		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(credenciales()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.requiereCodigoCorreo").value(true));
	}

	@Test
	@DisplayName("Restringir y liberar validan el motivo, el rol, la existencia y el estado")
	void validaciones() throws Exception {
		restringir(huesped.getIdUsuario(), "{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(UsuarioService.MOTIVO_INVALIDO));
		restringir(huesped.getIdUsuario(), "{\"motivo\": \"" + "x".repeat(501) + "\"}")
				.andExpect(status().isBadRequest());
		restringir(huesped.getIdUsuario(), "{\"motivo\": \"" + "x".repeat(500) + "\"}").andExpect(status().isOk());
		restringir(admin.getIdUsuario(), "{\"motivo\": \"" + MOTIVO + "\"}").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(UsuarioService.NO_RESTRINGIR_ADMIN));
		restringir(999999L, "{\"motivo\": \"" + MOTIVO + "\"}").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value(UsuarioService.NO_ENCONTRADO));

		comoAdmin(patch("/admin/usuarios/" + anfitrion.getIdUsuario() + "/liberar")
				.contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Revisado\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(UsuarioService.YA_ACTIVA));
		comoAdmin(patch("/admin/usuarios/999999/liberar")).andExpect(status().isNotFound());
		assertThat(usuarioRepository.findById(admin.getIdUsuario()).orElseThrow().getEstado())
				.isEqualTo(EstadoUsuario.ACTIVO);
	}

	@Test
	@DisplayName("CA-131 Solo el administrador gestiona usuarios; un usuario solo se consulta a sí mismo")
	void permisos_CA131() throws Exception {
		String tokenAnfitrion = jwtUtil.generateToken(anfitrion);
		mockMvc.perform(patch("/admin/usuarios/" + huesped.getIdUsuario() + "/restringir")
				.header("Authorization", "Bearer " + tokenAnfitrion).contentType(MediaType.APPLICATION_JSON)
				.content("{\"motivo\": \"" + MOTIVO + "\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/usuarios").header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/usuarios/listar").header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/usuarios/obtenercorreo").param("correo", CORREO_HUESPED)
				.header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/usuarios")).andExpect(status().isUnauthorized());

		// El propio usuario sí se consulta; a otro no
		mockMvc.perform(get("/usuarios/obtener/" + huesped.getIdUsuario())
				.header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correo").value(CORREO_HUESPED));
		mockMvc.perform(get("/usuarios/obtener/" + anfitrion.getIdUsuario())
				.header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(status().isForbidden());
		assertThat(usuarioRepository.findById(huesped.getIdUsuario()).orElseThrow().getEstado())
				.isEqualTo(EstadoUsuario.ACTIVO);
	}

	@Test
	@DisplayName("El detalle de usuario muestra sus reservas y sus alojamientos en cualquier estado")
	void detalleDeUsuario() throws Exception {
		comoAdmin(get("/admin/usuarios/" + huesped.getIdUsuario() + "/reservas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
		comoAdmin(get("/admin/usuarios/" + anfitrion.getIdUsuario() + "/alojamientos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].estado").value("BORRADOR"));
		comoAdmin(get("/admin/usuarios/999999/reservas")).andExpect(status().isNotFound());
		comoAdmin(get("/admin/usuarios/999999/alojamientos")).andExpect(status().isNotFound());
		comoAdmin(get("/usuarios/obtener/999999")).andExpect(status().isNotFound());
		comoAdmin(get("/usuarios/obtenercorreo")).andExpect(status().isBadRequest());
		comoAdmin(get("/usuarios/obtenercorreo").param("correo", "nadie@correo.com")).andExpect(status().isNotFound());
	}

	/**
	 * Restringe como administrador.
	 *
	 * @param idUsuario usuario
	 * @param cuerpo    JSON con el motivo
	 * @return la respuesta
	 */
	private ResultActions restringir(Long idUsuario, String cuerpo) throws Exception {
		return comoAdmin(patch("/admin/usuarios/" + idUsuario + "/restringir").contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo));
	}

	/**
	 * Ejecuta la petición con el token del administrador.
	 *
	 * @param peticion petición
	 * @return la respuesta
	 */
	private ResultActions comoAdmin(
			org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion) throws Exception {
		return mockMvc.perform(peticion.header("Authorization", "Bearer " + tokenAdmin));
	}

	/** JSON con el correo y la contraseña del huésped. */
	private static String credenciales() {
		return "{\"correo\": \"" + CORREO_HUESPED + "\", \"contrasenia\": \"" + CLAVE + "\"}";
	}

	/**
	 * Guarda un usuario activo y verificado con el rol indicado.
	 *
	 * @param correo correo de la cuenta
	 * @param rol    rol de la cuenta
	 * @return usuario guardado
	 */
	private Usuario crearUsuario(String correo, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre("Usuario de prueba");
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia(passwordEncoder.encode(CLAVE));
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
