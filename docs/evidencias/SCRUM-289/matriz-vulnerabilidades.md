# SCRUM-289 · Matriz de vulnerabilidades y resultados (inyección SQL)

**Riesgo:**

- **Alto:** permite iniciar sesión sin credenciales, leer datos de otros usuarios o modificar o borrar registros.
- **Medio:** permite inferir datos (por diferencia de resultados o por tiempo) o deja ver la estructura de la base de
  datos en los errores.
- **Bajo:** comportamiento inesperado sin acceso a datos.
- **Ninguno:** la entrada se trata como dato y no altera la consulta.

**Fecha de la prueba:** 2026-11-16, backend local en `main` (`a2af434`) conectado a Neon.

| ID | Superficie | Entrada | Técnicas probadas | Protección | Resultado | Riesgo |
|---|---|---|---|---|---|---|
| SQLI-01 | `POST /auth/login` | `correo` | Tautologías, comentarios, `UNION`, apilada, por tiempo | El correo se convierte en un hash antes de consultar | 401 en las 12 cargas, sin token y sin retraso | Ninguno |
| SQLI-02 | `POST /auth/login` | `contrasenia` | Tautologías, comentarios, `UNION`, apilada | Se compara con BCrypt en Java, sin consulta | 401 en las 10 cargas | Ninguno |
| SQLI-03 | `GET /alojamientos` | `ciudad`, `tipo` | Por diferencia de resultados, `UNION`, apilada, por tiempo | Criteria API con `cb.equal` | 0 resultados frente a 10 y 11 legítimos; sin retraso | Ninguno |
| SQLI-04 | `GET /alojamientos` | `amenidades` | Por diferencia de resultados, `UNION`, por tiempo | Criteria API con `cb.isMember` | 0 resultados, sin retraso | Ninguno |
| SQLI-05 | `GET /alojamientos` | `orden` | `ORDER BY` inyectado, subconsulta | Lista fija de cuatro valores | 400 | Ninguno |
| SQLI-06 | `GET /alojamientos/disponibles`, `GET /alojamientos/filtros` | `tipo`, `ciudad` | Por diferencia de resultados, `UNION` | Criteria API | 0 resultados o filtros vacíos | Ninguno |
| SQLI-07 | `/alojamientos/{id}`, `/alojamientos/{id}/resenas`, `/reservas/{id}`, `/admin/auditoria/{id}` | id en la ruta | Tautologías, apilada, `UNION`, paréntesis | Conversión a `Long` antes del controlador | 400 en las 20 cargas | Ninguno |
| SQLI-08 | `GET /admin/auditoria` | `accion` | Por diferencia de resultados, `UNION`, por tiempo | JPQL con parámetro `:accion` | 0 resultados frente a los eventos legítimos de `LOGIN`; sin retraso | Ninguno |
| SQLI-09 | `GET /usuarios/obtenercorreo` | `correo` | Tautologías, `UNION`, por tiempo | Hash del correo | 404, sin retraso | Ninguno |
| SQLI-10 | `GET /admin/reportes/{tipo}` | `tipo`, `formato` | Tautologías | Listas fijas de tipos y formatos | 400 | Ninguno |
| SQLI-11 | `GET /admin/alojamientos`, `/admin/reservas`, `/admin/resenas` | `estado` | Tautologías | Conversión a enum | 400 | Ninguno |
| SQLI-12 | `POST /cotizaciones` | Campos JSON numéricos y de fecha | Texto con SQL en `idAlojamiento`, `huespedes` y `fechaEntrada` | Conversión de tipos de Jackson | 400 | Ninguno |
| SQLI-13 | `PUT /usuarios/perfil` y luego `GET` | `nombre` guardado | Segundo orden con consulta apilada y `pg_sleep` | Persistencia de JPA con parámetros | Se guarda y se lee literal, sin retraso; se restauró el valor original | Ninguno |
| SQLI-14 | Toda la API | Mensajes de error | Basada en errores | `ManejadorGlobalExcepciones` responde mensajes genéricos | Ninguna respuesta dejó ver detalles de SQL | Ninguno |
| SQLI-15 | Base de datos | Conteos de publicaciones y usuarios | Integridad después de la batería | Todas las anteriores | 13 y 11, antes y después | Ninguno |

## Resumen

| Riesgo | Vulnerabilidades |
|---|---|
| Alto | 0 |
| Medio | 0 |
| Bajo | 0 |
| **Total** | **0** |

No hubo hallazgos que corregir, así que no hay cambios de código en esta tarea.

## Recomendaciones para mantener el resultado

- Mantener la regla actual: no usar `nativeQuery`, `JdbcTemplate` ni concatenar valores en `@Query`. Si alguna vez hace
  falta SQL nativo, usar parámetros con nombre, nunca `+`.
- Si se agrega un orden configurable, validarlo contra una lista fija, como hoy hace el catálogo.
- No verificado en esta prueba: los permisos del usuario de base de datos que usa el backend en Neon. Como defensa en
  profundidad, conviene que no sea el dueño del esquema y que no pueda borrar tablas.
