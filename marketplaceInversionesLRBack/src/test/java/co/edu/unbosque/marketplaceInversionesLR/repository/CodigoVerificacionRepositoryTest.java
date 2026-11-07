package co.edu.unbosque.marketplaceInversionesLR.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.marketplaceInversionesLR.entity.CodigoVerificacion;

/**
 * Pruebas del almacén en memoria de los códigos de verificación.
 */
class CodigoVerificacionRepositoryTest {

	/** Repositorio bajo prueba. */
	private final CodigoVerificacionRepository repositorio = new CodigoVerificacionRepository();

	@Test
	@DisplayName("Guarda un código por usuario y el nuevo reemplaza al anterior")
	void guardaUnCodigoPorUsuario() {
		repositorio.save(codigo(1L, "a", 10, false));
		repositorio.save(codigo(1L, "b", 10, false));

		assertThat(repositorio.count()).isEqualTo(1);
		assertThat(repositorio.findVigente(1L)).get().extracting(CodigoVerificacion::getCodigoHash).isEqualTo("b");
	}

	@Test
	@DisplayName("Un código usado ya no está vigente")
	void codigoUsadoNoVigente() {
		repositorio.save(codigo(2L, "a", 10, true));

		assertThat(repositorio.findVigente(2L)).isEmpty();
		assertThat(repositorio.findVigente(3L)).isEmpty();
	}

	@Test
	@DisplayName("La limpieza borra los códigos usados y los vencidos")
	void limpiezaBorraUsadosYVencidos() {
		repositorio.save(codigo(1L, "vigente", 10, false));
		repositorio.save(codigo(2L, "usado", 10, true));
		repositorio.save(codigo(3L, "vencido", -1, false));

		repositorio.limpiar();

		assertThat(repositorio.count()).isEqualTo(1);
		assertThat(repositorio.findVigente(1L)).isPresent();

		repositorio.deleteAll();
		assertThat(repositorio.count()).isZero();
	}

	/**
	 * Crea un código de prueba.
	 *
	 * @param idUsuario usuario dueño
	 * @param hash      hash del código
	 * @param minutos   minutos de vigencia desde ahora (negativo: ya venció)
	 * @param usado     si ya se usó
	 * @return código
	 */
	private static CodigoVerificacion codigo(Long idUsuario, String hash, int minutos, boolean usado) {
		CodigoVerificacion codigo = new CodigoVerificacion();
		codigo.setIdUsuario(idUsuario);
		codigo.setCodigoHash(hash);
		codigo.setFechaCreacion(LocalDateTime.now());
		codigo.setFechaExpiracion(LocalDateTime.now().plusMinutes(minutos));
		codigo.setUsado(usado);
		return codigo;
	}
}
