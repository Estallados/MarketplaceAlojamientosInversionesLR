package co.edu.unbosque.marketplaceInversionesLR.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConversacionDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.MensajeDTO;
import co.edu.unbosque.marketplaceInversionesLR.dto.PaginaDTO;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.Conversacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoConversacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Mensaje;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConversacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.MensajeRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;

/**
 * Pruebas unitarias de la mensajería (HU-28): envío con remitente y fecha,
 * notificación y envío por WebSocket (CA-165), acceso solo de los
 * participantes (CA-167) y conversación con el anfitrión del alojamiento
 * (CA-169), con los repositorios simulados.
 */
@ExtendWith(MockitoExtension.class)
class MensajeriaServiceTest {

	/** Huésped. */
	private static final Long HUESPED = 5L;

	/** Anfitrión del alojamiento. */
	private static final Long ANFITRION = 8L;

	/** Usuario que no participa. */
	private static final Long INTRUSO = 13L;

	/** Alojamiento. */
	private static final Long ALOJAMIENTO = 40L;

	/** Conversación existente. */
	private static final Long CONVERSACION = 70L;

	@Mock
	private ConversacionRepository conversacionRepo;

	@Mock
	private MensajeRepository mensajeRepo;

	@Mock
	private AlojamientoRepository alojamientoRepo;

	@Mock
	private UsuarioRepository usuarioRepo;

	@Mock
	private NotificacionService notificacionService;

	@Mock
	private TiempoRealService tiempoReal;

	private MensajeriaService servicio;

	@BeforeEach
	void preparar() {
		servicio = new MensajeriaService(conversacionRepo, mensajeRepo, alojamientoRepo, usuarioRepo,
				notificacionService, tiempoReal, new ModelMapper());
		lenient().when(usuarioRepo.findAllById(any()))
				.thenReturn(List.of(usuario(HUESPED, "Ana Huésped"), usuario(ANFITRION, "Beto Anfitrión")));
		lenient().when(mensajeRepo.save(any(Mensaje.class))).thenAnswer(inv -> {
			Mensaje m = inv.getArgument(0);
			m.setIdMensaje(99L);
			return m;
		});
	}

	@Test
	@DisplayName("CA-165 El mensaje queda con remitente y fecha, y se notifica al otro participante")
	void enviaConRemitenteYFecha_CA165() {
		when(conversacionRepo.findById(CONVERSACION)).thenReturn(Optional.of(conversacion()));
		LocalDateTime antes = LocalDateTime.now();

		Resultado<MensajeDTO> resultado = servicio.enviar(CONVERSACION, "  ¿Tiene parqueadero?  ", HUESPED);

		assertThat(resultado.esExito()).isTrue();
		ArgumentCaptor<Mensaje> guardado = ArgumentCaptor.forClass(Mensaje.class);
		verify(mensajeRepo).save(guardado.capture());
		assertThat(guardado.getValue().getIdUsuario()).isEqualTo(HUESPED);
		assertThat(guardado.getValue().getIdConversacion()).isEqualTo(CONVERSACION);
		assertThat(guardado.getValue().getContenido()).isEqualTo("¿Tiene parqueadero?");
		assertThat(guardado.getValue().getFechaEnvio()).isAfterOrEqualTo(antes);
		MensajeDTO dto = resultado.getDato();
		assertThat(dto.getIdMensaje()).isEqualTo(99L);
		assertThat(dto.getNombreUsuario()).isEqualTo("Ana Huésped");
		assertThat(dto.getFechaEnvio()).isNotNull();
		verify(notificacionService).enviar(ANFITRION, NotificacionService.MENSAJE_RECIBIDO,
				"Tienes un mensaje nuevo en la conversación " + CONVERSACION + ".");
	}

	@Test
	@DisplayName("CA-165 El mensaje se envía por WebSocket al destinatario y al remitente")
	void enviaPorWebSocket_CA165() {
		when(conversacionRepo.findById(CONVERSACION)).thenReturn(Optional.of(conversacion()));

		MensajeDTO dto = servicio.enviar(CONVERSACION, "Sí, cubierto", ANFITRION).getDato();

		verify(tiempoReal).enviarAUsuario(HUESPED, TiempoRealService.MENSAJES, dto);
		verify(tiempoReal).enviarAUsuario(ANFITRION, TiempoRealService.MENSAJES, dto);
		verify(notificacionService).enviar(eq(HUESPED), eq(NotificacionService.MENSAJE_RECIBIDO), anyString());
	}

