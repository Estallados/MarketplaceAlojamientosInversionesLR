package co.edu.unbosque.marketplaceInversionesLR.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Pruebas del envío de correos con el servidor de correo simulado.
 */
class CorreoUtilTest {

	/** Servidor de correo simulado. */
	private final JavaMailSender mailSender = mock(JavaMailSender.class);

	@Test
	@DisplayName("Envía el correo con remitente, destinatario, asunto y texto")
	void enviaElCorreo() {
		CorreoUtil correoUtil = new CorreoUtil(mailSender, "pruebas@marketplace.local");

		assertThat(correoUtil.enviarCorreo("ana@correo.com", "Asunto", "Texto")).isTrue();

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		SimpleMailMessage mensaje = captor.getValue();
		assertThat(mensaje.getFrom()).isEqualTo("pruebas@marketplace.local");
		assertThat(mensaje.getTo()).containsExactly("ana@correo.com");
		assertThat(mensaje.getSubject()).isEqualTo("Asunto");
		assertThat(mensaje.getText()).isEqualTo("Texto");
	}

	@Test
	@DisplayName("Sin remitente configurado el correo sale sin el campo From")
	void sinRemitente() {
		CorreoUtil correoUtil = new CorreoUtil(mailSender, " ");

		assertThat(correoUtil.enviarCorreo("ana@correo.com", "Asunto", "Texto")).isTrue();

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());
		assertThat(captor.getValue().getFrom()).isNull();
	}

	@Test
	@DisplayName("Si el servidor rechaza la contraseña devuelve false")
	void credencialesInvalidas() {
		doThrow(new MailAuthenticationException("rechazado")).when(mailSender).send(any(SimpleMailMessage.class));

		assertThat(new CorreoUtil(mailSender, null).enviarCorreo("ana@correo.com", "Asunto", "Texto")).isFalse();
	}

	@Test
	@DisplayName("Si el envío falla devuelve false")
	void envioFallido() {
		doThrow(new MailSendException("sin conexión")).when(mailSender).send(any(SimpleMailMessage.class));

		assertThat(new CorreoUtil(mailSender, "pruebas@marketplace.local").enviarCorreo("ana@correo.com", "Asunto",
				"Texto")).isFalse();
	}
}
