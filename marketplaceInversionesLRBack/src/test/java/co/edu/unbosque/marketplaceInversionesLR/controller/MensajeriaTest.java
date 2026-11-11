package co.edu.unbosque.marketplaceInversionesLR.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;

import co.edu.unbosque.marketplaceInversionesLR.ContenedorPostgres;
import co.edu.unbosque.marketplaceInversionesLR.entity.Alojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoAlojamiento;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.AlojamientoRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConversacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.MensajeRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.NotificacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.security.JwtUtil;
import co.edu.unbosque.marketplaceInversionesLR.service.MensajeriaService;
import co.edu.unbosque.marketplaceInversionesLR.service.NotificacionService;
import co.edu.unbosque.marketplaceInversionesLR.service.UsuarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import tools.jackson.databind.JsonNode;

/**
 * Pruebas de HU-28 Mensajería entre huésped y anfitrión sobre la API completa,
 * el WebSocket real (cliente STOMP en un puerto aleatorio) y un PostgreSQL
 * temporal en Docker.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ContenedorPostgres.class)
@Testcontainers(disabledWithoutDocker = true)
class MensajeriaTest {

	/** Ruta base de la mensajería. */
	private static final String RUTA = "/conversaciones";

	/** Segundos de espera para lo que llega por WebSocket. */
	private static final long ESPERA = 5;

	@LocalServerPort
	private int puerto;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ConversacionRepository conversacionRepository;

	@Autowired
	private MensajeRepository mensajeRepository;

	@Autowired
	private NotificacionRepository notificacionRepository;

	@Autowired
	private AlojamientoRepository alojamientoRepository;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private SimpUserRegistry usuariosConectados;

	@MockitoBean
	private JavaMailSender mailSender;

	/** Anfitrión dueño del alojamiento. */
	private Usuario anfitrion;

	/** Huésped que escribe. */
	private Usuario huesped;

	/** Token del anfitrión. */
	private String tokenAnfitrion;

	/** Token del huésped. */
	private String tokenHuesped;

	/** Token de un usuario que no participa. */
	private String tokenIntruso;

	/** Alojamiento publicado del anfitrión. */
	private Long idAlojamiento;

	/** Cliente STOMP. */
	private WebSocketStompClient cliente;

	@BeforeEach
	void preparar() {
		limpiar();
		anfitrion = crearUsuario("beto.mensajeria@correo.com", "Beto Anfitrión", Role.ANFITRION);
		huesped = crearUsuario("ana.mensajeria@correo.com", "Ana Huésped", Role.HUESPED);
		tokenAnfitrion = jwtUtil.generateToken(anfitrion);
		tokenHuesped = jwtUtil.generateToken(huesped);
		tokenIntruso = jwtUtil.generateToken(crearUsuario("caro.mensajeria@correo.com", "Caro", Role.HUESPED));
		idAlojamiento = crearAlojamiento();

		cliente = new WebSocketStompClient(new StandardWebSocketClient());
		cliente.setMessageConverter(new CompositeMessageConverter(
				List.of(new StringMessageConverter(), new JacksonJsonMessageConverter())));
	}

	/** Borra en orden de llaves foráneas: mensajes, conversaciones y luego el resto. */
	@AfterEach
	void limpiar() {
		if (cliente != null) {
			cliente.stop();
		}
		mensajeRepository.deleteAll();
		conversacionRepository.deleteAll();
		notificacionRepository.deleteAll();
		jdbc.update("DELETE FROM alojamiento_amenidad");
		jdbc.update("DELETE FROM alojamiento");
		usuarioRepository.deleteAll();
	}

	@Test
	@DisplayName("CA-169 Desde el alojamiento el huésped inicia la conversación con su anfitrión; si ya existe se retoma")
	void iniciaConElAnfitrion_CA169() throws Exception {
		Integer creada = JsonPath.read(iniciar("{\"idAlojamiento\":" + idAlojamiento
				+ ",\"mensaje\":\"¿Tiene parqueadero?\"}", tokenHuesped)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idAnfitrion").value(anfitrion.getIdUsuario()))
				.andExpect(jsonPath("$.idUsuario").value(huesped.getIdUsuario()))
				.andExpect(jsonPath("$.nombreUsuario").value("Ana Huésped"))
				.andExpect(jsonPath("$.nombreAnfitrion").value("Beto Anfitrión"))
				.andExpect(jsonPath("$.estado").value("ACTIVA"))
				.andReturn().getResponse().getContentAsString(), "$.idConversacion");

		iniciar("{\"idAlojamiento\":" + idAlojamiento + ",\"mensaje\":\"Hola de nuevo\"}", tokenHuesped)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idConversacion").value(creada));
		assertThat(conversacionRepository.count()).isEqualTo(1);

		iniciar("{\"idAlojamiento\":" + idAlojamiento + "}", tokenAnfitrion).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(MensajeriaService.PROPIO_ALOJAMIENTO));
		iniciar("{\"idAlojamiento\":999999}", tokenHuesped).andExpect(status().isNotFound());
		iniciar("{}", tokenHuesped).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("CA-165 El mensaje queda con remitente y fecha, el otro participante lo lee y recibe la notificación")
	void enviaYElOtroLoLee_CA165() throws Exception {
		Integer conversacion = crearConversacion("¿Tiene parqueadero?");

		enviar(conversacion, "{\"contenido\":\"Sí, cubierto\"}", tokenAnfitrion).andExpect(status().isCreated())
				.andExpect(jsonPath("$.idUsuario").value(anfitrion.getIdUsuario()))
				.andExpect(jsonPath("$.nombreUsuario").value("Beto Anfitrión"))
				.andExpect(jsonPath("$.fechaEnvio").isNotEmpty());

		mensajes(conversacion, tokenHuesped).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.contenido[0].idUsuario").value(huesped.getIdUsuario()))
				.andExpect(jsonPath("$.contenido[0].contenido").value("¿Tiene parqueadero?"))
				.andExpect(jsonPath("$.contenido[1].contenido").value("Sí, cubierto"));
		mockMvc.perform(get("/notificaciones").header("Authorization", "Bearer " + tokenHuesped))
				.andExpect(jsonPath("$.contenido[0].tipo").value(NotificacionService.MENSAJE_RECIBIDO));
		enviar(conversacion, "{\"contenido\":\"   \"}", tokenHuesped).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("CA-167 Quien no participa no ve ni escribe en la conversación y no aparece en su lista")
	void soloParticipantes_CA167() throws Exception {
		Integer conversacion = crearConversacion(null);

		mensajes(conversacion, tokenIntruso).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(MensajeriaService.NO_PARTICIPA));
		enviar(conversacion, "{\"contenido\":\"Hola\"}", tokenIntruso).andExpect(status().isForbidden());
		listar(tokenIntruso).andExpect(jsonPath("$.totalElementos").value(0));
		listar(tokenAnfitrion).andExpect(jsonPath("$.totalElementos").value(1));
		listar(tokenHuesped).andExpect(jsonPath("$.contenido[0].idConversacion").value(conversacion));
		assertThat(mensajeRepository.count()).isZero();
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("CA-165 El mensaje y su notificación llegan en tiempo real por WebSocket")
	void llegaPorWebSocket_CA165() throws Exception {
		StompSession sesion = conectar(tokenAnfitrion, new CompletableFuture<>()).get(ESPERA, TimeUnit.SECONDS);
		BlockingQueue<JsonNode> mensajes = suscribir(sesion, "/user/queue/mensajes");
		BlockingQueue<JsonNode> notificaciones = suscribir(sesion, "/user/queue/notificaciones");
		esperarSuscripciones(anfitrion.getIdUsuario(), 2);

		Integer conversacion = crearConversacion("Hola, ¿está disponible?");

		JsonNode recibido = siguiente(mensajes);
		assertThat(recibido.get("contenido").asString()).isEqualTo("Hola, ¿está disponible?");
		assertThat(recibido.get("idConversacion").asLong()).isEqualTo(conversacion.longValue());
		assertThat(recibido.get("idUsuario").asLong()).isEqualTo(huesped.getIdUsuario());
		assertThat(siguiente(notificaciones).get("tipo").asString()).isEqualTo(NotificacionService.MENSAJE_RECIBIDO);

		// Al remitente también le llega su mensaje (para sus otras pestañas)
		enviar(conversacion, "{\"contenido\":\"Sí, te esperamos\"}", tokenAnfitrion).andExpect(status().isCreated());
		assertThat(siguiente(mensajes).get("idUsuario").asLong()).isEqualTo(anfitrion.getIdUsuario());
	}

	@Test
	@DisplayName("CA-167 Por WebSocket no se conecta sin token ni se escucha o escribe en colas ajenas")
	void webSocketSoloLoPropio_CA167() throws Exception {
		assertThatThrownBy(() -> conectar(null, new CompletableFuture<>()).get(ESPERA, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class);
		assertThatThrownBy(() -> conectar("token.falso.123", new CompletableFuture<>()).get(ESPERA, TimeUnit.SECONDS))
				.isInstanceOf(ExecutionException.class);

		StompSession victima = conectar(tokenHuesped, new CompletableFuture<>()).get(ESPERA, TimeUnit.SECONDS);
		BlockingQueue<JsonNode> mensajesVictima = suscribir(victima, "/user/queue/mensajes");
		esperarSuscripciones(huesped.getIdUsuario(), 1);

		// Enviar directo a la cola de otro usuario se rechaza y no le llega nada
		CompletableFuture<String> errorEnvio = new CompletableFuture<>();
		StompSession atacante = conectar(tokenIntruso, errorEnvio).get(ESPERA, TimeUnit.SECONDS);
		atacante.send("/user/" + huesped.getIdUsuario() + "/queue/mensajes", "falso");
		assertThat(errorEnvio.get(ESPERA, TimeUnit.SECONDS)).isNotNull();
		assertThat(mensajesVictima.poll(1, TimeUnit.SECONDS)).isNull();

		// Suscribirse fuera de /user/queue/ también se rechaza
		CompletableFuture<String> errorSuscripcion = new CompletableFuture<>();
		StompSession curioso = conectar(tokenIntruso, errorSuscripcion).get(ESPERA, TimeUnit.SECONDS);
		curioso.subscribe("/queue/mensajes", new StompSessionHandlerAdapter() {
		});
		assertThat(errorSuscripcion.get(ESPERA, TimeUnit.SECONDS)).isNotNull();
	}

	/**
	 * Conecta; un frame ERROR del servidor o el cierre de la conexión que lo sigue
	 * (el que llegue primero) completan {@code error}.
	 */
	private CompletableFuture<StompSession> conectar(String token, CompletableFuture<String> error) {
		StompHeaders conexion = new StompHeaders();
		if (token != null) {
			conexion.add("Authorization", "Bearer " + token);
		}
		return cliente.connectAsync("ws://localhost:" + puerto + "/ws", new WebSocketHttpHeaders(), conexion,
				new StompSessionHandlerAdapter() {
					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						error.complete(headers.getFirst("message"));
					}

					@Override
					public void handleException(StompSession session, StompCommand command, StompHeaders headers,
							byte[] payload, Throwable exception) {
						error.complete(String.valueOf(exception.getMessage()));
					}

					@Override
					public void handleTransportError(StompSession session, Throwable exception) {
						error.complete(String.valueOf(exception.getMessage()));
					}
				});
	}

	private static BlockingQueue<JsonNode> suscribir(StompSession sesion, String destino) {
		BlockingQueue<JsonNode> cola = new LinkedBlockingQueue<>();
		sesion.subscribe(destino, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return JsonNode.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				cola.add((JsonNode) payload);
			}
		});
		return cola;
	}

	/** El broker no confirma las suscripciones: se espera a verlas registradas. */
	private void esperarSuscripciones(Long idUsuario, int cantidad) throws InterruptedException {
		for (int intento = 0; intento < 50; intento++) {
			SimpUser conectado = usuariosConectados.getUser(String.valueOf(idUsuario));
			if (conectado != null && conectado.getSessions().stream()
					.mapToLong(s -> s.getSubscriptions().size()).sum() >= cantidad) {
				return;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Las suscripciones no se registraron");
	}

	private static JsonNode siguiente(BlockingQueue<JsonNode> cola) throws InterruptedException {
		JsonNode dato = cola.poll(ESPERA, TimeUnit.SECONDS);
		assertThat(dato).as("No llegó el mensaje esperado por WebSocket").isNotNull();
		return dato;
	}

	private Integer crearConversacion(String mensaje) throws Exception {
		String cuerpo = mensaje == null ? "{\"idAlojamiento\":" + idAlojamiento + "}"
				: "{\"idAlojamiento\":" + idAlojamiento + ",\"mensaje\":\"" + mensaje + "\"}";
		return JsonPath.read(iniciar(cuerpo, tokenHuesped).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString(), "$.idConversacion");
	}

	private ResultActions iniciar(String cuerpo, String token) throws Exception {
		return mockMvc.perform(post(RUTA).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions enviar(Integer conversacion, String cuerpo, String token) throws Exception {
		return mockMvc.perform(post(RUTA + "/" + conversacion + "/mensajes").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions mensajes(Integer conversacion, String token) throws Exception {
		return mockMvc.perform(get(RUTA + "/" + conversacion + "/mensajes").header("Authorization", "Bearer " + token));
	}

	private ResultActions listar(String token) throws Exception {
		return mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token));
	}

	private Long crearAlojamiento() {
		Alojamiento alojamiento = new Alojamiento();
		alojamiento.setTitulo("Casa en el lago");
		alojamiento.setIdAnfitrion(anfitrion.getIdUsuario());
		alojamiento.setEstado(EstadoAlojamiento.ACTIVA);
		alojamiento.setCapacidad(4);
		alojamiento.setPrecioNoche(new BigDecimal("200000"));
		alojamiento.setFechaCreacion(LocalDateTime.now());
		return alojamientoRepository.save(alojamiento).getIdAlojamiento();
	}

	private Usuario crearUsuario(String correo, String nombre, Role rol) {
		Usuario usuario = new Usuario();
		usuario.setNombre(nombre);
		usuario.setCorreo(AESUtil.encrypt(correo));
		usuario.setCorreoHash(UsuarioService.hashCorreo(correo));
		usuario.setContrasenia("no-se-usa");
		usuario.setRol(rol);
		usuario.setEstado(EstadoUsuario.ACTIVO);
		usuario.setFechaRegistro(LocalDateTime.now());
		usuario.setCorreoVerificado(true);
		usuario.setTotpHabilitado(false);
		return usuarioRepository.save(usuario);
	}
}
