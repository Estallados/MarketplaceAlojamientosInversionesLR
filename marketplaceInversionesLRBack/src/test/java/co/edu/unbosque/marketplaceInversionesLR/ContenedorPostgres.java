package co.edu.unbosque.marketplaceInversionesLR;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de datos de las pruebas de integración: un PostgreSQL 18 (la misma
 * versión de Neon) que Testcontainers levanta en Docker y borra al terminar.
 * Así las pruebas nunca tocan la base compartida del equipo.
 * <p>
 * Las clases que la usan llevan {@code @Testcontainers(disabledWithoutDocker = true)}:
 * si Docker no está disponible se saltan en lugar de fallar. En GitHub Actions
 * Docker siempre está disponible.
 * </p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContenedorPostgres {

	/**
	 * Contenedor de PostgreSQL. {@link ServiceConnection} configura la conexión
	 * de Spring con sus datos.
	 *
	 * @return contenedor de PostgreSQL
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer("postgres:18-alpine");
	}
}
