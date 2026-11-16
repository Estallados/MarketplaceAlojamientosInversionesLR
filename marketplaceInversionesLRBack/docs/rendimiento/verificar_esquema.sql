-- Verificación del esquema de producción (Neon) para el rendimiento. SOLO LECTURA: todo corre dentro de una
-- transacción READ ONLY, así que no puede modificar nada aunque se ejecute completo por error.
--   psql "$DB_URL_PSQL" -f verificar_esquema.sql
-- Las exportaciones del DDL pierden las restricciones EXCLUDE y los WHERE de los índices parciales; estas
-- consultas muestran cómo están realmente en la base.

BEGIN TRANSACTION READ ONLY;

-- 1. Restricciones de exclusión que impiden reservas y períodos cruzados. El backend traduce la violación de
--    ex_reserva_sin_cruces (SQLSTATE 23P01) a un 409. Deben aparecer las dos con "EXCLUDE USING gist".
SELECT conrelid::regclass AS tabla, conname AS restriccion, pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE contype = 'x'
ORDER BY 1, 2;

-- 2. Definición completa de los índices que la exportación pudo recortar (expresiones y WHERE parciales).
SELECT tablename AS tabla, indexname AS indice, indexdef AS definicion
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('ex_periodo_sin_cruces', 'ex_reserva_sin_cruces', 'idx_alojamiento_ciudad', 'idx_alojamiento_tipo',
                    'idx_notificacion_pendientes', 'idx_reserva_pendientes', 'idx_resena_alojamiento_estado',
                    'idx_pago_reserva', 'idx_servicio_alojamiento', 'idx_idempotencia_fecha')
ORDER BY 1, 2;

-- 3. Índices que nunca se han usado desde el último reinicio de estadísticas (candidatos a revisar; no borrar
--    sin mirar antes cuánto tiempo llevan las estadísticas acumulándose).
SELECT s.relname AS tabla, s.indexrelname AS indice, s.idx_scan AS usos,
       pg_size_pretty(pg_relation_size(s.indexrelid)) AS tamanio
FROM pg_stat_user_indexes s
JOIN pg_index i ON i.indexrelid = s.indexrelid
WHERE NOT i.indisunique AND s.idx_scan = 0
ORDER BY pg_relation_size(s.indexrelid) DESC;

-- 4. Tamaño de las tablas que más crecen. registro_idempotencia ahora se limpia cada hora (registros de más
--    de 24 h); si aquí aparecen registros más antiguos, la limpieza no está corriendo.
SELECT relname AS tabla, n_live_tup AS filas, pg_size_pretty(pg_total_relation_size(relid)) AS tamanio_total
FROM pg_stat_user_tables
WHERE relname IN ('registro_idempotencia', 'evento_auditoria', 'cotizacion', 'notificacion', 'reserva', 'pago')
ORDER BY pg_total_relation_size(relid) DESC;

SELECT estado, count(*) AS registros, min(fecha_creacion) AS mas_antiguo
FROM registro_idempotencia
GROUP BY estado;

-- 5. Planes de las consultas en bloque con que el backend arma una página de alojamientos y una de reservas.
--    Deben usar los índices (Index Scan / Bitmap Index Scan), no Seq Scan sobre tablas grandes. EXPLAIN ANALYZE
--    de un SELECT ejecuta la consulta pero no modifica nada.
EXPLAIN (ANALYZE, BUFFERS)
SELECT r.id_alojamiento, avg(r.calificacion), count(r.id_resena)
FROM resena r
WHERE r.id_alojamiento IN (SELECT id_alojamiento FROM alojamiento WHERE estado = 'ACTIVA' ORDER BY fecha_creacion DESC LIMIT 100)
  AND r.estado = 'PUBLICADA'
GROUP BY r.id_alojamiento;

EXPLAIN (ANALYZE, BUFFERS)
SELECT s.*
FROM servicio_adicional s
WHERE s.id_alojamiento IN (SELECT id_alojamiento FROM alojamiento WHERE estado = 'ACTIVA' ORDER BY fecha_creacion DESC LIMIT 100)
ORDER BY s.id_servicio;

EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM pago p
WHERE p.id_reserva IN (SELECT id_reserva FROM reserva ORDER BY fecha_creacion DESC LIMIT 100);

-- 6. Plan del catálogo ordenado por recomendados (promedio y cantidad de reseñas de cada alojamiento activo).
--    Es un equivalente aproximado de la consulta que genera Hibernate con AlojamientoEspecificacion.
EXPLAIN (ANALYZE, BUFFERS)
SELECT a.id_alojamiento
FROM alojamiento a
WHERE a.estado = 'ACTIVA'
  AND EXISTS (SELECT 1 FROM periodo_disponibilidad p WHERE p.id_alojamiento = a.id_alojamiento AND p.fecha_fin >= current_date)
ORDER BY (SELECT count(*) FROM resena r WHERE r.id_alojamiento = a.id_alojamiento AND r.estado = 'PUBLICADA') DESC,
         (SELECT avg(r.calificacion) FROM resena r WHERE r.id_alojamiento = a.id_alojamiento AND r.estado = 'PUBLICADA') DESC NULLS LAST
LIMIT 100;

ROLLBACK;
