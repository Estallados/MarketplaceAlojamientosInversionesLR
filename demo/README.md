# Perfil de demostración: datos semilla y alojamientos reales (SCRUM-206 / T18)

Deja el Marketplace listo para la sustentación con un usuario por rol y **13 alojamientos reales ACTIVOS en
2 ciudades**: 10 en Bogotá, tomados del registro oficial del Instituto Distrital de Turismo (IDT), y 3 en
Medellín. Cada alojamiento tiene 4 imágenes con licencia libre, reseñas y actividad de reservas.

## Criterios de T18

| Criterio | Cómo se cumple | Estado |
|---|---|---|
| Script o perfil demo documentado | Este documento, los scripts de `demo/scripts/`, `DatosDemoInicializador` (`DEMO_POBLAR=true`) y los SQL `database/05b_datos_base_demo.sql` y `05c_estadias_pasadas_demo.sql` | Cumple |
| Un usuario por rol | Un administrador, 2 anfitriones y 5 huéspedes (tabla de abajo), más las políticas FLEXIBLE, MODERADA y ESTRICTA y la configuración de tarifas (5 % limpieza, 10 % servicio) | Cumple |
| Al menos 5 publicaciones ACTIVAS en 2 ciudades | 13 ACTIVAS: 10 en Bogotá y 3 en Medellín | Cumple |

La evidencia está en `docs/evidencias/SCRUM-206/`.

## Usuarios

Contraseña de todos: `DEMO_CONTRASENIA`, por defecto `Demo#2026`. Tienen el correo verificado y no usan
segundo factor.

| Nombre | Correo | Rol |
|---|---|---|
| Andrés Castillo | `andrescastillo@gmail.com` | ADMIN |
| Laura Gómez | `lauragomez@gmail.com` | ANFITRION (dueña de los 13 alojamientos) |
| Carlos Ruiz | `carlosruiz@gmail.com` | ANFITRION |
| Ana Torres | `anatorres@gmail.com` | HUESPED |
| Diego Mora | `diegomora@gmail.com` | HUESPED |
| Sofía Pérez | `sofiaperez@gmail.com` | HUESPED |
| Pedro Díaz | `pedrodiaz@gmail.com` | HUESPED |
| Camila Herrera | `camilaherrera@gmail.com` | HUESPED |

Son direcciones `@gmail.com` que pueden pertenecer a personas reales. La aplicación solo les envía correo al
verificar la cuenta, recuperar la contraseña o pedir el segundo factor; las notificaciones de reservas son
internas. En la demostración no se deben usar esos tres flujos con estas cuentas.

## Alojamientos

| Ciudad | Localidad o comuna | Alojamientos |
|---|---|---|
| Bogotá | Chapinero | Four Seasons Casa Medina, The Artisan D.C., Viaggio Urbano |
| Bogotá | Santa Fe | Hotel Dann Avenida 19, ibis Bogotá Museo |
| Bogotá | La Candelaria | Botánico Hostel, Hotel de la Ópera |
| Bogotá | Usaquén | Hotel Estelar La Fontana |
| Bogotá | Teusaquillo | DoubleTree by Hilton Salitre AR |
| Bogotá | Fontibón | City Express Plus Bogotá Aeropuerto |
| Medellín | La Candelaria (Comuna 10) | Hotel Nutibara |
| Medellín | El Poblado (Comuna 14) | Hotel Dann Carlton Medellín, Four Points by Sheraton Medellín |

Tipos: 11 `Hotel`, 1 `Habitación` (habitación privada en hostal) y 1 `Apartamento` (apartaestudio en
apartahotel). En Bogotá se verificaron 19 establecimientos y, por decisión del equipo, se cargaron 10
(`SELECCION` en `construir_datos.py`). Los otros 9 quedan en `no_cargados` de `alojamientos_bogota.json`.

## Archivos

| Archivo | Contenido |
|---|---|
| `datos/alojamientos_bogota.json` | Datos completos de los 13 alojamientos (incluidos los de Medellín), con fuentes, imágenes y qué campos son valores técnicos |
| `datos/alojamientos_bogota.csv` | Inventario general |
| `datos/fuentes_alojamientos.csv` | Trazabilidad: fuente, URL y dato respaldado por cada alojamiento |
| `datos/licencias_imagenes.csv` | Autor, licencia, URL original y clasificación (real o ilustrativa) de cada imagen |
| `datos/coordenadas_alojamientos.csv` | Validación de coordenadas contra OpenStreetMap |
| `datos/resultado_carga.json` | Id en el Marketplace, estado, URL y public ID de Cloudinary de cada imagen, e imágenes huérfanas borradas |
| `datos/resultado_actividad.json` | Lo que creó la última ejecución de `generar_actividad.py` |
| `scripts/construir_datos.py` | Regenera el JSON y los CSV a partir de la capa del IDT y la curaduría |
| `scripts/validar_coordenadas.py` | Valida las coordenadas con Nominatim |
| `scripts/descargar_imagenes.py` | Descarga las imágenes de Wikimedia Commons, verificando de nuevo licencia y autor |
| `scripts/cargar_alojamientos.py` | Carga los alojamientos por la API REST; en publicaciones existentes sube las imágenes que falten y actualiza la descripción |
| `scripts/generar_estadias_pasadas.py` | Genera el SQL de las estadías terminadas (`database/05c_estadias_pasadas_demo.sql`) |
| `scripts/generar_actividad.py` | Crea por la API reseñas, reservas, cancelaciones, cotizaciones, favoritos y mensajes |

