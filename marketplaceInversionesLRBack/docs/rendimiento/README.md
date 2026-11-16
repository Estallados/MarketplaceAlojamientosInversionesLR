# Pruebas de carga y estrés del backend

Cómo funcionan las pruebas de rendimiento del backend, qué hace cada una, cómo correrlas y cómo leer lo que producen. Cada ejecución guarda sus resultados en `resultados/`.

## Qué son

Son pruebas que simulan a muchos usuarios usando el marketplace a la vez para responder:

- ¿Cuántos usuarios y peticiones por segundo aguanta el backend sin ponerse lento?
- ¿En qué punto empieza a fallar, y por qué?
- ¿Se recupera solo cuando baja el tráfico?
- ¿Las reservas siguen siendo correctas cuando muchos reservan a la vez?

**Corren siempre en local**, nunca contra producción. El backend se levanta en tu máquina con una base de datos de prueba en memoria y todos los servicios externos apagados (Neon, Brevo, Stripe, Cloudinary, Nominatim y Google). El generador de tráfico se niega a apuntar a cualquier cosa que no sea `localhost`.

## Las piezas

```text
ejecutar.sh ──► entorno.sh ──► H2 (base en memoria, puerto 9092)
     │                   └───► backend Spring Boot (puerto 8081)
     │                   └───► SembrarDatos.java (llena la base y crea los 
     │
     └──► carga.mjs ──► peticiones HTTP al backend ──► resultados/<escenario>.json
```

| Archivo | Qué hace |
|---|---|
| `ejecutar.sh` | El punto de entrada. Corre uno o varios escenarios, cada uno desde cero. |
| `entorno.sh` | Compila el backend, levanta H2 y el backend, siembra los datos y los apaga |
| `SembrarDatos.java` | Llena la base con datos de prueba y genera el token de cada usuario virtual |
| `carga.mjs` | El generador de tráfico: lanza las peticiones, mide los tiempos y guarda los resultados |
| `consultas.mjs` | Cuenta cuántas consultas SQL hace cada endpoint |
| `verificar_esquema.sql` | Consultas de solo lectura para revisar en Neon las restricciones, los índices y los planes de las consultas más usadas |
| `resultados/` | Un JSON por escenario |

Ninguna pieza necesita instalar nada: usan el JDK, Node 22 y las dependencias de Maven que ya están en `~/.m2`.

## Qué pasa cuando corres un escenario

`./ejecutar.sh estres` hace, en orden:

1. **Compila** el backend la primera vez, fuera de `target/`, para no tocar tu compilación. Las siguientes veces reutiliza lo compilado.
2. **Apaga** cualquier entorno de prueba anterior.
3. **Levanta H2** en memoria y **arranca el backend** contra ella. Hibernate crea las tablas a partir de las entidades.
4. **Siembra los datos** de prueba (abajo).
5. **Valida con un solo usuario** que todo funciona: 27 comprobaciones, desde buscar y ver un detalle hasta reservar, cancelar y repetir la cancelación. **Si alguna falla, el escenario no se ejecuta**: no tiene sentido medir el rendimiento de algo que no funciona.
6. **Calienta** la JVM con 5 usuarios durante 30 s, para que lo medido no sea el arranque en frío.
7. **Ejecuta el escenario** y muestra una línea por etapa.
8. **Guarda** las métricas, la telemetría, los errores del log y, en concurrencia, la comprobación en la base, todo en `resultados/<escenario>.json`.
9. Pasa al siguiente escenario, si pediste varios, y al final **apaga todo**.

Cada escenario empieza con la base recién creada, así que un escenario no influye en el siguiente.

## Los datos de prueba

`SembrarDatos.java` crea siempre el mismo volumen, con semillas fijas:

| Dato | Cantidad |
|---|---|
| Administrador | 1 (`admin@carga.local`, contraseña `Carga#2026`) |
| Anfitriones | 200 |
| Huéspedes | 2.000 |
| Alojamientos | 5.000 (4.000 activos), en 10 ciudades y 5 tipos, con 3 amenidades cada uno y disponibles de hace 30 días a dentro de 2 años |
| Políticas de cancelación | 3 (Flexible, Moderada y Estricta) |
| Reservas pasadas con su pago | 30.000 |
| Reseñas | 10.000 |
| Notificaciones | 40.000 |
| Eventos de auditoría | 100.000 |

