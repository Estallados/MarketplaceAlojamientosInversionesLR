package co.edu.unbosque.marketplaceInversionesLR.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de correos de texto plano con Spring Mail.
 */
@Service
public class CorreoUtil {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(CorreoUtil.class);

	/** Servicio de Spring para enviar correos. */
	private final JavaMailSender mailSender;

	/** Cuenta que envía los correos; Gmail exige que coincida con la autenticada. */
	private final String remitente;

	/**
	 * Crea la utilidad.
	 *
	 * @param mailSender servicio de envío de correos
	 * @param remitente  cuenta remitente ({@code spring.mail.username})
	 */
	public CorreoUtil(JavaMailSender mailSender, @Value("${spring.mail.username:}") String remitente) {
		this.mailSender = mailSender;
		this.remitente = remitente;
	}

	/**
	 * Envía un correo. Si falla, lo registra en el log y devuelve {@code false};
	 * nunca lanza excepción, para que un problema de correo no impida, por
	 * ejemplo, crear la cuenta.
	 *
	 * @param to      destinatario
	 * @param subject asunto
	 * @param body    cuerpo en texto plano
	 * @return {@code true} si el correo se envió
	 */
	public boolean enviarCorreo(String to, String subject, String body) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			if (remitente != null && !remitente.isBlank()) {
				message.setFrom(remitente);
			}
			message.setTo(to);
			message.setSubject(subject);
			message.setText(body);
			mailSender.send(message);
			return true;
		} catch (MailAuthenticationException e) {
			LOG.error("El servidor de correo rechazó el usuario o la contraseña. Revisa la variable de entorno "
					+ "MAIL_PASSWORD (debe ser una contraseña de aplicación de Gmail): {}", e.getMessage());
			return false;
		} catch (MailException e) {
			LOG.error("Error enviando correo: {}", e.getMessage());
			return false;
		}
	}
}