Los scripts solo usan la biblioteca estándar de Python 3.10 o superior.

Los archivos de la carpeta `database/` del equipo no están en el repositorio y se publican en Confluence:
- los SQL `05b_datos_base_demo.sql` y `05c_estadias_pasadas_demo.sql`;
- la herramienta `herramientas/GenerarDatosBaseDemo.java`;
- las imágenes descargadas (`Alojamientos/`).

## Cómo preparar el ambiente

1. **Datos base.** Arranca el backend una vez con `DEMO_POBLAR=true` (y, si se quiere, `DEMO_CONTRASENIA`). Al
   terminar de arrancar agrega las políticas, la configuración de tarifas y los usuarios que falten; nunca
   modifica ni borra datos. En el log queda `Datos de demostración: N políticas, N usuarios y N configuración
   de tarifas nuevos`.

   Alternativa sin reiniciar el backend: generar el SQL equivalente con la herramienta del equipo y ejecutarlo
   en el SQL Editor de Neon. Usa `INSERT … WHERE NOT EXISTS`, así funciona aunque el esquema lo haya creado
   Hibernate sin restricciones `UNIQUE`, y no duplica nada:

   ```powershell
   $env:AES_SECRET_KEY = '<clave del ambiente>'
   java database\herramientas\GenerarDatosBaseDemo.java > database\05b_datos_base_demo.sql
   ```

2. **Imágenes.** Se guardan fuera del repositorio, en la carpeta `database/Alojamientos` del equipo:

   ```bash
   python demo/scripts/descargar_imagenes.py --destino ../database/Alojamientos
   ```

3. **Backend** con `DB_*`, `AES_SECRET_KEY` y `CLOUDINARY_URL` configuradas (ver el README principal).

4. **Alojamientos.** Las contraseñas van por variables de entorno, nunca en el código:

   ```bash
   export API_URL=http://localhost:8081
   export DEMO_ANFITRION_CONTRASENIA='<contraseña del anfitrión>'
   export DEMO_ADMIN_CONTRASENIA='<contraseña del administrador>'
   python demo/scripts/cargar_alojamientos.py --imagenes ../database/Alojamientos
   ```

   Por cada alojamiento, la anfitriona lo crea en BORRADOR con sus imágenes (el backend las sube a Cloudinary en
   `marketplace/alojamientos/<id>`), agrega 180 días de disponibilidad desde hoy y lo envía a revisión; el
   administrador lo aprueba. Si se vuelve a ejecutar, no duplica nada: reconoce las publicaciones por título y
   solo completa lo que falte.

5. **Estadías terminadas.** Generar el SQL y ejecutarlo en el SQL Editor de Neon (es idempotente):

   ```powershell
   $env:AES_SECRET_KEY = '<clave del ambiente>'
   python demo/scripts/generar_estadias_pasadas.py > ../database/05c_estadias_pasadas_demo.sql
   ```

6. **Actividad:**

   ```bash
   export DEMO_CONTRASENIA='<contraseña de los usuarios demo>'
   python demo/scripts/generar_actividad.py
   ```

7. **Si la carga se hizo con un backend distinto al desplegado** (por ejemplo, uno local apuntando a Neon),
   reinicia el backend de Render: `ImagenRepository` lee las imágenes de Cloudinary al arrancar, y hasta
   entonces las publicaciones nuevas se ven sin foto en ese servidor. Además, Render debe usar la misma
   `CLOUDINARY_URL` y la misma `CLOUDINARY_CARPETA` que el backend con el que se hizo la carga.

## Resultado (2026-11-16)

Se cargó con un backend local (`localhost:8081`) conectado a Neon y Cloudinary.

- **Usuarios:** los 8 inician sesión (200) con el rol esperado.
- **Alojamientos:** 13 ACTIVOS (ids 1 a 13). La búsqueda por ciudad devuelve 10 en Bogotá y 3 en Medellín. Los
  filtros por tipo, amenidad y precio funcionan, y el detalle trae la política MODERADA y las coordenadas sin
  cambios.
- **Imágenes:** 4 por alojamiento, 52 en total, todas responden 200 en Cloudinary.
- **Front local (`ng serve`):** Explorar muestra los 13 con su portada y calificación. El detalle muestra la
  galería, la descripción con aviso y créditos, horarios, política, precio, reseñas y el mapa de
  OpenStreetMap. El punto del mapa se desplaza unos metros a propósito (CA-164).