Todos los usuarios tienen la contraseña `Carga#2026` y ningún dato es real. El sembrado se niega a escribir en una base que no sea H2 local.

**Por qué los tokens se generan en el sembrado.** Iniciar sesión cuesta ~70 ms de CPU por BCrypt. Hacer login con 2.200 usuarios antes de cada prueba tardaría y calentaría la máquina, así que el sembrado firma el JWT de cada usuario con la misma clave y formato que el backend. El login real se mide aparte, en los escenarios `login` y `login_fallido`.

## Qué tráfico se simula

Cada petición del generador elige una acción al azar, con estos pesos relativos, que imitan un uso real del marketplace:

| Peso | Acción | Petición (mezcla `frontend`) |
|---|---|---|
| 22 | Buscar por ciudad | `GET /alojamientos?ciudad=…&tamanio=9` |
| 13 | Buscar por ciudad y fechas | `GET /alojamientos?ciudad=…&llegada=…&salida=…&huespedes=2&tamanio=9` |
| 5 | Filtrar el catálogo | `GET /alojamientos/disponibles?tipo=…&precioMinimo=…&precioMaximo=…&amenidades=wifi&tamanio=9` |
| 15 | Ver el catálogo | `GET /alojamientos/disponibles?tamanio=9` |
| 10 | Opciones de los filtros de Explorar (solo en `frontend`) | `GET /alojamientos/filtros` |
| 18 | Ver un alojamiento | `GET /alojamientos/{id}` |
| 7 | Ver su calendario | `GET /alojamientos/{id}/disponibilidad` |
| 5 | Ver sus reseñas | `GET /alojamientos/{id}/resenas` |
| 3 | Contar alojamientos por tipo | `GET /alojamientos/tipos` |
| 3 | Ver el perfil | `GET /usuarios/perfil` |
| 3 | Contador de notificaciones | `GET /notificaciones/noleidas` |
| 3 | Mis reservas | `GET /reservas/mias` |
| 2 | Cotizar | `POST /cotizaciones` |
| 1 | Cotizar y reservar | `POST /cotizaciones` + `POST /reservas` |

Hay dos versiones de esta mezcla, que se eligen con la variable `MEZCLA`:

- **`frontend`** (por defecto): pide lo mismo que el frontend: Explorar carga las opciones de los filtros y páginas de 9 con los filtros resueltos en el servidor, y Mis reservas pide `tamanio=100`.
- **`paginada`**: el mismo recorrido con páginas de 12 y 20, ordenadas en el servidor y sin la consulta de opciones de filtro. Sirve para comparar con mediciones anteriores.

Cada reserva usa fechas y alojamientos distintos, para medir el camino normal y no choques de fechas. Los choques se prueban aparte, en `concurrencia`.

## Dos formas de generar tráfico

- **Modelo cerrado (usuarios virtuales).** Hay N usuarios. Cada uno hace una acción, espera la respuesta, piensa entre 1 y 3 s y repite. Si el servidor se pone lento, los usuarios piden menos. Se parece a usuarios reales navegando y se usa en `carga`.
- **Modelo abierto (tasa fija).** Llegan X peticiones por segundo pase lo que pase, aunque el servidor tarde. Si el servidor no da abasto, las peticiones se acumulan, y por eso este modelo encuentra el punto de quiebre. Se usa en `estres`, `pico`, `resistencia`, `login` y `login_fallido`.

En ambos, una petición que tarda más de **10 s** se cuenta como `TIMEOUT`. Si hay más de 3.000 peticiones esperando a la vez, las nuevas se **descartan** (`desc`) para no tumbar el generador.

## Los escenarios

