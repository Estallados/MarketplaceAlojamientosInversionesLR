package co.edu.unbosque.marketplaceInversionesLR.configuration;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import co.edu.unbosque.marketplaceInversionesLR.security.WebSocketAuthInterceptor;

/**
 * WebSocket con STOMP para mensajes y notificaciones en tiempo real. El cliente
 * se conecta a {@code ws://host:8081/ws} con el JWT en el frame CONNECT y se
 * suscribe a {@code /user/queue/mensajes} y {@code /user/queue/notificaciones};
 * cada usuario recibe solo lo suyo. Los mensajes se siguen enviando por REST.
 *
 * @see <a href="https://spring.io/guides/gs/messaging-stomp-websocket">Guía
 *      STOMP de Spring</a>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** Interceptor que autentica la conexión y controla las suscripciones. */
	private final WebSocketAuthInterceptor authInterceptor;

	/** Orígenes del frontend que pueden abrir el WebSocket (los mismos de CORS). */
	private final List<String> origenesPermitidos;

	/**
	 * Crea la configuración.
	 *
	 * @param authInterceptor    interceptor de seguridad de STOMP
	 * @param origenesPermitidos orígenes permitidos; por defecto
	 *                           {@code http://localhost:4200}
	 */
	public WebSocketConfig(WebSocketAuthInterceptor authInterceptor,
			@Value("${app.cors.allowed-origins:http://localhost:4200}") List<String> origenesPermitidos) {
		this.authInterceptor = authInterceptor;
		this.origenesPermitidos = origenesPermitidos;
	}

	/**
	 * Registra el punto de conexión {@code /ws}, sin SockJS.
	 *
	 * @param registry registro de endpoints STOMP
	 */
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// Mismos orígenes que CORS (SecurityConfig)
		registry.addEndpoint("/ws").setAllowedOrigins(origenesPermitidos.stream().map(String::trim)
				.toArray(String[]::new));
	}

	/**
	 * Usa el broker simple en memoria con destinos {@code /queue} y el prefijo
	 * {@code /user} para los destinos de cada usuario.
	 *
	 * @param registry registro del broker
	 */
	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue");
		registry.setUserDestinationPrefix("/user");
	}

	/**
	 * Pone el interceptor de seguridad en el canal de entrada, para que revise
	 * cada frame que manda el cliente.
	 *
	 * @param registration configuración del canal de entrada
	 */
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(authInterceptor);
	}
}
