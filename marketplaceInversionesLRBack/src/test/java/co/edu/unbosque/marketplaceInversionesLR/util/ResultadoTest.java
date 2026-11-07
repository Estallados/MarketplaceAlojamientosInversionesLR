package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del resultado de las operaciones de los servicios.
 */
class ResultadoTest {

	@Test
	@DisplayName("Un resultado exitoso trae el dato y no trae mensaje")
	void exito() {
		Resultado<String> resultado = Resultado.exito("dato");

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getCodigo()).isEqualTo(Resultado.EXITOSO);
		assertThat(resultado.getDato()).isEqualTo("dato");
		assertThat(resultado.getMensaje()).isNull();
	}

	@Test
	@DisplayName("Un error trae su código y su mensaje, y opcionalmente un dato")
	void error() {
		Resultado<String> sinDato = Resultado.error(Resultado.NO_ENCONTRADO, "No existe.");
		Resultado<String> conDato = Resultado.error(Resultado.ESTADO_INVALIDO, "Estado inválido.", "detalle");

		assertThat(sinDato.esExito()).isFalse();
		assertThat(sinDato.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(sinDato.getMensaje()).isEqualTo("No existe.");
		assertThat(sinDato.getDato()).isNull();
		assertThat(conDato.getCodigo()).isEqualTo(Resultado.ESTADO_INVALIDO);
		assertThat(conDato.getDato()).isEqualTo("detalle");
	}
}
