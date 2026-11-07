package co.edu.unbosque.marketplaceInversionesLR;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class MarketplaceInversionesLrApplicationTests {

	@Test
	void contextLoads() {
	}

}