| Escenario | Qué hace | Qué pregunta responde | Duración |
|---|---|---|---|
| `validacion` | 1 usuario recorre todas las acciones y 13 reglas (404, 401, 409, reservar sin `Idempotency-Key`, cancelar, repetir la cancelación…) | ¿Funciona todo antes de cargarlo? | ~5 s |
| `humo` | 5 usuarios durante 30 s | Calentamiento; lo usa `ejecutar.sh` | 30 s |
| `carga` | 50, luego 200 y luego 400 usuarios virtuales; cada nivel sube en 30 s y se mantiene 3 min | ¿Cómo responde con un número normal, alto y muy alto de usuarios? | 13 min |
| `estres` | Sube la tasa por escalones de 60 s (10, 25, 50, 75, 100 … rps) hasta que se rompe; después, 2 min a 25 rps | ¿Dónde está el límite y se recupera solo? | 10–20 min |
| `pico` | 1 min a 25 rps, **salto a 400 rps durante 30 s**, y vuelta a 25 rps | ¿Qué pasa con una avalancha repentina y cuánto tarda en recuperarse? | ~5–7 min |
| `resistencia` | Una tasa fija durante 15 min, por defecto de golpe tras el arranque. Con `RESISTENCIA_RAMPA=1`, sube primero en 4 min. | ¿Se degrada con el tiempo? ¿Fugas de memoria o conexiones? | 15–19 min |
| `login` | Solo inicios de sesión, de 5 a 240 por segundo | ¿Cuántos logins por segundo aguanta? | ~4 min |
| `login_fallido` | 100 lecturas/s normales y, encima, 50, 100 y 200 intentos con contraseña incorrecta. Los intentos que frena el límite de fallos responden 429. | ¿Un ataque de fuerza bruta afecta a los demás usuarios? | ~2 min |
| `concurrencia` | 3 pruebas de choque: 100 huéspedes reservan las mismas fechas a la vez; la misma cotización se envía 30 veces a la vez; la misma cancelación con la misma `Idempotency-Key` se envía 30 veces a la vez. Al final revisa la base. | ¿Puede haber dobles reservas, cobros o cancelaciones duplicadas? | ~1 min |

Variables útiles:

| Variable | Escenario | Para qué |
|---|---|---|
| `MEZCLA=paginada` | Todos | Usar páginas pequeñas con filtros en el servidor |
| `ESCALONES=10,50,100` | `estres` | Elegir los escalones |
| `RECUPERACION_RPS` | `estres` | Tasa de la recuperación (por defecto 25) |
| `BASE_RPS`, `PICO_RPS`, `PICO_MIN_DESPUES` | `pico` | Tasa base, tasa del pico y minutos de recuperación |
| `RESISTENCIA_RPS`, `RESISTENCIA_MIN`, `RESISTENCIA_RAMPA=1` | `resistencia` | Tasa, minutos (múltiplo de 5) y subida gradual |
| `CARGA_POOL`, `CARGA_OSIV`, `CARGA_XMX` | Todos | Conexiones del pool (10), `open-in-view` (true) y heap del backend (1g) |
| `SUFIJO=_algo` | Todos | Guardar en `resultados/<escenario>_algo.json` sin sobrescribir |
| `SALUD_MAX_FALLOS` | Todos | Cuántas comprobaciones de salud fallidas seguidas (cada 5 s) antes de abortar (por defecto 6 = 30 s) |

## Protecciones

- **Solo `localhost`.** Cualquier otro destino da error antes de enviar nada.
- **Validación previa.** Si falla, el escenario no corre.
- **Corte por indisponibilidad.** Si `/actuator/health` no responde durante 30 s, la prueba se aborta. En `estres` y `pico`, donde romper el sistema es justo el objetivo, se corta la subida pero se mide igual la recuperación.
- **Corte por errores.** `estres` deja de subir al pasar del 20 % de errores. `carga` no pasa a la siguiente etapa si una supera el 50 %.
- **Ctrl+C** detiene la prueba al instante, guarda lo medido y apaga el entorno.
- **Puertos.** Si 8081 o 9092 están ocupados, no arranca.

## Qué se mide

Por cada etapa, y por cada acción dentro de ella:

| Métrica | Significado |
|---|---|
| `rps` | Respuestas por segundo recibidas (throughput) |
| `media` | Tiempo medio de respuesta, en ms |
| `p50` | Mediana: la mitad de las peticiones tarda menos que esto |
| `p90`, `p95`, `p99` | El 90, 95 y 99 % de las peticiones tarda menos que esto |
| `max` | La petición más lenta |
| `errPct` | Porcentaje de peticiones fallidas: código inesperado, timeout o cuerpo incorrecto |
| `estados` | Cuántas respuestas hubo de cada código (`200`, `409`, `TIMEOUT`, `CUERPO_INVALIDO`…) |
| `descartadas` | Peticiones que el generador no lanzó porque ya había 3.000 en espera |

