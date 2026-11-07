package co.edu.unbosque.marketplaceInversionesLR.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import jakarta.annotation.PostConstruct;

/**
 * Revisa al arrancar las variables de entorno que necesita la aplicación.
 * Sin {@code AES_SECRET_KEY} no arranca; sin {@code MAIL_PASSWORD} arranca, pero
 * avisa que no se enviarán correos (los códigos quedan en el log).
 */
@Component
public class VerificadorConfiguracion {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(VerificadorConfiguracion.class);

	/** Propiedades de la aplicación. */
	private final Environment environment;

	/**
	 * Crea el verificador.
	 *
	 * @param environment propiedades de la aplicación
	 */
	public VerificadorConfiguracion(Environment environment) {
		this.environment = environment;
	}

	/**
	 * Hace las revisiones al iniciar.
	 *
	 * @throws IllegalStateException si falta AES_SECRET_KEY o no es válida
	 */
	@PostConstruct
	public void verificar() {
		// Sin AES_SECRET_KEY no se puede registrar ni iniciar sesión: mejor no arrancar
		AESUtil.hash("verificacion");
		if (!correoConfigurado()) {
			LOG.warn("MAIL_PASSWORD no está definida. La app funciona, pero no se enviarán correos: "
					+ "los códigos de verificación quedan en el log.");
		}
	}

	/**
	 * Indica si la contraseña del correo está configurada.
	 *
	 * @return {@code true} si {@code spring.mail.password} tiene valor
	 */
	private boolean correoConfigurado() {
		try {
			String contrasenia = environment.getProperty("spring.mail.password");
			return contrasenia != null && !contrasenia.isBlank() && !contrasenia.startsWith("${");
		} catch (IllegalArgumentException e) {
			return false;
		}
	}
}
