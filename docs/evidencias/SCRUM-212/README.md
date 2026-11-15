# SCRUM-212 · Verificar RNF de usabilidad y diseño adaptable (CA-146, 147, 148, 149)

Revisión del front con las pantallas principales de los tres roles y las públicas, en dos tamaños:
**computador 1280 × 800** y **móvil 390 × 844** (30 pantallas, 60 capturas).

## Cómo se revisó

- Navegador Chromium automatizado (Playwright) contra `ng serve`.
- Pantallas públicas con el backend real (solo consultas GET).
- Pantallas con sesión (huésped, anfitrión y administrador) con una sesión simulada y respuestas de
  prueba para la API. No se escribió nada en la base de datos.
- En cada captura se midió si algún elemento se sale del ancho de la pantalla, y las capturas se
  revisaron una por una para encontrar lo que la medición no detecta (textos montados, cortados o
  colores que no se ven).

## Resultado por criterio

| CA | Qué se revisó | Hallazgos | Estado |
|----|---------------|-----------|--------|
| CA-146 Diseño adaptable | 30 pantallas en computador y móvil | 7 problemas, corregidos (tabla de abajo) | Cumple |
| CA-147 Validación comprensible | Formularios de registro, login, recuperación, perfil, publicación, servicios, tarifas, fechas, pago, reseñas | El login solo mostraba un aviso general y el botón quedaba deshabilitado sin decir qué faltaba. Ahora cada campo muestra su mensaje y se marca con `aria-invalid` | Cumple |
| CA-148 Estados comprensibles | Estados de publicación, reserva y calendarios | La barra de «Confirmadas» del dashboard no se veía (color no definido en el tema). Los calendarios de disponibilidad indican el estado con símbolo además del color (✓ disponible, ✕ reservado, – no disponible) | Cumple |
| CA-149 Retroalimentación | Pantallas que crean, modifican o eliminan datos | Todas muestran el resultado o el motivo del rechazo; sin cambios | Cumple |

## Problemas corregidos

| Pantalla | Tamaño | Problema | Corrección | Evidencia |
|----------|--------|----------|------------|-----------|
| Mis reservas (huésped) | Móvil | La pestaña «Canceladas» quedaba fuera de la pantalla y en el resumen se montaban las fechas de entrada y salida | Pestañas con desplazamiento horizontal; resumen en 2 columnas en móvil | [captura](movil-mis-reservas.jpg) |
| Mis reseñas (huésped) | Móvil | Las tarjetas se salían de la pantalla con títulos largos | La tarjeta respeta el ancho y el título se recorta con «…» | [captura](movil-mis-resenas.jpg) |
| Reservas, reseñas y usuarios (administrador) | Móvil | El filtro se salía de la pantalla y las columnas de la tabla quedaban recortadas sin poder verlas | Encabezado que baja de línea; tablas con desplazamiento horizontal; los mensajes de carga, error y vacío quedan a la vista | [captura](movil-admin-reservas.jpg) |
| Disponibilidad (anfitrión) | Móvil | Las etiquetas del calendario se partían en sílabas («Pas ado», «No hab ilita do») | En móvil se muestra el símbolo del estado; la etiqueta completa sigue en computador y en el lector de pantalla | [captura](movil-calendario-anfitrion.jpg) |
| Dashboard (administrador) | Móvil | Las etiquetas de «Reservas por estado» quedaban pegadas al número | Una columna en pantallas angostas | [captura](movil-dashboard-reservas-por-estado.jpg) |
| Dashboard y reportes (administrador) | Ambos | Usaban colores que el tema desactiva (`slate`, `emerald`): tarjetas sin fondo y la barra de «Confirmadas» invisible | Colores del tema (`ink`, `brand`) | [captura](movil-dashboard-reservas-por-estado.jpg) |
| Menú del administrador | Computador | Con 9 secciones, «Auditoría» quedaba debajo de la campana de notificaciones | Con más de 7 secciones la barra aparece desde 1280 px; antes, las secciones están en el menú de usuario | [captura](escritorio-menu-administrador.jpg) |
| Iniciar sesión | Ambos | Sin mensaje por campo (CA-147) | Mensaje bajo correo, contraseña y código | [móvil](login-errores-por-campo-movil.jpg), [computador](login-errores-por-campo-escritorio.jpg) |

## Pruebas

- `ng test`: 299 pruebas en verde, entre ellas 3 nuevas de CA-147 en `login.spec.ts`.
- Después de las correcciones, la revisión en los dos tamaños no encuentra elementos fuera de la pantalla.

Las 120 capturas completas (antes y después) se adjuntan en el comentario de Jira de SCRUM-212.
