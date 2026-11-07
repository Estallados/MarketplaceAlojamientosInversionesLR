package co.edu.unbosque.marketplaceInversionesLR.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pruebas de las reglas de contraseña (RNF-005) y del formato del correo.
 */
class ExceptionChekerTest {

	@ParameterizedTest(name = "\"{0}\" es válida")
	@ValueSource(strings = { "Clave#2026", "Aa1!aaaa" })
	@DisplayName("CA-135 Una contraseña que cumple todas las reglas es válida")
	void contraseniaValida_CA135(String contrasenia) {
		assertThat(ExceptionCheker.contraseniaValida(contrasenia)).isTrue();
		assertThatCode(() -> ExceptionCheker.checkerPasword(contrasenia)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("CA-135 Cada regla que falla lanza su propia excepción")
	void cadaReglaTieneSuExcepcion_CA135() {
		assertThatThrownBy(() -> ExceptionCheker.checkerPasword("Ab1!")).isInstanceOf(CharacterException.class);
		assertThatThrownBy(() -> ExceptionCheker.checkerPasword("clave#2026")).isInstanceOf(CapitalException.class);
		assertThatThrownBy(() -> ExceptionCheker.checkerPasword("CLAVE#2026")).isInstanceOf(SmallException.class);
		assertThatThrownBy(() -> ExceptionCheker.checkerPasword("Clave#Abcd")).isInstanceOf(NumberException.class);
		assertThatThrownBy(() -> ExceptionCheker.checkerPasword("Clave2026x")).isInstanceOf(SymbolException.class);
	}

	@Test
	@DisplayName("CA-135 Más de 64 caracteres o más de 72 bytes no es válida")
	void limitesDeLargo_CA135() {
		assertThat(ExceptionCheker.contraseniaValida("Aa1!" + "a".repeat(60))).isTrue();
		assertThat(ExceptionCheker.contraseniaValida("Aa1!" + "a".repeat(61))).isFalse();
		assertThat(ExceptionCheker.contraseniaValida("Ñ1!" + "ñ".repeat(35))).isFalse();
		assertThat(ExceptionCheker.contraseniaValida(null)).isFalse();
	}

	@Test
	@DisplayName("El correo debe tener usuario, arroba, dominio y extensión")
	void formatoDeCorreo() {
		assertThatCode(() -> ExceptionCheker.checkerMail("ana.mora@correo.com")).doesNotThrowAnyException();
		assertThatThrownBy(() -> ExceptionCheker.checkerMail("ana-sin-arroba")).isInstanceOf(MailException.class);
		assertThatThrownBy(() -> ExceptionCheker.checkerMail("ana@correo")).isInstanceOf(MailException.class);
		assertThatThrownBy(() -> ExceptionCheker.checkerMail(null)).isInstanceOf(MailException.class);
	}

	@Test
	@DisplayName("Las excepciones explican qué le falta a la contraseña")
	void mensajesDeLasExcepciones() {
		assertThat(new CharacterException().getMessage()).contains("8 y 64");
		assertThat(new NumberException().getMessage()).contains("número");
	}
}
