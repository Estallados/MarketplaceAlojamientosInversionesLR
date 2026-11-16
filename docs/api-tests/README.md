# Pruebas de la API: contrato OpenAPI y colección Postman/Newman (SCRUM-204 / T16)

Valida que el contrato `docs/openapi/openapi.yaml` coincida con la implementación del backend y ejecuta el flujo
E2E de la API con Newman en GitHub Actions.

## Criterios de T16

| Criterio | Cómo se cumple | Estado |
|---|---|---|
| Colección Postman del flujo E2E en `docs/api-tests/` | `marketplace-e2e.postman_collection.json`: 52 peticiones en 7 carpetas | Cumple |
| Ejecución automática con Newman en GitHub Actions | Job `contrato-y-newman` en `.github/workflows/api-tests.yml`, en cada PR a `main` y en cada push a `main` | Cumple |
| Diferencias entre contrato e implementación registradas | Tabla de abajo; `verificar_contrato.py` las detecta y el contrato quedó corregido | Cumple |

## Archivos

| Archivo | Contenido |
|---|---|
| `marketplace-e2e.postman_collection.json` | Colección del flujo E2E. Cada petición verifica el código esperado, que ese código esté documentado en `openapi.yaml` y que la respuesta sea JSON |
| `local.postman_environment.json` | Variables: `baseUrl`, contraseñas y correos de los usuarios de la demostración |
| `semilla-ci.sql` | Dos publicaciones de prueba de Laura Gómez: una completa en PENDIENTE_REVISION y un borrador incompleto |
| `verificar_contrato.py` | Compara los controladores (rutas, métodos y `@ApiResponse`) con `openapi.yaml` y falla si hay diferencias |

## Flujo de la colección

| Carpeta | Qué prueba |
|---|---|
| 1. Registro | Registro de anfitrión y huésped (201), correo duplicado (409), rol ADMIN no permitido (403), contraseña débil (400) y login sin verificar el correo (403) |
| 2.1 Acceso y roles | Login de los tres roles con los usuarios de la demostración, contraseña incorrecta (401), `/admin` sin token (401) y con rol huésped (403) |
| 2.2 Catálogo público | Políticas, tipos, filtros, configuración de pagos y búsqueda con fechas invertidas (400) |
| 2.3 Publicación y revisión | El anfitrión agrega un servicio adicional; el borrador incompleto no pasa a revisión (409); el administrador aprueba la publicación pendiente (ACTIVA) |
| 2.4 Disponibilidad, cotización y reserva | Búsqueda, detalle, calendario, cotización (3 noches), reserva sin `Idempotency-Key` (400), reserva y pago simulado (CONFIRMADA), repetición idempotente, fechas ocupadas, historial, simulación y cancelación |
| 2.5 Mensajería, notificaciones y favoritos | Conversación huésped–anfitrión, notificaciones y favoritos |
| 2.6 Administración y reportes | Dashboard, usuarios, reservas, auditoría, reporte PDF, tipo de reporte inválido (400) e historial de exportaciones |

Las publicaciones de prueba se insertan con `semilla-ci.sql` porque crearlas por la API exige subir imágenes a
Cloudinary, que no está disponible en el CI. El resto del flujo usa solo la API.

## Ejecución local

Con el backend corriendo en `http://localhost:8081` con `DEMO_POBLAR=true`:

```bash
# 1. Contrato contra los controladores
pip install pyyaml
python3 docs/api-tests/verificar_contrato.py

# 2. Datos de prueba (solo una vez por base de datos)
psql "<cadena de conexión>" -f docs/api-tests/semilla-ci.sql

# 3. Colección
npx newman@6.2.1 run docs/api-tests/marketplace-e2e.postman_collection.json \
  -e docs/api-tests/local.postman_environment.json
```

En Postman: Import → los dos archivos `.json` → seleccionar el entorno "Marketplace local" → Run collection.
La colección reserva y cancela; no la ejecutes contra producción.

## En el CI

El job `contrato-y-newman` levanta PostgreSQL 16, compila y arranca el backend con
`SPRING_JPA_HIBERNATE_DDL_AUTO=create` y `DEMO_POBLAR=true`, carga `semilla-ci.sql` y ejecuta la colección. Si
algo falla, el artefacto `resultados-newman` trae el reporte JUnit de Newman y el log del backend.

## Diferencias encontradas entre el contrato y la implementación

`verificar_contrato.py` contra `main` (commit `b26186a`) encontró 75 diferencias. Todas se corrigieron en
`openapi.yaml` en este PR y el script ya no reporta ninguna.

| Tipo | Cantidad | Ejemplo | Corrección |
|---|---|---|---|
| Operación del código que no estaba en el contrato | 1 | `GET /alojamientos/filtros` (`AlojamientoController`) | Se agregó con sus parámetros y su respuesta `{tipos, amenidades, precioMinimo, precioMaximo}` |
| Operación del contrato que ya no existe en el código | 0 | — | — |
| Códigos de respuesta declarados con `@ApiResponse` y no documentados | 61 operaciones | `POST /reservas` documentaba 200 y 400; el código responde 201, 402, 403, 404, 409, 422 y 502 | Se agregaron los códigos con la descripción del `@ApiResponse` |
| Rutas protegidas sin 401 | 13 operaciones | `GET /admin/dashboard` sin token responde 401 y solo documentaba 403 | Se agregó 401 |

Códigos agregados en total: 404 (50), 403 (31), 409 (24), 201 (13), 401 (16), 429 (3), 503 (3), 413 (2),
415 (2), 422 (2), 500 (2), 402 (1) y 502 (1).

Lo más importante para el frontend:

- Las creaciones responden **201**, no 200: registro, reserva, cotización, conversación, mensaje, servicio
  adicional, periodo, favorito nuevo, reseña e imágenes.
- `POST /reservas` y `POST /reservas/{id}/cancelar` exigen el encabezado `Idempotency-Key`; sin él responden 400.
- Las rutas de `/admin` responden 401 sin token y 403 con un rol distinto de ADMIN.