- **Imágenes huérfanas:** en Cloudinary había 6 imágenes de alojamientos anteriores con los ids 8, 9, 10 y 11, y
  el backend las asoció a los nuevos alojamientos con esos ids. Todas respondían 404. Con autorización del
  equipo se borraron con `DELETE /alojamientos/{id}/imagenes/{idImagen}`. Quedan registradas en
  `imagenes_huerfanas_previas_borradas` de `resultado_carga.json`.
- **Render:** su backend ve los alojamientos, pero sin fotos hasta que se reinicie (ver el paso 7).

## Actividad de demostración

Es actividad ficticia de los huéspedes demo sobre las publicaciones:

- **Estadías terminadas:** 26, 2 por alojamiento, cada una con cotización, reserva CONFIRMADA y pago APROBADO
  del simulador. Van por SQL porque la API no deja reservar fechas pasadas, y las reseñas exigen una estadía
  terminada.
- **Reseñas:** 26, 2 por alojamiento, con calificaciones de 3 a 5 y promedios entre 3.5 y 5.0. Cada comentario
  empieza con "Reseña de demostración:", porque no son opiniones reales sobre los establecimientos.
- **Reservas futuras:** 3 CONFIRMADAS, pagadas con tarjetas de prueba. Una más con pago rechazado
  (`pm_card_chargeDeclined`), que quedó CANCELADA con el pago RECHAZADO.
- **Cancelaciones:** 2 con la política MODERADA, ambas COMPLETADA con la devolución simulada:
  - con 25 días de anticipación: reembolso del 100 %;
  - con 3 días de anticipación: reembolso del 50 %.
- **Cotizaciones vigentes sin reservar:** se crean de nuevo en cada ejecución del script y vencen solas.
- **Favoritos:** 7.
- **Conversaciones:** 2, con la respuesta de la anfitriona.

## Procedencia y criterios

- **Establecimientos de Bogotá:** todos están en la capa oficial *Establecimiento de Alojamiento Turístico.
  Bogotá* (IDT / IDECA,
  [datos abiertos de Bogotá](https://datosabiertos.bogota.gov.co/dataset/establecimiento-de-alojamiento-turistico-bogota),
  CC BY-SA 4.0). De ahí salen el RNT, la localidad, el sector y las coordenadas, que el IDT describe como
  aproximadas.
- **Establecimientos de Medellín:** no hay una capa oficial equivalente. La ubicación sale del objeto
  `tourism=hotel` de OpenStreetMap (ODbL), que es además el proveedor de mapas del Marketplace. La dirección
  viene del sitio de la cadena o de plataformas comerciales.
- **Direcciones y descripciones:** sitios oficiales de las cadenas, Visit Bogotá (IDT) y, como referencia
  complementaria, plataformas comerciales. Cada dato tiene su URL en `fuentes_alojamientos.csv`. Cuando las
  fuentes no coinciden, se indica en `estado_direccion`.
- **Coordenadas:** se envían en la publicación, así el backend no geocodifica la dirección. Las 13 caen dentro
  de su ciudad y en la localidad o comuna esperada según OpenStreetMap. Nominatim no resuelve bien muchas
  direcciones colombianas con placa; por eso no se deja que el backend las geocodifique.
- **Imágenes:** 52 archivos de Wikimedia Commons (31 fotografías distintas), con licencias CC BY 2.0, CC BY 3.0,
  CC BY-SA 2.0, CC BY-SA 2.5, CC BY-SA 3.0, CC BY-SA 4.0 o dominio público.
  - **17 son reales:** fachadas y entradas de 9 establecimientos.
  - **35 son ilustrativas:** interiores genéricos o calles del entorno que no son el inmueble.
  - No se usaron fotos de Airbnb, Booking.com ni de los sitios de los hoteles.
  - La descripción de cada publicación indica qué foto es real y cuál ilustrativa, con su autor y licencia.
- **Valores técnicos de demostración** (no son datos reales): precio por noche (según segmento), política de
  cancelación (MODERADA), reglas y período de disponibilidad. También la capacidad, las habitaciones y los baños
  de la unidad publicada (una habitación doble o un apartaestudio), y las horas de entrada y salida cuando no se
  encontraron. Cada alojamiento los lista en `datos_tecnicos`. Las camas quedan vacías porque no se pudieron
  verificar. No se crearon servicios adicionales: el modelo exige un costo y no hay tarifas públicas
  verificables.
- **Aviso:** cada descripción aclara que es una publicación de demostración académica, que no es una oferta
  real y que no está afiliada al establecimiento.

## Excluidos

Los establecimientos y las fotos sin información verificable están en `excluidos` de `alojamientos_bogota.json`.
Por ejemplo, las fotos de Commons llamadas "Hotel Marriott Bogota" son del Marriott de la Avenida El Dorado y no
del JW Marriott de la Calle 73. También se descartaron candidatas con personas, con marca de agua o que no
correspondían al tipo de alojamiento.