El generador también comprueba el **contenido** de las respuestas: un 200 con un cuerpo que no corresponde, por ejemplo una búsqueda sin la lista `contenido`, cuenta como error `CUERPO_INVALIDO`.

Cada 5 s se guarda además la **telemetría** del backend:

| Campo | Significado | Señal de problema |
|---|---|---|
| `hilos` | Hilos de Tomcat ocupados (máximo 200) | Llega a 200: todas las peticiones hacen cola |
| `hikPend` | Peticiones esperando una conexión a la base | Sube a decenas o cientos |
| `hikAct` | Conexiones en uso (máximo 10 por defecto) | Siempre al máximo |
| `cpuBackend`, `cpuH2` | CPU del backend y de la base (100 = un núcleo) | Su suma se acerca al total de la máquina |
| `heapMB`, `rssBackendMB` | Memoria usada por la JVM y por el proceso | Crece sin parar durante la resistencia |
| `gcPausaTotalS` | Segundos acumulados de pausas de recolección de basura | Crece de golpe |
| `salud`, `saludMs` | Respuesta de `/actuator/health` y su tiempo | `TIMEOUT` o segundos |
| `retrasoGeneradorMs` | Retraso del propio generador | Más de ~100 ms: la medición no es fiable |

## Cómo leer la salida

Mientras corre, la terminal imprime una línea por etapa:

```text
escalon_150  obj=150 rps rps=151.2  media=57.2   p50=73.1   p95=131.9   p99=153.9   err=0%   desc=0 {"200":8768,"201":317}
escalon_175  obj=175 rps rps=175.2  media=290.8  p50=271.6  p95=615.3   p99=919.6   err=0%   desc=0 {"200":10173,"201":426}
escalon_200  obj=200 rps rps=169.4  media=4826.2 p50=4802   p95=9632.9  p99=10000.8 err=2.4% desc=0 {"200":9664,"201":384,"TIMEOUT":243}
```

- **150 rps está bien:** atiende todo lo pedido (`rps` ≈ `obj`), p95 bajo y sin errores.
- **175 rps se degrada:** aún atiende todo, pero el p95 se dispara por encima de 500 ms.
- **200 rps colapsa:** atiende menos de lo pedido (169 de 200), el p95 roza los 10 s y aparecen `TIMEOUT`.

El criterio usado es **p95 < 500 ms y errores < 1 %**. Es una convención: el proyecto no define objetivos numéricos de rendimiento.

## Los archivos de resultados

Cada escenario deja un solo archivo, `resultados/<escenario><SUFIJO>.json`, con esta forma:

```text
{
  escenario, mezcla, base, fecha,
  abortado,          // motivo, si se cortó por indisponibilidad
  etapas: [          // una por etapa, con las métricas de arriba
    { nombre, objetivo, rps, media, p50, p90, p95, p99, errPct, estados,
      operaciones: { buscar_ciudad: {…}, detalle: {…}, … } }   // las mismas métricas por acción
  ],
  telemetria: [ … ],  // una muestra cada 5 s
  extra,              // datos propios del escenario (rondas de concurrencia, motivo del quiebre…)
  erroresLog,         // líneas ERROR del log del backend: total y las 6 más repetidas
  comprobacionSql     // solo en concurrencia: cruces de fechas, pagos y cancelaciones duplicadas
}
```

Para ver un resumen con la telemetría:

```bash
python3 - <<'E'
import json
d = json.load(open('resultados/estres.json'))
for e in d['etapas']:
    t = [x for x in d['telemetria'] if x.get('etapa') == e['nombre']]
    m = lambda k: max([x.get(k) or 0 for x in t] or [0])
    print(f"{e['nombre']:16} rps={e['rps']:<7} p95={e['p95']:<8} err={e['errPct']}%"
          f"  hilos={m('hilos')} en_cola={m('hikPend')} cpu_backend={m('cpuBackend')}% cpu_h2={m('cpuH2')}%")
E
```

