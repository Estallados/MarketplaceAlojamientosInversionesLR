# SCRUM-289 · Verificar la seguridad de la base de datos mediante inyección SQL

## Resultado

**No se encontraron vulnerabilidades de inyección SQL.** Se ejecutaron 93 pruebas contra el backend real:
86 seguras, 0 vulnerables y 7 informativas (líneas base y restauración de datos). Ninguna petición fue cortada por
el limitador de intentos, así que todas las cargas llegaron a la aplicación.

| Criterio de hecho | Evidencia | Estado |
|---|---|---|
| Pruebas en el inicio de sesión, búsquedas, filtros y demás endpoints con entradas del usuario | `sqli_test.py`, secciones 1 a 7: login, catálogo y sus filtros, parámetros de ruta, rutas de administrador, cuerpos JSON y texto guardado | Cumple |
| Comprobar si se puede acceder, consultar, modificar o eliminar información | Comparación de resultados (un filtro inyectado nunca devuelve más que el legítimo), evasión del login, `pg_sleep` para consultas apiladas e integridad de los conteos antes y después | Cumple |
| Verificar la protección de las consultas JPA y parametrizadas | Revisión del código (sección «Revisión del código») | Cumple |
| Identificar y clasificar las vulnerabilidades por riesgo | `matriz-vulnerabilidades.md` | Cumple: 0 vulnerabilidades |
| Corregir y repetir las pruebas | No hubo vulnerabilidades que corregir. La batería se ejecutó dos veces; la segunda corrigió dos datos de la propia prueba (ver «Ejecución») | No aplica |
| Documentar entradas, respuestas y evidencias | `resultados_sqli.json` (cada carga con su respuesta) y `salida-consola.txt` | Cumple |

## Ambiente

- **Backend:** local (`http://localhost:8081`), rama `main` en `a2af434`, conectado a la base de datos PostgreSQL de Neon.
- **Datos:** los de la demostración (SCRUM-206): 13 publicaciones activas y 11 usuarios.
- **Fecha:** 2026-11-16 (hora de Colombia).

## Qué se probó

| # | Categoría | Pruebas | Resultado |
|---|---|---|---|
| 0 | Líneas base: catálogo completo y filtrado, usuarios, auditoría y login fallido | 6 | Informativas |
| 1 | Evasión del login: 10 cargas en el campo correo y 10 en la contraseña de cuentas reales | 20 | 20 seguras: siempre 401, nunca un token |
| 2 | Búsqueda pública por diferencia de resultados: `ciudad`, `tipo`, `amenidades`, `orden`, `/disponibles` y `/filtros` | 22 | 22 seguras |
| 3 | Parámetros de ruta numéricos: `/alojamientos/{id}`, `/alojamientos/{id}/resenas`, `/reservas/{id}` y `/admin/auditoria/{id}` | 20 | 20 seguras: 400 antes de consultar |
| 4 | Rutas de administrador que reciben texto: auditoría por acción, usuario por correo, reportes y filtros por estado | 12 | 12 seguras |
| 5 | Campos JSON numéricos y de fecha con texto (`POST /cotizaciones`) | 2 | 2 seguras: 400 |
| 6 | Segundo orden: se guarda `Prueba'); SELECT pg_sleep(5);--` como nombre del perfil y se vuelve a leer | 2 | Segura: se guarda y se lee literal; luego se restauró el nombre original |
| 7 | Ciega por tiempo con `pg_sleep(5)` en login, búsqueda, auditoría y correo | 7 | 7 seguras: la más lenta tardó 0,47 s |
| 8 | Integridad: publicaciones y usuarios antes y después | 2 | Sin cambios: 13 y 11 |

**Cómo se decide cada veredicto:**

- **Login:** es vulnerable si responde 200 con un token.
- **Búsquedas:** es vulnerable si el filtro inyectado devuelve más resultados que el legítimo. Por ejemplo,
  `ciudad=Bogotá` devuelve 10 publicaciones; si `Bogotá' OR '1'='1` se ejecutara como SQL devolvería las 13, pero
  devuelve 0.