	@Test
	@DisplayName("CA-165 Un mensaje vacío o de más de 2000 caracteres no se envía")
	void mensajeInvalido_CA165() {
		assertThat(servicio.enviar(CONVERSACION, "   ", HUESPED).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.enviar(CONVERSACION, null, HUESPED).getMensaje())
				.isEqualTo(MensajeriaService.MENSAJE_INVALIDO);
		assertThat(servicio.enviar(CONVERSACION, "x".repeat(MensajeriaService.LARGO_MAXIMO + 1), HUESPED)
				.getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);

		verify(mensajeRepo, never()).save(any());
		verify(notificacionService, never()).enviar(anyLong(), anyString(), anyString());
	}

	@Test
	@DisplayName("CA-167 Quien no participa no puede escribir ni leer la conversación")
	void soloParticipantes_CA167() {
		when(conversacionRepo.findById(CONVERSACION)).thenReturn(Optional.of(conversacion()));
		Pageable pagina = PageRequest.of(0, 50);

		Resultado<MensajeDTO> envio = servicio.enviar(CONVERSACION, "Hola", INTRUSO);
		Resultado<PaginaDTO<MensajeDTO>> lectura = servicio.mensajes(CONVERSACION, INTRUSO, pagina);

		assertThat(envio.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(envio.getMensaje()).isEqualTo(MensajeriaService.NO_PARTICIPA);
		assertThat(lectura.getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		verify(mensajeRepo, never()).save(any());
		verify(mensajeRepo, never()).findByIdConversacion(anyLong(), any());
		verify(tiempoReal, never()).enviarAUsuario(any(), any(), any());
	}

	@Test
	@DisplayName("CA-167 Los participantes leen los mensajes con el nombre de cada remitente")
	void participantesLeen_CA167() {
		when(conversacionRepo.findById(CONVERSACION)).thenReturn(Optional.of(conversacion()));
		Pageable pagina = PageRequest.of(0, 50);
		when(mensajeRepo.findByIdConversacion(CONVERSACION, pagina))
				.thenReturn(new PageImpl<>(List.of(mensaje(1L, HUESPED, "Hola"), mensaje(2L, ANFITRION, "Hola Ana")),
						pagina, 2));

		Resultado<PaginaDTO<MensajeDTO>> resultado = servicio.mensajes(CONVERSACION, ANFITRION, pagina);

		assertThat(resultado.esExito()).isTrue();
		List<MensajeDTO> mensajes = resultado.getDato().getContenido();
		assertThat(mensajes).extracting(MensajeDTO::getNombreUsuario).containsExactly("Ana Huésped",
				"Beto Anfitrión");
		assertThat(mensajes).extracting(MensajeDTO::getContenido).containsExactly("Hola", "Hola Ana");
	}

	@Test
	@DisplayName("CA-167 Una conversación que no existe responde no encontrada")
	void conversacionInexistente_CA167() {
		when(conversacionRepo.findById(CONVERSACION)).thenReturn(Optional.empty());

		Resultado<MensajeDTO> resultado = servicio.enviar(CONVERSACION, "Hola", HUESPED);

		assertThat(resultado.getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(resultado.getMensaje()).isEqualTo(MensajeriaService.NO_EXISTE);
	}

	@Test
	@DisplayName("CA-167 Mis conversaciones busca las del usuario como huésped o anfitrión")
	void listaLasPropias_CA167() {
		Pageable pagina = PageRequest.of(0, 20);
		when(conversacionRepo.buscarDeParticipante(ANFITRION, pagina))
				.thenReturn(new PageImpl<>(List.of(conversacion()), pagina, 1));

		PaginaDTO<ConversacionDTO> resultado = servicio.listar(ANFITRION, pagina);

		assertThat(resultado.getTotalElementos()).isEqualTo(1);
		assertThat(resultado.getContenido().get(0).getNombreUsuario()).isEqualTo("Ana Huésped");
		assertThat(resultado.getContenido().get(0).getNombreAnfitrion()).isEqualTo("Beto Anfitrión");
	}

	@Test
	@DisplayName("CA-169 Desde el alojamiento se crea la conversación con su anfitrión y el primer mensaje")
	void iniciaConElAnfitrion_CA169() {
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento()));
		when(conversacionRepo.findByIdUsuarioAndIdAlojamiento(HUESPED, ALOJAMIENTO)).thenReturn(Optional.empty());
		when(conversacionRepo.save(any(Conversacion.class))).thenAnswer(inv -> {
			Conversacion c = inv.getArgument(0);
			c.setIdConversacion(CONVERSACION);
			return c;
		});

		Resultado<ConversacionDTO> resultado = servicio.iniciar(datos("¿Tiene parqueadero?"), HUESPED);

		assertThat(resultado.esExito()).isTrue();
		ConversacionDTO dto = resultado.getDato();
		assertThat(dto.getIdConversacion()).isEqualTo(CONVERSACION);
		assertThat(dto.getIdUsuario()).isEqualTo(HUESPED);
		assertThat(dto.getIdAnfitrion()).isEqualTo(ANFITRION);
		assertThat(dto.getIdAlojamiento()).isEqualTo(ALOJAMIENTO);
		assertThat(dto.getEstado()).isEqualTo(EstadoConversacion.ACTIVA);
		assertThat(dto.getNombreAnfitrion()).isEqualTo("Beto Anfitrión");
		verify(mensajeRepo).save(any(Mensaje.class));
		verify(notificacionService).enviar(eq(ANFITRION), eq(NotificacionService.MENSAJE_RECIBIDO), anyString());
	}

	@Test
	@DisplayName("CA-169 Si ya existía se retoma la misma conversación sin crear otra")
	void retomaLaExistente_CA169() {
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento()));
		when(conversacionRepo.findByIdUsuarioAndIdAlojamiento(HUESPED, ALOJAMIENTO))
				.thenReturn(Optional.of(conversacion()));

