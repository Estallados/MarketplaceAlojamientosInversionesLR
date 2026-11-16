import java.io.PrintWriter;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.unbosque.marketplaceInversionesLR.util.AESUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Llena la base H2 de pruebas con un volumen realista y genera los tokens de los usuarios virtuales.
 *
 *  - El backend no crea al arrancar ni el administrador ni las políticas de cancelación: se insertan aquí.
 *  - El esquema sale de las entidades (Hibernate ddl-auto=create), con sus UNIQUE.
 *  - El JWT se firma igual que JwtUtil (jjwt 0.12, HS256, claim "authorities").
 *  - Los pagos históricos usan el proveedor SIMULADO.
 * Los tokens se firman aquí para no gastar 2.200 logins BCrypt en la preparación; el login real se mide
 * en los escenarios login y login_fallido.
 * NUNCA debe apuntar a una base compartida: rechaza cualquier URL que no sea H2 local.
 *
 * Uso: java -cp <clases+dependencias> SembrarDatos.java <url-h2> <salida.json>
 */
public class SembrarDatos {
    static final int ANFITRIONES = 200, HUESPEDES = 2000, ALOJAMIENTOS = 5000, ACTIVOS = 4000, RESERVAS = 30000;
    static final String[] CIUDADES = { "Bogotá", "Medellín", "Cartagena", "Cali", "Santa Marta", "Manizales",
            "Barranquilla", "Bucaramanga", "Pereira", "Villa de Leyva" };
    static final String[] TIPOS = { "Apartamento", "Casa", "Cabaña", "Estudio", "Finca" };
    static final String[] AMENIDADES = { "wifi", "cocina", "piscina", "parqueadero", "terraza", "mascotas" };
    static final String CONTRASENIA = "Carga#2026";

