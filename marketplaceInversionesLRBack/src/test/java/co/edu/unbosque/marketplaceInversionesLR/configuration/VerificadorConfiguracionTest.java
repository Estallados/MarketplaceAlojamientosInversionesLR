package co.edu.unbosque.marketplaceInversionesLR.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pruebas de la verificación de la configuración al arrancar: la falta del
 * correo nunca debe impedir el arranque.
 */
class VerificadorConfiguracionTest {

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "${MAIL_PASSWORD}", "clave-de-aplicacion" })
	void verificaSinFallar(String contrasenia) {
		MockEnvironment entorno = new MockEnvironment();
		if (contrasenia != null) {
			entorno.setProperty("spring.mail.password", contrasenia);
		}

		assertThatCode(() -> new VerificadorConfiguracion(entorno).verificar()).doesNotThrowAnyException();
	}
}
