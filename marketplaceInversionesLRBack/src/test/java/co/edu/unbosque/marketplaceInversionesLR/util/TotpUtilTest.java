package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de los códigos TOTP del segundo factor (RFC 6238).
 */
class TotpUtilTest {

	/** Secreto del vector de prueba de RFC 6238 ("12345678901234567890" en Base32). */
	private static final String SECRETO_RFC = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	@Test
	@DisplayName("Genera secretos Base32 de 160 bits, sin relleno y distintos cada vez")
	void generaSecretos() {
		String secreto = TotpUtil.generarSecreto();

		assertThat(secreto).hasSize(32).matches("[A-Z2-7]+").doesNotContain("=");
		assertThat(TotpUtil.generarSecreto()).isNotEqualTo(secreto);
	}

	@Test
	@DisplayName("Calcula el código del vector de prueba de RFC 6238")
	void codigoDelVectorDePrueba() {
		// RFC 6238, apéndice B (SHA-1): T = 59 s da 94287082; con 6 dígitos, 287082
		assertThat(TotpUtil.codigo(SECRETO_RFC, Instant.ofEpochSecond(59))).isEqualTo("287082");
		assertThat(TotpUtil.codigo(SECRETO_RFC, Instant.ofEpochSecond(1111111109))).isEqualTo("081804");
	}

	@Test
	@DisplayName("Acepta el código actual y los de un paso antes o después")
	void aceptaLaVentanaDeTiempo() {
		String secreto = TotpUtil.generarSecreto();
		Instant ahora = Instant.now();

		assertThat(TotpUtil.verificar(secreto, TotpUtil.codigo(secreto, ahora))).isTrue();
		assertThat(TotpUtil.verificar(secreto, TotpUtil.codigo(secreto, ahora.minusSeconds(30)))).isTrue();
		assertThat(TotpUtil.verificar(secreto, TotpUtil.codigo(secreto, ahora.plusSeconds(30)))).isTrue();
		assertThat(TotpUtil.verificar(secreto, TotpUtil.codigo(secreto, ahora.minusSeconds(300)))).isFalse();
	}

	@Test
	@DisplayName("Rechaza códigos vacíos, con formato inválido o sin secreto")
	void rechazaEntradasInvalidas() {
		String secreto = TotpUtil.generarSecreto();

		assertThat(TotpUtil.verificar(secreto, null)).isFalse();
		assertThat(TotpUtil.verificar(null, "123456")).isFalse();
		assertThat(TotpUtil.verificar(secreto, "12345")).isFalse();
		assertThat(TotpUtil.verificar(secreto, "abcdef")).isFalse();
		assertThat(TotpUtil.verificar(secreto, "1234567")).isFalse();
	}

	@Test
	@DisplayName("Arma la URI otpauth con el emisor, la cuenta y el secreto")
	void armaLaUri() {
		assertThat(TotpUtil.uri("Emisor", "ana@correo.com", "ABC"))
				.isEqualTo("otpauth://totp/Emisor:ana@correo.com?secret=ABC&issuer=Emisor&digits=6&period=30");
	}

	@Test
	@DisplayName("Es una clase utilitaria que no se instancia")
	void noSeInstancia() throws NoSuchMethodException {
		Constructor<TotpUtil> constructor = TotpUtil.class.getDeclaredConstructor();
		constructor.setAccessible(true);

		assertThatThrownBy(constructor::newInstance).isInstanceOf(InvocationTargetException.class)
				.hasCauseInstanceOf(IllegalStateException.class);
	}
}
