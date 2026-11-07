package co.edu.unbosque.marketplaceInversionesLR.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pruebas del JSON de {@link UsuarioDTO}: la contraseña y los datos sensibles
 * nunca salen en las respuestas.
 */
class UsuarioDTOTest {

	/** Mismo motor JSON que usa la API. */
	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	@DisplayName("Al responder no salen la contraseña, el hash del correo ni el secreto del segundo factor")
	void noExponeDatosSensibles() {
		UsuarioDTO dto = new UsuarioDTO();
		dto.setIdUsuario(1L);
		dto.setNombre("Ana");
		dto.setCorreo("ana@correo.com");
		dto.setRol(Role.HUESPED);
		dto.setEstado(EstadoUsuario.ACTIVO);
		dto.setContrasenia("Clave#2026");
		dto.setCorreoHash("hash");
		dto.setSecretoTotp("secreto");
		dto.setTotpHabilitado(false);
		dto.setCorreoVerificado(true);

		String texto = json.writeValueAsString(dto);

		assertThat(texto).contains("\"nombre\":\"Ana\"").contains("\"correo\":\"ana@correo.com\"")
				.contains("\"rol\":\"HUESPED\"").doesNotContain("contrasenia").doesNotContain("Clave#2026")
				.doesNotContain("correoHash").doesNotContain("secretoTotp").doesNotContain("secreto\"");
	}

	@Test
	@DisplayName("Al registrarse sí se recibe la contraseña, pero nunca el hash ni el secreto")
	void recibeLaContrasenia() {
		UsuarioDTO dto = json.readValue("{\"nombre\":\"Ana\",\"correo\":\"ana@correo.com\",\"contrasenia\":\"Clave#2026\","
				+ "\"telefono\":\"3001234567\",\"rol\":\"ANFITRION\",\"correoHash\":\"x\",\"secretoTotp\":\"y\"}",
				UsuarioDTO.class);

		assertThat(dto.getContrasenia()).isEqualTo("Clave#2026");
		assertThat(dto.getTelefono()).isEqualTo("3001234567");
		assertThat(dto.getRol()).isEqualTo(Role.ANFITRION);
		assertThat(dto.getCorreoHash()).isNull();
		assertThat(dto.getSecretoTotp()).isNull();
	}

	@Test
	@DisplayName("toString no muestra la contraseña")
	void toStringSinContrasenia() {
		UsuarioDTO dto = new UsuarioDTO();
		dto.setIdUsuario(1L);
		dto.setCorreo("ana@correo.com");
		dto.setContrasenia("Clave#2026");

		assertThat(dto.toString()).contains("ana@correo.com").doesNotContain("Clave#2026");
	}
}