Si `en_cola` se dispara y `hilos` llega a 200, las peticiones esperan conexión a la base. Si además `cpu_backend + cpu_h2` se acerca al total de la máquina (1.200 en un equipo de 12 núcleos), lo que se agota es la CPU.

## Cómo correrlas

**Requisitos:** JDK 17 o superior en `~/Prog/JDK` (o indícalo con `JAVA_HOME_PRUEBAS=/ruta`), Node 22 y las dependencias de Maven en `~/.m2`, incluido `com/h2database/h2/2.4.240`.

**Puertos:** el backend de prueba usa 8081 y H2 el 9092. Si tienes el backend de desarrollo corriendo en 8081, usa otros: `CARGA_PUERTO=8091 CARGA_PUERTO_H2=9093 ./ejecutar.sh estres`. Los scripts comprueban que el destino tenga los datos sembrados antes de enviar nada, así que nunca cargan un backend conectado a una base real.

**Antes de empezar:** cierra programas pesados (juegos, compilaciones, llamadas con video). Generador, backend y base comparten la CPU de tu máquina, y cualquier otro programa altera las mediciones.

Desde `marketplaceInversionesLRBack/docs/rendimiento`:

```bash
./ejecutar.sh concurrencia                    # ~2 min: ¿las reservas son seguras?
./ejecutar.sh estres                          # ~12 min: ¿dónde está el límite?
./ejecutar.sh carga                           # ~14 min: 50, 200 y 400 usuarios
./ejecutar.sh login login_fallido             # ~8 min
BASE_RPS=25 PICO_RPS=400 PICO_MIN_DESPUES=5 SALUD_MAX_FALLOS=72 ./ejecutar.sh pico
RESISTENCIA_RPS=110 RESISTENCIA_MIN=15 RESISTENCIA_RAMPA=1 SUFIJO=_con_rampa ./ejecutar.sh resistencia
MEZCLA=paginada SUFIJO=_mezcla_paginada ./ejecutar.sh estres   # páginas pequeñas, para comparar
CARGA_POOL=30 SUFIJO=_pool30 ./ejecutar.sh estres        # probar otra configuración
```

Contar las consultas SQL por endpoint:

```bash
export TRABAJO=${TMPDIR:-/tmp}/marketplace-alojamientos-carga
./entorno.sh iniciar --sql
node consultas.mjs "$TRABAJO/usuarios.json" "$TRABAJO/app-sql.log" resultados/consultas_por_endpoint.json
./entorno.sh detener
```

Levantar el entorno para probar a mano, sin generar carga:

```bash
./entorno.sh iniciar        # backend en http://localhost:8081 con los datos de prueba
./entorno.sh detener
```

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| «Los puertos 8081 o 9092 ya están en uso» | Otra prueba o el backend de desarrollo están corriendo. Detenlos o ejecuta `./entorno.sh detener`. |
| La prueba usa código viejo | Lo compilado se reutiliza. Borra `${TMPDIR:-/tmp}/marketplace-alojamientos-carga` para recompilar. |
| «La validación funcional falló» | Algo del backend no responde como debe. Revisa el detalle que imprime y `$TRABAJO/app.log`. |
| Latencias más altas de lo normal con poca carga | Otro programa usa CPU. Revisa con `top` y repite con la máquina libre. |
| `retrasoGeneradorMs` alto | El generador no da abasto, normalmente porque la máquina está saturada; esa medición no es fiable |
| «El login del administrador falló» | El sembrado no terminó. Revisa la salida de `./entorno.sh iniciar`. |

## Limitaciones que hay que tener en cuenta

- **H2 no es PostgreSQL**, y no hay latencia de red hacia la base. En producción (Neon), cada consulta paga la red, así que los números de aquí son un **techo optimista** y sirven sobre todo para comparar versiones y encontrar cuellos de botella.
- **Todo corre en la misma máquina**, así que el generador, el backend y la base compiten por la CPU.
- **Hay variación entre corridas.** El punto de quiebre puede moverse entre ejecuciones idénticas: léelo como un rango.
- **No se prueban** el correo real, Stripe real, la subida de imágenes ni WebSocket.
