package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import co.edu.unbosque.marketplaceInversionesLR.dto.NotificacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Notificacion;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de las notificaciones (HU-02): el registro con cada evento
 * (CA-010) y la consulta y el marcado solo de las propias (CA-013), con el
 * repositorio simulado.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

	/** Usuario dueño de las notificaciones. */
	private static final Long USUARIO = 12L;

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

	@Test
	@DisplayName("CA-010 Un evento registra la notificación asociada al usuario correspondiente")
	void registraPorEvento_CA010() {
		servicio.enviar(9L, NotificacionService.RESERVA_CONFIRMADA, "Tu reserva 15 está confirmada.");

		ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
		verify(notificacionRepo).save(guardada.capture());
		assertThat(guardada.getValue().getIdUsuario()).isEqualTo(9L);
		assertThat(guardada.getValue().getTipo()).isEqualTo(NotificacionService.RESERVA_CONFIRMADA);
	}

	@Test
	@DisplayName("CA-013 Listar pide solo las del usuario, con la página recibida, y las convierte")
	void listaLasPropias_CA013() {
		Pageable pagina = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("fecha")));
		Notificacion propia = notificacion(21L, USUARIO, false);
		when(notificacionRepo.findByIdUsuario(USUARIO, pagina)).thenReturn(new PageImpl<>(List.of(propia), pagina, 1));

		PaginaDTO<NotificacionDTO> resultado = servicio.listar(USUARIO, pagina);

		assertThat(resultado.getTotalElementos()).isEqualTo(1);
		NotificacionDTO dto = resultado.getContenido().get(0);
		assertThat(dto.getIdNotificacion()).isEqualTo(21L);
		assertThat(dto.getTipo()).isEqualTo(NotificacionService.RESERVA_CONFIRMADA);
		assertThat(dto.getMensaje()).isEqualTo("Tu reserva 15 está confirmada.");
		assertThat(dto.getFecha()).isEqualTo(propia.getFecha());
		assertThat(dto.getLeida()).isFalse();
		assertThat(dto.getIdUsuario()).isEqualTo(USUARIO);
	}

	@Test
	@DisplayName("CA-013 El contador y el marcado masivo solo cuentan las del usuario")
	void contarYMarcarTodas_CA013() {
		when(notificacionRepo.countByIdUsuarioAndLeidaFalse(USUARIO)).thenReturn(3L);
		when(notificacionRepo.marcarTodasLeidas(USUARIO)).thenReturn(3);

		assertThat(servicio.contarNoLeidas(USUARIO)).isEqualTo(3L);
		assertThat(servicio.marcarTodasLeidas(USUARIO)).isEqualTo(3);
	}

	@Test
	@DisplayName("CA-013 Marcar una propia la deja leída; una ajena o inexistente responde no encontrado")
	void marcarUna_CA013() {
		Notificacion propia = notificacion(21L, USUARIO, false);
		when(notificacionRepo.findByIdNotificacionAndIdUsuario(21L, USUARIO)).thenReturn(Optional.of(propia));
		when(notificacionRepo.findByIdNotificacionAndIdUsuario(22L, USUARIO)).thenReturn(Optional.empty());
		when(notificacionRepo.save(propia)).thenReturn(propia);

		Resultado<NotificacionDTO> marcada = servicio.marcarLeida(21L, USUARIO);
		Resultado<NotificacionDTO> ajena = servicio.marcarLeida(22L, USUARIO);

		assertThat(marcada.esExito()).isTrue();
		assertThat(marcada.getDato().getLeida()).isTrue();
		assertThat(propia.getLeida()).isTrue();
		assertThat(ajena.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(ajena.getMensaje()).isEqualTo(NotificacionService.NO_EXISTE);
		assertThat(servicio.marcarLeida(null, USUARIO).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
	}

	@Test
	@DisplayName("Marcar una ya leída la devuelve sin volver a guardarla")
	void yaLeida() {
		Notificacion leida = notificacion(21L, USUARIO, true);
		when(notificacionRepo.findByIdNotificacionAndIdUsuario(21L, USUARIO)).thenReturn(Optional.of(leida));

		assertThat(servicio.marcarLeida(21L, USUARIO).getDato().getLeida()).isTrue();
		verify(notificacionRepo, never()).save(any());
	}

	/**
	 * Notificación de prueba de una reserva confirmada.
	 *
	 * @param id        id de la notificación
	 * @param idUsuario dueño
	 * @param leida     si ya se leyó
	 * @return notificación
	 */
	private static Notificacion notificacion(Long id, Long idUsuario, boolean leida) {
		Notificacion notificacion = new Notificacion();
		notificacion.setIdNotificacion(id);
		notificacion.setIdUsuario(idUsuario);
		notificacion.setTipo(NotificacionService.RESERVA_CONFIRMADA);
		notificacion.setMensaje("Tu reserva 15 está confirmada.");
		notificacion.setFecha(LocalDateTime.now().minusHours(1));
		notificacion.setLeida(leida);
		return notificacion;
	}
}
