package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del cifrado de datos personales. La clave de prueba llega en
 * AES_SECRET_KEY desde el maven-surefire-plugin.
 */
class AESUtilTest {

	@Test
	@DisplayName("Cifrar y descifrar devuelve el texto original")
	void cifrarYDescifrar() {
		String cifrado = AESUtil.encrypt("ana@correo.com");

		assertThat(cifrado).isNotEqualTo("ana@correo.com");
		assertThat(AESUtil.decrypt(cifrado)).isEqualTo("ana@correo.com");
	}

	@Test
	@DisplayName("El mismo texto cifrado dos veces da resultados distintos (IV aleatorio)")
	void cifradoUsaIvAleatorio() {
		assertThat(AESUtil.encrypt("ana@correo.com")).isNotEqualTo(AESUtil.encrypt("ana@correo.com"));
	}

	@Test
	@DisplayName("El hash es siempre el mismo para el mismo texto y distinto para otro")
	void hashEsDeterminista() {
		assertThat(AESUtil.hash("ana@correo.com")).isEqualTo(AESUtil.hash("ana@correo.com"))
				.isNotEqualTo(AESUtil.hash("luis@correo.com"));
	}

	@Test
	@DisplayName("La clave derivada tiene 32 bytes y depende del propósito")
	void claveDerivada() {
		assertThat(AESUtil.derivarClave("jwt")).hasSize(32).isEqualTo(AESUtil.derivarClave("jwt"))
				.isNotEqualTo(AESUtil.derivarClave("otro"));
	}

	@Test
	@DisplayName("Un texto que no es un cifrado válido no se puede descifrar")
	void textoInvalidoNoDescifra() {
		assertThatThrownBy(() -> AESUtil.decrypt("no-es-base64!")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AESUtil.decrypt("AAAA")).isInstanceOf(IllegalArgumentException.class);
		byte[] bytes = Base64.getDecoder().decode(AESUtil.encrypt("ana@correo.com"));
		bytes[bytes.length - 1] ^= 1;
		String alterado = Base64.getEncoder().encodeToString(bytes);
		assertThatThrownBy(() -> AESUtil.decrypt(alterado)).isInstanceOf(IllegalArgumentException.class);
	}
}