    public static void main(String[] a) throws Exception {
        if (!a[0].startsWith("jdbc:h2:tcp://localhost")) {
            throw new IllegalArgumentException("Solo se permite una base H2 local");
        }
        // Un solo hash (costo 10, el de BCryptPasswordEncoder por defecto) para todas las cuentas de prueba.
        String hash = new BCryptPasswordEncoder().encode(CONTRASENIA);
        try (Connection c = DriverManager.getConnection(a[0], "sa", "")) {
            c.setAutoCommit(false);
            politicas(c);
            List<String> tokens = usuarios(c, hash);
            alojamientos(c);
            historico(c);
            c.commit();
            c.setAutoCommit(true);
            try (Statement s = c.createStatement()) {
                for (String[] t : new String[][] { { "usuario", "id_usuario", "10000" },
                        { "alojamiento", "id_alojamiento", "10000" }, { "cotizacion", "id_cotizacion", "100000" },
                        { "reserva", "id_reserva", "100000" }, { "pago", "id_pago", "100000" },
                        { "resena", "id_resena", "100000" }, { "notificacion", "id_notificacion", "100000" },
                        { "evento_auditoria", "id_evento", "200000" },
                        { "periodo_disponibilidad", "id_periodo", "10000" },
                        { "politica_cancelacion", "id_politica", "100" } }) {
                    s.execute("ALTER TABLE " + t[0] + " ALTER COLUMN " + t[1] + " RESTART WITH " + t[2]);
                }
                s.execute("ANALYZE");
            }
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(a[1])))) {
                w.println("[" + String.join(",\n", tokens) + "]");
            }
        }
        System.out.println("Datos sembrados: " + (ANFITRIONES + HUESPEDES + 1) + " usuarios, " + ALOJAMIENTOS
                + " alojamientos, " + RESERVAS + " reservas");
    }

    static void politicas(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("insert into politica_cancelacion (id_politica,nombre,condiciones,dias_reembolso_total,"
                    + "dias_reembolso_parcial,porcentaje_parcial) values "
                    + "(1,'Flexible','Reembolso total hasta 1 día antes.',1,null,null),"
                    + "(2,'Moderada','Total hasta 5 días antes; 50 % hasta 1 día antes.',5,1,50),"
                    + "(3,'Estricta','50 % hasta 7 días antes.',null,7,50)");
        }
    }

    static String token(String correo, String rol) {
        long ahora = System.currentTimeMillis();
        return Jwts.builder().subject(correo).claims(Map.of("authorities", List.of("ROLE_" + rol)))
                .issuedAt(new java.util.Date(ahora)).expiration(new java.util.Date(ahora + 12 * 3600_000L))
                .signWith(Keys.hmacShaKeyFor(AESUtil.derivarClave("jwt"))).compact();
    }

    static List<String> usuarios(Connection c, String hash) throws SQLException {
        List<String> salida = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("insert into usuario (id_usuario,nombre,correo,correo_hash,"
                + "rol,estado,contrasenia,totp_habilitado,fecha_registro,correo_verificado) "
                + "values (?,?,?,?,?,'ACTIVO',?,false,localtimestamp,true)")) {
            for (int i = 0; i <= ANFITRIONES + HUESPEDES; i++) {
                long id = 1000L + i;
                String rol = i == 0 ? "ADMIN" : i <= ANFITRIONES ? "ANFITRION" : "HUESPED";
                String correo = i == 0 ? "admin@carga.local"
                        : (i <= ANFITRIONES ? "anfitrion" : "huesped") + i + "@carga.local";
                p.setLong(1, id);
                p.setString(2, i == 0 ? "Administrador de carga" : "Usuario " + i);
                p.setString(3, AESUtil.encrypt(correo));
                p.setString(4, AESUtil.hash(correo));
                p.setString(5, rol);
                p.setString(6, hash);
                p.addBatch();
                if (i > 0) {
                    salida.add(String.format("{\"id\":%d,\"correo\":\"%s\",\"rol\":\"%s\",\"token\":\"%s\"}", id,
                            correo, rol, token(correo, rol)));
                }
            }
            p.executeBatch();
        }
        return salida;
    }

    static void alojamientos(Connection c) throws SQLException {
        Random rnd = new Random(42);
        try (PreparedStatement a = c.prepareStatement("insert into alojamiento (id_alojamiento,titulo,descripcion,"
                + "ubicacion,ciudad,latitud,longitud,tipo,capacidad,habitaciones,camas,banos,hora_entrada,hora_salida,"
                + "precio_noche,reglas,estado,id_anfitrion,id_politica,fecha_creacion,fecha_actualizacion) values "
                + "(?,?,?,?,?,?,?,?,?,2,2,1,time '15:00:00',time '11:00:00',?,'No fumar.',?,?,?,"
                + "dateadd(day,?,localtimestamp),localtimestamp)");
                PreparedStatement m = c.prepareStatement("insert into alojamiento_amenidad (id_alojamiento,amenidad) values (?,?)");
                PreparedStatement d = c.prepareStatement("insert into periodo_disponibilidad (id_periodo,"
                        + "id_alojamiento,fecha_inicio,fecha_fin) values (?,?,?,?)")) {
            LocalDate hoy = LocalDate.now();
            String[] estados = { "PAUSADA", "BORRADOR", "PENDIENTE_REVISION" };
            for (int i = 1; i <= ALOJAMIENTOS; i++) {
                a.setLong(1, i);
                a.setString(2, "Alojamiento de prueba " + i);
                a.setString(3, "Descripción de prueba del alojamiento " + i);
                a.setString(4, "Calle " + i);
                a.setString(5, CIUDADES[rnd.nextInt(CIUDADES.length)]);
                a.setDouble(6, 4 + rnd.nextDouble());
                a.setDouble(7, -74 - rnd.nextDouble());
                a.setString(8, TIPOS[rnd.nextInt(TIPOS.length)]);
                a.setInt(9, 2 + rnd.nextInt(7));
                a.setInt(10, 80000 + rnd.nextInt(600000));
                a.setString(11, i <= ACTIVOS ? "ACTIVA" : estados[i % 3]);
                a.setLong(12, 1001 + i % ANFITRIONES);
                a.setLong(13, 1 + i % 3);
                a.setInt(14, -rnd.nextInt(365));
                a.addBatch();
                int primera = rnd.nextInt(AMENIDADES.length);
                for (int k = 0; k < 3; k++) {
                    m.setLong(1, i);
                    m.setString(2, AMENIDADES[(primera + k) % AMENIDADES.length]);
                    m.addBatch();
                }
                d.setLong(1, i);
                d.setLong(2, i);
                d.setObject(3, hoy.minusDays(30));
                d.setObject(4, hoy.plusDays(730));
                d.addBatch();
            }
            a.executeBatch();
            m.executeBatch();
            d.executeBatch();
        }
    }

    static void historico(Connection c) throws SQLException {
        Random rnd = new Random(7);
        LocalDate hoy = LocalDate.now();
        try (PreparedStatement q = c.prepareStatement("insert into cotizacion (id_cotizacion,fecha_entrada,fecha_salida,"
                + "huespedes,noches,precio_noche,precio_base,descuento,porcentaje_limpieza,valor_limpieza,"
                + "porcentaje_servicio,valor_servicio,valor_adicionales,total,desglose,id_usuario,id_alojamiento,"
                + "id_reserva,fecha_creacion,fecha_expiracion) values (?,?,?,2,3,100000,300000,0,5,15000,10,30000,0,"
                + "345000,'[]',?,?,?,localtimestamp,localtimestamp)");
                PreparedStatement r = c.prepareStatement("insert into reserva (id_reserva,fecha_entrada,fecha_salida,"
                        + "noches,huespedes,estado,total,desglose,id_huesped,id_alojamiento,id_cotizacion,fecha_creacion)"
                        + " values (?,?,?,3,2,'CONFIRMADA',345000,'[]',?,?,?,localtimestamp)");
                PreparedStatement p = c.prepareStatement("insert into pago (id_pago,referencia,proveedor,valor,estado,"
                        + "fecha,metodo,id_reserva) values (?,?,'SIMULADO',345000,'APROBADO',localtimestamp,"
                        + "'pm_card_visa',?)");
                PreparedStatement s = c.prepareStatement("insert into resena (id_resena,calificacion,comentario,estado,"
                        + "fecha,id_usuario,id_reserva,id_alojamiento) values (?,?,'Muy bien','PUBLICADA',localtimestamp,?,?,?)");
                PreparedStatement n = c.prepareStatement("insert into notificacion (id_notificacion,tipo,mensaje,fecha,"
                        + "leida,id_usuario) values (?,'RESERVA','Mensaje de prueba',localtimestamp,?,?)");
                PreparedStatement e = c.prepareStatement("insert into evento_auditoria (id_evento,accion,fecha,detalle,"
                        + "id_usuario,entidad,id_entidad,resultado) values (?,'LOGIN',localtimestamp,null,?,'USUARIO',?,'EXITOSO')")) {
            for (int i = 1; i <= RESERVAS; i++) {
                int alojamiento = 1 + i % ACTIVOS;
                LocalDate entrada = hoy.minusDays(300 - (i / ACTIVOS) * 10L);
                long huesped = 1001 + ANFITRIONES + rnd.nextInt(HUESPEDES);
                q.setLong(1, i); q.setObject(2, entrada); q.setObject(3, entrada.plusDays(3));
                q.setLong(4, huesped); q.setLong(5, alojamiento); q.setLong(6, i); q.addBatch();
                r.setLong(1, i); r.setObject(2, entrada); r.setObject(3, entrada.plusDays(3));
                r.setLong(4, huesped); r.setLong(5, alojamiento); r.setLong(6, i); r.addBatch();
                p.setLong(1, i); p.setString(2, "sim_" + i); p.setLong(3, i); p.addBatch();
                if (i % 3 == 0) {
                    s.setLong(1, i); s.setInt(2, 1 + rnd.nextInt(5)); s.setLong(3, huesped); s.setLong(4, i);
                    s.setLong(5, alojamiento); s.addBatch();
                }
                if (i % 5000 == 0) {
                    q.executeBatch(); r.executeBatch(); p.executeBatch(); s.executeBatch();
                }
            }
            q.executeBatch(); r.executeBatch(); p.executeBatch(); s.executeBatch();
            for (int i = 1; i <= 40000; i++) {
                n.setLong(1, i); n.setBoolean(2, i % 4 != 0);
                n.setLong(3, 1001 + rnd.nextInt(ANFITRIONES + HUESPEDES)); n.addBatch();
                if (i % 5000 == 0) n.executeBatch();
            }
            n.executeBatch();
            for (int i = 1; i <= 100000; i++) {
                long u = 1001 + rnd.nextInt(ANFITRIONES + HUESPEDES);
                e.setLong(1, 1000L + i); e.setLong(2, u); e.setLong(3, u); e.addBatch();
                if (i % 5000 == 0) e.executeBatch();
            }
            e.executeBatch();
        }
    }
}
