# SCRUM-208 · Verificar RNF de seguridad: roles, aislamiento, autenticación y contraseñas (CA-131, 133, 134, 135)

## Resultado por criterio

| CA | Qué exige | Cómo se prueba | Estado |
|----|-----------|----------------|--------|
| CA-131 Acceso según rol | Permitir o rechazar según el rol | **Back:** `SeguridadRutasTest` recorre los 84 endpoints registrados en Spring y los compara con una matriz de permisos explícita. Con cada rol sin permiso, la ruta responde 403; con un rol permitido, la seguridad la deja pasar. Si se agrega un endpoint sin registrarlo en la matriz, la prueba falla. **Front:** `rutas-protegidas.spec.ts` recorre `app.routes`: toda ruta no pública tiene guarda y, con un rol sin permiso, vuelve al inicio | Cumple |
| CA-133 Aislamiento | Un usuario no consulta ni modifica información privada de otro | `SeguridadRutasTest#unUsuarioNoConsultaOtraCuenta_CA133` y las pruebas de integración de perfil, estado de cuenta, favoritos, notificaciones, reservas y mensajería (ahora etiquetadas CA-133) | Cumple |
| CA-134 Exigir autenticación | Sin sesión válida no se ejecuta la operación | **Back:** cada endpoint protegido responde 401 sin token, con un token alterado y con el token de una cuenta que ya no existe. **Front:** sin sesión, cada ruta protegida manda al inicio de sesión | Cumple |
| CA-135 Contraseña mínima | Rechazar e informar qué condición no se cumple | **Back:** `ExceptionChekerTest` (cada regla), registro, creación de administrador y restablecimiento. **Front:** validador compartido `auth/validaciones/contrasena.ts` con las mismas reglas que el back, usado por el registro y la recuperación; el mensaje dice exactamente qué falta | Cumple |

Política de contraseña (back y front): entre 8 y 64 caracteres (máximo 72 bytes, límite de BCrypt), una
mayúscula, una minúscula, un número y un símbolo.

## Cambios

- **Back:** `security/SeguridadRutasTest` (293 casos). Usa la cadena de seguridad real (`SecurityConfig`,
  `JwtAuthenticationFilter` con tokens firmados y `@PreAuthorize`) con los servicios simulados, así que
  corre sin base de datos.
- **Front:**
  - el registro validaba con otra regla que el back: no limitaba a 64 caracteres y no contaba las
    mayúsculas con tilde. Ahora registro y recuperación usan el mismo validador;
  - el registro muestra bajo el campo qué le falta a la contraseña;
  - pruebas nuevas: `contrasena.spec.ts`, `register.spec.ts` (el registro no tenía pruebas) y
    `rutas-protegidas.spec.ts`.
- **Matriz de trazabilidad:** las filas de CA-131, 133, 134 y 135 citaban pruebas que ya no existen
  (`SeguridadRolesYCorsTest`, `AuthFlowTest`, `IdentidadPerfilTest`…); ahora citan las reales.

## Ejecución

| Suite | Resultado |
|-------|-----------|
| Back `mvn test` | 1023 pruebas, 0 fallos. Las 196 de integración (Testcontainers) se saltan sin Docker y corren en CI |
| `SeguridadRutasTest` | 293 casos, 0 fallos |
| Front `ng test` | 307 pruebas, 0 fallos |

Para comprobar que las pruebas detectan fallos reales, se quitó temporalmente el `@PreAuthorize` de
`POST /reservas` (falló: el administrador y el anfitrión podían reservar) y la guarda de `/favoritos`
en el front (fallaron 3 pruebas). Después se restauró el código.

## Matriz de permisos de la API

`PERMISOS` en `SeguridadRutasTest` es la matriz completa. Resumen:

| Acceso | Rutas |
|--------|-------|
| Pública | `/auth/**`, catálogo, detalle, disponibilidad y reseñas visibles de un alojamiento, `/pagos/configuracion` |
| Cualquier usuario con sesión | perfil, segundo factor, notificaciones, favoritos, mensajería, `POST /cotizaciones`, `GET /reservas/mias`, `GET /reservas/{id}`, historial de una publicación (cada servicio filtra lo que es del usuario: CA-133) |
| Huésped | crear, simular y cancelar reservas; crear y listar sus reseñas |
| Anfitrión | crear, enviar a revisión, pausar y reanudar publicaciones; sus alojamientos, reservas y reseñas |
| Anfitrión o administrador | editar publicación, fotos, servicios y períodos de disponibilidad; tarifas |
| Administrador | `/admin/**`, crear administradores, listar y buscar usuarios |