		Resultado<ConversacionDTO> resultado = servicio.iniciar(datos(null), HUESPED);

		assertThat(resultado.getDato().getIdConversacion()).isEqualTo(CONVERSACION);
		verify(conversacionRepo, never()).save(any());
		verify(mensajeRepo, never()).save(any());
		assertThat(servicio.tieneConversacion(HUESPED, ALOJAMIENTO)).isTrue();
		assertThat(servicio.tieneConversacion(HUESPED, null)).isFalse();
	}

	@Test
	@DisplayName("CA-169 Sin alojamiento, con uno inexistente, propio o con mensaje muy largo no se inicia")
	void validaciones_CA169() {
		when(alojamientoRepo.findById(ALOJAMIENTO)).thenReturn(Optional.of(alojamiento()));
		when(alojamientoRepo.findById(1L)).thenReturn(Optional.empty());
		ConversacionDTO inexistente = datos(null);
		inexistente.setIdAlojamiento(1L);

		assertThat(servicio.iniciar(new ConversacionDTO(), HUESPED).getMensaje())
				.isEqualTo(MensajeriaService.FALTA_ALOJAMIENTO);
		assertThat(servicio.iniciar(null, HUESPED).getCodigo()).isEqualTo(Resultado.DATOS_INVALIDOS);
		assertThat(servicio.iniciar(inexistente, HUESPED).getCodigo()).isEqualTo(Resultado.NO_ENCONTRADO);
		assertThat(servicio.iniciar(datos(null), ANFITRION).getCodigo()).isEqualTo(Resultado.SIN_PERMISO);
		assertThat(servicio.iniciar(datos("x".repeat(2001)), HUESPED).getMensaje())
				.isEqualTo(MensajeriaService.PRIMER_MENSAJE_LARGO);
		verify(conversacionRepo, never()).save(any());
	}

	private static ConversacionDTO datos(String mensaje) {
		ConversacionDTO datos = new ConversacionDTO();
		datos.setIdAlojamiento(ALOJAMIENTO);
		datos.setMensaje(mensaje);
		return datos;
	}

	private static Alojamiento alojamiento() {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setIdAlojamiento(ALOJAMIENTO);
		alojamiento.setIdAnfitrion(ANFITRION);
		return alojamiento;
	}

	private static Conversacion conversacion() {
		Conversacion conversacion = new Conversacion();
		conversacion.setIdConversacion(CONVERSACION);
		conversacion.setIdUsuario(HUESPED);
		conversacion.setIdAnfitrion(ANFITRION);
		conversacion.setIdAlojamiento(ALOJAMIENTO);
		conversacion.setEstado(EstadoConversacion.ACTIVA);
		conversacion.setFechaCreacion(LocalDateTime.now());
		return conversacion;
	}

	private static Mensaje mensaje(Long id, Long remitente, String contenido) {
		Mensaje mensaje = new Mensaje();
		mensaje.setIdMensaje(id);
		mensaje.setIdConversacion(CONVERSACION);
		mensaje.setIdUsuario(remitente);
		mensaje.setContenido(contenido);
		mensaje.setFechaEnvio(LocalDateTime.now());
		return mensaje;
	}

	private static Usuario usuario(Long id, String nombre) {
		Usuario usuario = new Usuario();
		usuario.setIdUsuario(id);
		usuario.setNombre(nombre);
		return usuario;
	}
}
