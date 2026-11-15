package co.edu.unbosque.marketplaceInversionesLR;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal del backend del Marketplace de alojamientos de Inversiones
 * LR: arranca Spring Boot con toda la configuración del proyecto.
 */
@SpringBootApplication
public class MarketplaceInversionesLrApplication {

	/**
	 * Crea la clase principal; Spring Boot la instancia al arrancar.
	 */
	public MarketplaceInversionesLrApplication() {
		// Spring Boot crea la instancia; no requiere inicialización.
	}

	/**
	 * Punto de entrada: inicia la aplicación.
	 *
	 * @param args argumentos de la línea de comandos
	 */
	public static void main(String[] args) {
		SpringApplication.run(MarketplaceInversionesLrApplication.class, args);
	}

}

