package co.edu.unbosque.marketplaceInversionesLR.configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.marketplaceInversionesLR.entity.ConfiguracionTarifa;
import co.edu.unbosque.marketplaceInversionesLR.entity.EstadoUsuario;
import co.edu.unbosque.marketplaceInversionesLR.entity.PoliticaCancelacion;
import co.edu.unbosque.marketplaceInversionesLR.entity.Role;
import co.edu.unbosque.marketplaceInversionesLR.entity.Usuario;
import co.edu.unbosque.marketplaceInversionesLR.repository.ConfiguracionTarifaRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.PoliticaCancelacionRepository;
import co.edu.unbosque.marketplaceInversionesLR.repository.UsuarioRepository;
import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;

/**
 * Datos base para la demostración (SCRUM-206 / T18): las políticas de
 * cancelación, la configuración de tarifas y un usuario por rol. Solo actúa
 * con {@code DEMO_POBLAR=true}.
 * <p>
 * Solo agrega lo que falta: una política se reconoce por su nombre, un usuario
 * por su correo y la configuración de tarifas se crea si no hay ninguna. Nunca
 * modifica ni borra datos existentes, así que se puede dejar activo entre
 * reinicios. El correo se cifra con {@code AES_SECRET_KEY} igual que en el
 * registro, por lo que los usuarios sirven con la clave de cualquier ambiente.
 * Los alojamientos de la demostración se cargan aparte, por la API, con
 * {@code demo/scripts/cargar_alojamientos.py}.
 * </p>
 */
@Component
public class DatosDemoInicializador {

	/** Log de la clase. */
	private static final Logger LOG = LoggerFactory.getLogger(DatosDemoInicializador.class);

	/**
	 * Usuario de la demostración.
	 *
	 * @param nombre   nombre completo
	 * @param correo   correo de inicio de sesión
	 * @param telefono teléfono, o {@code null}
	 * @param rol      rol
	 */
	record UsuarioDemo(String nombre, String correo, String telefono, Role rol) {
	}

	/** Usuarios de la demostración: al menos uno por rol. */
	static final List<UsuarioDemo> USUARIOS = List.of(
			new UsuarioDemo("Andrés Castillo", "andrescastillo@gmail.com", null, Role.ADMIN),
			new UsuarioDemo("Laura Gómez", "lauragomez@gmail.com", "3001112233", Role.ANFITRION),
			new UsuarioDemo("Carlos Ruiz", "carlosruiz@gmail.com", "3002223344", Role.ANFITRION),
			new UsuarioDemo("Ana Torres", "anatorres@gmail.com", "3003334455", Role.HUESPED),
			new UsuarioDemo("Diego Mora", "diegomora@gmail.com", "3004445566", Role.HUESPED),
			new UsuarioDemo("Sofía Pérez", "sofiaperez@gmail.com", "3005556677", Role.HUESPED),
			new UsuarioDemo("Pedro Díaz", "pedrodiaz@gmail.com", "3006667788", Role.HUESPED),
			new UsuarioDemo("Camila Herrera", "camilaherrera@gmail.com", "3007778899", Role.HUESPED));

	/** Porcentaje de limpieza de la configuración inicial de tarifas. */
	static final BigDecimal LIMPIEZA = new BigDecimal("5.00");

	/** Porcentaje de servicio de la configuración inicial de tarifas. */
	static final BigDecimal SERVICIO = new BigDecimal("10.00");

	/** Si se cargan los datos al arrancar ({@code app.demo.poblar}). */
	private final boolean habilitado;

	/** Contraseña de los usuarios de la demostración ({@code app.demo.contrasenia}). */
	private final String contrasenia;

	/** Repositorio de usuarios. */
	private final UsuarioRepository usuarioRepo;

	/** Repositorio de políticas de cancelación. */
	private final PoliticaCancelacionRepository politicaRepo;

	/** Repositorio de configuraciones de tarifa. */
	private final ConfiguracionTarifaRepository tarifaRepo;

	/** Codificador de contraseñas (BCrypt). */
	private final PasswordEncoder passwordEncoder;

