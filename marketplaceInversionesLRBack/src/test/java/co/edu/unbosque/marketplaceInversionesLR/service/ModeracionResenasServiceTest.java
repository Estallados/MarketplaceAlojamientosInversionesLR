package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.ResenaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoResena;
import co.edu.unbosque.marketplaceInversionesLR.entity.Resena;
import co.edu.unbosque.marketplaceInversionesLR.entity.ResultadoAuditoria;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ResenaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ReservaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.LimitesTexto;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de HU-14 Ocultar reseñas que incumplan reglas de
 * moderación, con los repositorios, las notificaciones y la auditoría
 * simulados.
 */
@ExtendWith(MockitoExtension.class)
class ModeracionResenasServiceTest {

	/** Administrador que modera. */
	private static final Long ADMIN = 100L;

	/** Autor de la reseña. */
	private static final Long AUTOR = 3L;

	/** Reseña moderada. */
	private static final Long ID_RESENA = 9L;

	/** Reserva reseñada. */
	private static final Long ID_RESERVA = 15L;

	/** Motivo de prueba. */
	private static final String MOTIVO = "Lenguaje ofensivo";

	/** Página por defecto. */
	private static final Pageable PAGINA = PageRequest.of(0, 20);

	@Mock
	private ResenaRepository resenaRepo;

	@Mock
	private ReservaRepository reservaRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private NotificacionService notificacionService;

	@Mock
	private AuditoriaService auditoriaService;

	private ResenaService servicio;

	@BeforeEach
	void preparar() {
		servicio = new ResenaService(resenaRepo, reservaRepo, alojamientoRepo, usuarioRepo, notificacionService,
				auditoriaService, new ModelMapper());
	}

	@Test
	@DisplayName("CA-066 Ocultar cambia solo el estado a OCULTA (no la borra), avisa al autor con el motivo y lo audita")
	void ocultar_CA066() {
		when(resenaRepo.findById(ID_RESENA)).thenReturn(Optional.of(resena(EstadoResena.PUBLICADA)));

		Resultado<ResenaDTO> resultado = servicio.moderar(ID_RESENA, true, "  " + MOTIVO + "  ", ADMIN);

		assertThat(resultado.esExito()).isTrue();
		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoResena.OCULTA);
		assertThat(resultado.getDato().getIdResena()).isEqualTo(ID_RESENA);
		verify(resenaRepo).cambiarEstado(ID_RESENA, EstadoResena.OCULTA);
		verify(resenaRepo, never()).save(any());
		verify(resenaRepo, never()).delete(any());
		verify(notificacionService).enviar(AUTOR, NotificacionService.RESENA_OCULTA,
				ResenaService.AVISO_OCULTA + ID_RESERVA + ". Motivo: " + MOTIVO);
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.RESENA_MODERADA, AuditoriaService.ENTIDAD_RESENA,
				ID_RESENA, ResultadoAuditoria.EXITOSO, "Ocultada: " + MOTIVO);
	}

	@Test
	@DisplayName("Mostrar vuelve a PUBLICADA y lo audita, sin avisar al autor")
	void mostrar() {
		when(resenaRepo.findById(ID_RESENA)).thenReturn(Optional.of(resena(EstadoResena.OCULTA)));

		Resultado<ResenaDTO> resultado = servicio.moderar(ID_RESENA, false, null, ADMIN);

		assertThat(resultado.getDato().getEstado()).isEqualTo(EstadoResena.PUBLICADA);
		verify(resenaRepo).cambiarEstado(ID_RESENA, EstadoResena.PUBLICADA);
		verifyNoInteractions(notificacionService);
		verify(auditoriaService).registrar(ADMIN, AuditoriaService.RESENA_MODERADA, AuditoriaService.ENTIDAD_RESENA,
				ID_RESENA, ResultadoAuditoria.EXITOSO, "Visible de nuevo");
	}

	@Test
	@DisplayName("Si ya estaba en ese estado responde igual, sin repetir el cambio, el aviso ni la auditoría")
	void yaEnEseEstado() {
		when(resenaRepo.findById(ID_RESENA)).thenReturn(Optional.of(resena(EstadoResena.OCULTA)));

		assertThat(servicio.moderar(ID_RESENA, true, MOTIVO, ADMIN).getDato().getEstado())
				.isEqualTo(EstadoResena.OCULTA);

		verify(resenaRepo, never()).cambiarEstado(anyLong(), any());
		verifyNoInteractions(notificacionService, auditoriaService);
	}

	@Test
	@DisplayName("CA-066 Ocultar exige un motivo de máximo 500 caracteres")
	void motivoObligatorio_CA066() {
		for (String sinMotivo : new String[] { null, "", "   " }) {
			Resultado<ResenaDTO> resultado = servicio.moderar(ID_RESENA, true, sinMotivo, ADMIN);
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
			assertThat(resultado.getMensaje()).isEqualTo(ResenaService.MOTIVO_OBLIGATORIO);
		}
		Resultado<ResenaDTO> largo = servicio.moderar(ID_RESENA, true, "x".repeat(LimitesTexto.MOTIVO + 1), ADMIN);
		assertThat(largo.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(largo.getMensaje()).isEqualTo(LimitesTexto.MOTIVO_LARGO);
		verifyNoInteractions(resenaRepo, notificacionService, auditoriaService);
	}

	@Test
	@DisplayName("Una reseña que no existe responde 404")
	void noExiste() {
		when(resenaRepo.findById(404L)).thenReturn(Optional.empty());

		for (Resultado<ResenaDTO> resultado : List.of(servicio.moderar(404L, true, MOTIVO, ADMIN),
				servicio.moderar(404L, false, null, ADMIN), servicio.moderar(null, false, null, ADMIN))) {
			assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
			assertThat(resultado.getMensaje()).isEqualTo(ResenaService.NO_EXISTE);
		}
		verify(resenaRepo, never()).cambiarEstado(anyLong(), any());
	}

	@Test
	@DisplayName("El listado del administrador trae todas o solo las de un estado")
	void listarTodas() {
		when(resenaRepo.findAll(PAGINA)).thenReturn(new PageImpl<>(
				List.of(resena(EstadoResena.PUBLICADA), resena(EstadoResena.OCULTA)), PAGINA, 2));
		when(resenaRepo.findByEstado(eq(EstadoResena.OCULTA), eq(PAGINA)))
				.thenReturn(new PageImpl<>(List.of(resena(EstadoResena.OCULTA)), PAGINA, 1));

		assertThat(servicio.listarTodas(null, PAGINA).getContenido()).extracting(ResenaDTO::getEstado)
				.containsExactly(EstadoResena.PUBLICADA, EstadoResena.OCULTA);
		assertThat(servicio.listarTodas(EstadoResena.OCULTA, PAGINA).getContenido()).extracting(ResenaDTO::getEstado)
				.containsExactly(EstadoResena.OCULTA);
	}

	/**
	 * Reseña del autor en un estado.
	 *
	 * @param estado estado
	 * @return la reseña
	 */
	private static Resena resena(EstadoResena estado) {
		Resena r = new Resena();
		r.setIdResena(ID_RESENA);
		r.setIdReserva(ID_RESERVA);
		r.setIdAlojamiento(10L);
		r.setIdUsuario(AUTOR);
		r.setCalificacion(1);
		r.setComentario("Comentario inapropiado");
		r.setEstado(estado);
		r.setFecha(LocalDateTime.now());
		return r;
	}
}