- **Por tiempo:** es vulnerable si la respuesta tarda 4,5 s o más.
- **Basada en errores:** en cualquier prueba, es vulnerable si la respuesta deja ver detalles de SQL (`SQLState`,
  `PSQLException`, `org.hibernate`, `syntax error`…). Ninguna los mostró.

**Cargas destructivas:** a diferencia del script del proyecto base, no se usaron `DROP`, `DELETE` ni `UPDATE`
inyectados, porque la base de datos es la compartida del equipo. `pg_sleep` demuestra igual si una consulta apilada
se ejecuta, sin riesgo para los datos.

## Revisión del código

Ninguna consulta arma SQL con texto que envía el usuario:

- No hay `nativeQuery`, `createNativeQuery`, `JdbcTemplate` ni `Statement` en el código de la aplicación.
- Las consultas `@Query` son JPQL con parámetros con nombre (`:id`, `:accion`…). Las que concatenan texto solo usan
  constantes del código (`ReservaRepository.OCUPA`, `ResenaRepository.DE_ANFITRION`).
- La búsqueda usa `Specification` y Criteria API (`AlojamientoEspecificacion`, `AlojamientoFiltrosRepositoryImpl`):
  los valores viajan como parámetros con `cb.equal` y `cb.isMember`, no se usa `LIKE`.
- El orden del catálogo solo acepta cuatro valores fijos, y los demás órdenes son constantes `Sort.by(...)`.
- El correo nunca llega como texto a la base de datos: se normaliza y se convierte en un hash (`UsuarioService.hashCorreo`)
  antes de buscar. La contraseña se compara con BCrypt en Java, sin consulta.
- Los ids, las fechas, los números y los estados se convierten a `Long`, `LocalDate`, `Integer` o a un enum antes de
  llegar al servicio. Un valor inválido responde 400 y no se consulta la base de datos.

## Ejecución

```bash
cd docs/evidencias/SCRUM-289
export SQLI_ADMIN=<correo del administrador semilla> SQLI_CUENTAS=<correos semilla separados por comas> SQLI_CLAVE=<contraseña semilla>
python sqli_test.py http://localhost:8081
```

Las credenciales se leen de variables de entorno y no quedan en los resultados. Las cuentas de la contraseña se
identifican como «cuenta semilla N».

El script reparte los intentos de contraseña entre varias cuentas y espera 2,2 s entre inicios de sesión, porque el
`LimitadorIntentosLogin` permite 5 fallos por cuenta cada 15 minutos y 30 por IP cada minuto. Si un intento recibe 429,
se marca como `BLOQUEADO` y no cuenta como seguro.

La primera corrida se repitió por dos errores de la propia prueba, no del backend: el tipo de referencia
(«Habitación doble») no existe, así que el filtro legítimo daba 0, y los reportes están en `/admin/reportes/{tipo}`,
no en `/reportes/{tipo}`. La segunda corrida, que es la que queda en los archivos, usa el tipo «Hotel» (11
publicaciones) y la ruta correcta.

Los inicios de sesión fallidos de la batería quedaron registrados en la auditoría como `LOGIN` `RECHAZADO`, tal como
exige HU-26.

## Archivos

| Archivo | Contenido |
|---|---|
| `sqli_test.py` | Batería de pruebas (solo librería estándar de Python), adaptada de `SoftwareFake/marketplace-backend/docs/seguridad/sqli_test.py` |
| `resultados_sqli.json` | Cada prueba con su carga, respuesta resumida (mensaje o total de resultados), tiempo y veredicto |
| `salida-consola.txt` | Salida de la ejecución |
| `matriz-vulnerabilidades.md` | Clasificación por riesgo de cada superficie probada |

## Matriz de trazabilidad

No aplica. Ningún criterio de aceptación de la matriz trata la inyección SQL; esta es una verificación técnica sin CA
propio, como SCRUM-206.
