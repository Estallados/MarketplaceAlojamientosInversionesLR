package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;

/**
 * Pruebas unitarias del registro de notificaciones, con el repositorio
 * simulado.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	@Mock
	private NotificacionRepository notificacionRepo;

	private NotificacionService servicio;

	@BeforeEach
	void preparar() {
		servicio = new NotificacionService(notificacionRepo);
	}

	@Test
	@DisplayName("CA-195 La notificación queda guardada sin leer para el usuario, con su tipo y mensaje")
	void registraLaNotificacion_CA195() {
		LocalDateTime antes = LocalDateTime.now();

		servicio.enviar(4L, NotificacionService.ALOJAMIENTO_RECHAZADO, "Tu alojamiento fue rechazado.");

		ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
		verify(notificacionRepo).save(guardada.capture());
		assertThat(guardada.getValue().getIdUsuario()).isEqualTo(4L);
		assertThat(guardada.getValue().getTipo()).isEqualTo(NotificacionService.ALOJAMIENTO_RECHAZADO);
		assertThat(guardada.getValue().getMensaje()).isEqualTo("Tu alojamiento fue rechazado.");
		assertThat(guardada.getValue().getLeida()).isFalse();
		assertThat(guardada.getValue().getFecha()).isAfterOrEqualTo(antes);
	}

	@Test
	@DisplayName("Un mensaje muy largo se corta para que quepa en la columna")
	void cortaMensajesLargos() {
		servicio.enviar(4L, NotificacionService.ALOJAMIENTO_APROBADO, "x".repeat(LimitesTexto.MEDIO + 50));

		ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
		verify(notificacionRepo).save(guardada.capture());
		assertThat(guardada.getValue().getMensaje()).hasSize(LimitesTexto.MEDIO);
		assertThat(LimitesTexto.recortar(null, 5)).isNull();
		assertThat(LimitesTexto.recortar("abc", 5)).isEqualTo("abc");
	}

	@Test
	@DisplayName("Sin destinatario no se guarda nada")
	void sinDestinatario() {
		servicio.enviar(null, NotificacionService.ALOJAMIENTO_APROBADO, "Mensaje");

		verify(notificacionRepo, never()).save(any());
	}
}