	/**
	 * Crea el inicializador.
	 *
	 * @param habilitado      valor de {@code app.demo.poblar}
	 * @param contrasenia     contraseña de los usuarios de la demostración
	 * @param usuarioRepo     repositorio de usuarios
	 * @param politicaRepo    repositorio de políticas
	 * @param tarifaRepo      repositorio de configuraciones de tarifa
	 * @param passwordEncoder codificador de contraseñas (BCrypt)
	 */
	public DatosDemoInicializador(@Value("${app.demo.poblar:false}") boolean habilitado,
			@Value("${app.demo.contrasenia:Demo#2026}") String contrasenia, UsuarioRepository usuarioRepo,
			PoliticaCancelacionRepository politicaRepo, ConfiguracionTarifaRepository tarifaRepo,
			PasswordEncoder passwordEncoder) {
		this.habilitado = habilitado;
		this.contrasenia = contrasenia;
		this.usuarioRepo = usuarioRepo;
		this.politicaRepo = politicaRepo;
		this.tarifaRepo = tarifaRepo;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * Al terminar de arrancar, agrega los datos que falten si
	 * {@code DEMO_POBLAR=true}.
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void alArrancar() {
		if (!habilitado) {
			return;
		}
		int politicas = crearPoliticas();
		int usuarios = crearUsuarios();
		boolean tarifa = crearTarifa();
		LOG.info("Datos de demostración: {} políticas, {} usuarios y {} configuración de tarifas nuevos", politicas,
				usuarios, tarifa ? 1 : 0);
	}

	/**
	 * Crea las políticas FLEXIBLE, MODERADA y ESTRICTA que no existan.
	 *
	 * @return cantidad de políticas creadas
	 */
	int crearPoliticas() {
		int creadas = 0;
		for (PoliticaCancelacion politica : List.of(
				politica("FLEXIBLE", "Devolución del 100 % si cancelas con al menos 1 día de anticipación.", 1, null,
						null),
				politica("MODERADA",
						"Devolución del 100 % con 5 o más días de anticipación y del 50 % con 1 a 4 días.", 5, 1, 50),
				politica("ESTRICTA",
						"Devolución del 50 % con 7 o más días de anticipación; después no hay devolución.", null, 7,
						50))) {
			if (politicaRepo.findByNombre(politica.getNombre()).isEmpty()) {
				politicaRepo.save(politica);
				creadas++;
			}
		}
		return creadas;
	}

	/**
	 * Crea los usuarios de {@link #USUARIOS} cuyo correo no esté registrado,
	 * activos y con el correo verificado.
	 *
	 * @return cantidad de usuarios creados
	 */
	int crearUsuarios() {
		int creados = 0;
		for (UsuarioDemo demo : USUARIOS) {
			String correo = demo.correo().trim().toLowerCase();
			String correoHash = AESUtil.hash(correo);
			if (usuarioRepo.existsByCorreoHash(correoHash)) {
				continue;
			}
			Usuario usuario = new Usuario();
			usuario.setNombre(demo.nombre());
			usuario.setCorreo(AESUtil.encrypt(correo));
			usuario.setCorreoHash(correoHash);
			usuario.setTelefono(demo.telefono());
			usuario.setContrasenia(passwordEncoder.encode(contrasenia));
			usuario.setRol(demo.rol());
			usuario.setEstado(EstadoUsuario.ACTIVO);
			usuario.setFechaRegistro(LocalDateTime.now());
			usuario.setCorreoVerificado(true);
			usuario.setTotpHabilitado(false);
			usuarioRepo.save(usuario);
			creados++;
		}
		return creados;
	}

	/**
	 * Crea la configuración de tarifas (5 % de limpieza y 10 % de servicio) si
	 * no hay ninguna, a nombre del administrador de la demostración.
	 *
	 * @return {@code true} si la creó
	 */
	boolean crearTarifa() {
		if (tarifaRepo.count() > 0) {
			return false;
		}
		String correoAdmin = USUARIOS.get(0).correo();
		return usuarioRepo.findByCorreoHash(AESUtil.hash(correoAdmin)).map(admin -> {
			ConfiguracionTarifa tarifa = new ConfiguracionTarifa();
			tarifa.setPorcentajeLimpieza(LIMPIEZA);
			tarifa.setPorcentajeServicio(SERVICIO);
			tarifa.setIdUsuario(admin.getIdUsuario());
			tarifa.setFecha(LocalDateTime.now());
			tarifaRepo.save(tarifa);
			return true;
		}).orElse(false);
	}

	/**
	 * Arma una política de cancelación.
	 *
	 * @param nombre             nombre único
	 * @param condiciones        texto para el huésped
	 * @param diasTotal          días mínimos para el reembolso total, o
	 *                           {@code null}
	 * @param diasParcial        días mínimos para el reembolso parcial, o
	 *                           {@code null}
	 * @param porcentajeParcial  porcentaje del reembolso parcial, o {@code null}
	 * @return la política sin guardar
	 */
	private static PoliticaCancelacion politica(String nombre, String condiciones, Integer diasTotal,
			Integer diasParcial, Integer porcentajeParcial) {
		PoliticaCancelacion politica = new PoliticaCancelacion();
		politica.setNombre(nombre);
		politica.setCondiciones(condiciones);
		politica.setDiasReembolsoTotal(diasTotal);
		politica.setDiasReembolsoParcial(diasParcial);
		politica.setPorcentajeParcial(porcentajeParcial);
		return politica;
	}
}
