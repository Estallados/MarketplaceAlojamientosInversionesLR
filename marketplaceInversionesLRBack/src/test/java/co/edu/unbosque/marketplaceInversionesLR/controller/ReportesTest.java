package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.RegistroExportacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.AuditoriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.ReporteService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.ExportadorUtil;

/**
 * Pruebas de HU-05 Exportar reportes de gestión en PDF o Excel sobre la API
 * completa y un PostgreSQL temporal en Docker (CA-027).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class ReportesTest {

	/** Ruta base de los reportes. */
	private static final String RUTA = "/admin/reportes";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private RegistroExportacionRepository exportacionRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Administrador que exporta. */
	private Usuario admin;

	/** Token del administrador. */
	private String tokenAdmin;

	/** Anfitrión con alojamientos. */
	private Usuario anfitrion;

	@BeforeEach
	void preparar() {
		limpiar();
		admin = crearUsuario("admin.reportes@correo.com", "Admin Reportes", Role.ADMIN);
		tokenAdmin = jwtUtil.generateToken(admin);
		anfitrion = crearUsuario("anfitrion.reportes@correo.com", "Beto Anfitrión", Role.ANFITRION);
		crearAlojamiento("Casa en el lago", "Guatapé");
		crearAlojamiento("Apartamento centro", "Bogotá");
	}

	/** Borra en orden de llaves foráneas: exportaciones antes que usuarios. */
	@AfterEach
	void limpiar() {
		exportacionRepository.deleteAll();
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-027 Exportar en PDF genera el archivo y registra solicitante y fecha")
	void exportaPdf_CA027() throws Exception {
		LocalDateTime antes = LocalDateTime.now().minusSeconds(1);

		byte[] pdf = mockMvc.perform(get(RUTA + "/alojamientos").param("formato", "pdf")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/pdf"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-alojamientos.pdf\""))
				.andReturn().getResponse().getContentAsByteArray();

		assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
		Map<String, Object> registro = jdbc.queryForMap(
				"SELECT tipo_reporte, formato, cantidad_registros, id_usuario, fecha FROM registro_exportacion");
		assertThat(registro).containsEntry("tipo_reporte", "alojamientos").containsEntry("formato", "pdf")
				.containsEntry("cantidad_registros", 2).containsEntry("id_usuario", admin.getIdUsuario());
		assertThat(((java.sql.Timestamp) registro.get("fecha")).toLocalDateTime()).isAfter(antes);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_auditoria WHERE accion = ? AND id_usuario = ?",
				Long.class, AuditoriaService.REPORTE_EXPORTADO, admin.getIdUsuario())).isPositive();
	}

	@Test
	@DisplayName("CA-027 Exportar en Excel trae los datos registrados del sistema")
	void exportaExcelConLosDatos_CA027() throws Exception {
		byte[] excel = mockMvc.perform(get(RUTA + "/usuarios").param("formato", "excel")
				.header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"reporte-usuarios.xlsx\""))
				.andReturn().getResponse().getContentAsByteArray();

		try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
			Sheet hoja = libro.getSheetAt(0);
			int base = ExportadorUtil.FILA_ENCABEZADO_EXCEL;
			assertThat(hoja.getRow(base + 0).getCell(2).getStringCellValue()).isEqualTo("Correo");
			assertThat(hoja.getLastRowNum()).isEqualTo(base + 2);
			assertThat(hoja.getRow(base + 1).getCell(2).getStringCellValue() + hoja.getRow(base + 2).getCell(2).getStringCellValue())
					.contains("admin.reportes@correo.com", "anfitrion.reportes@correo.com");
		}
	}

	@Test
	@DisplayName("CA-027 El historial muestra quién exportó, qué y cuándo")
	void historial_CA027() throws Exception {
		mockMvc.perform(get(RUTA + "/demanda").param("formato", "excel").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk());

		mockMvc.perform(get(RUTA + "/historial").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.contenido[0].tipoReporte").value("demanda"))
				.andExpect(jsonPath("$.contenido[0].formato").value("excel"))
				.andExpect(jsonPath("$.contenido[0].idUsuario").value(admin.getIdUsuario()))
				.andExpect(jsonPath("$.contenido[0].nombreUsuario").value("Admin Reportes"))
				.andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty());
	}

	@Test
	@DisplayName("CA-027 Un tipo o formato inválido responde 400 y solo el administrador exporta")
	void validaciones_CA027() throws Exception {
		mockMvc.perform(get(RUTA + "/clientes").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(ReporteService.REPORTE_INVALIDO));
		mockMvc.perform(get(RUTA + "/usuarios").param("formato", "word").header("Authorization", "Bearer " + tokenAdmin))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get(RUTA + "/usuarios").header("Authorization", "Bearer " + jwtUtil.generateToken(anfitrion)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(RUTA + "/usuarios")).andExpect(status().isUnauthorized());
		assertThat(exportacionRepository.count()).isZero();
	}

	private void crearAlojamiento(String titulo, String ciudad) {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo(titulo);
		alojamiento.setCiudad(ciudad);
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		alojamientoRepository.save(alojamiento);
	}

	private Usuario crearUsuario(String correo, String nombre, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
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
