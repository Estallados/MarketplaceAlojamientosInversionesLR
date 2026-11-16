# SCRUM-206 · T18 Datos semilla y arranque para la demostración

## Resultado por criterio

| Criterio | Evidencia | Estado |
|---|---|---|
| Script o perfil demo documentado | `demo/README.md` explica el perfil paso a paso. Lo implementan los scripts de `demo/scripts/`, `DatosDemoInicializador` (`DEMO_POBLAR=true`) y los SQL `database/05b_datos_base_demo.sql` y `05c_estadias_pasadas_demo.sql` | Cumple |
| Un usuario por rol | `verificacion-api.txt`, punto 1: el administrador, 2 anfitriones y 5 huéspedes inician sesión (200) con el rol esperado | Cumple |
| Al menos 5 publicaciones ACTIVAS en 2 ciudades | `verificacion-api.txt`, puntos 2 y 3: 13 ACTIVAS, 10 en Bogotá y 3 en Medellín. Capturas `explorar-13-activas.jpg` y `busqueda-medellin.jpg` | Cumple |

## Ambiente verificado

- **Backend:** local (`localhost:8081`), conectado a Neon y Cloudinary.
- **Front:** local (`ng serve`, `localhost:4200`).
- **Fecha:** 2026-11-16.

Cada alojamiento tiene:
- 4 imágenes de Wikimedia Commons con licencia libre: 52 en total, todas responden 200 en Cloudinary.
- 2 reseñas publicadas y coordenadas válidas en el mapa.

Además quedaron reservas futuras, cancelaciones con reembolso, cotizaciones, favoritos y conversaciones.

## Archivos

| Archivo | Contenido |
|---|---|
| `verificacion-api.txt` | Salida de la verificación contra la API: inicio de sesión por rol, publicaciones ACTIVAS por ciudad e imágenes, reseñas y coordenadas de cada alojamiento |
| `explorar-13-activas.jpg` | Explorar en el front con los 13 alojamientos disponibles |
| `busqueda-medellin.jpg` | Búsqueda por la ciudad de Medellín: 3 alojamientos con sus fotos y calificación |

## Matriz de trazabilidad

No aplica. La matriz está organizada por criterio de aceptación (CA) y T18 es una tarea técnica sin CA propio.

## Pendiente fuera del repositorio

- **Confluence:** publicar los archivos de la carpeta `database/` del equipo, que no están en el repositorio: los
  SQL `05b` y `05c`, la herramienta `GenerarDatosBaseDemo.java` y las imágenes.

- **Render:** reiniciar el backend para que cargue las imágenes de Cloudinary, y confirmar que usa la misma
  `CLOUDINARY_URL` y la misma `CLOUDINARY_CARPETA` que el ambiente donde se hizo la carga.
