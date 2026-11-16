# Guion de demostración de 5 minutos (SCRUM-205 / T17)

Muestra en vivo el flujo **publicar → buscar → disponibilidad → cotizar → reservar**, el mismo que valida la
prueba E2E (`e2e/tests/flujo-reserva.spec.ts`).

## Preparación (antes de empezar)

- Backend con `DEMO_POBLAR=true` y los datos de `demo/` cargados; frontend abierto en el inicio.
- Tres ventanas, cada una con su sesión iniciada (contraseña `Demo#2026`):
  - **Anfitriona:** `lauragomez@gmail.com`
  - **Administrador:** `andrescastillo@gmail.com` (en una ventana privada)
  - **Huésped:** `anatorres@gmail.com` (en otro navegador o perfil)
- Tener a mano una foto de una casa y la tarjeta de prueba `4242 4242 4242 4242`, `12/30`, `123`.
- Plan B: si algo falla en vivo, mostrar el video del último job **E2E** (artefacto `evidencia-e2e`).

## Guion

| Tiempo | Quién habla | Pantalla | Qué se dice y se hace |
|---|---|---|---|
| 0:00 – 0:30 | Presentador | Inicio | "InversionesLR conecta anfitriones con huéspedes. Vamos a seguir una reserva completa con los tres roles: anfitrión, administrador y huésped." |
| 0:30 – 1:45 | Anfitriona | Mis alojamientos → **Publicar alojamiento** | Paso 1: título "Casa campestre demo", ciudad Villa de Leyva, tipo Casa, 4 huéspedes, $200.000 por noche. Paso 2: horarios 15:00 / 11:00 y política Flexible. Paso 3: Wifi y Cocina. Paso 4: subir la foto y **Guardar borrador**. "El borrador queda guardado; nadie lo ve todavía." |
| 1:45 – 2:15 | Anfitriona | **Disponibilidad** del alojamiento | **+ Agregar disponibilidad** de mañana a dos meses → **Guardar período**. Luego **Editar** → **Enviar a revisión**. "Pasa a Pendiente de revisión: solo se publica cuando el administrador lo aprueba." |
| 2:15 – 2:45 | Administrador | **Revisar publicaciones** | Abrir la publicación, revisar fotos y datos → **Aprobar publicación** → **Confirmar aprobación**. "Queda Activa y aparece en la búsqueda." |
| 2:45 – 3:15 | Huésped | Inicio | Buscar **Villa de Leyva** con fechas dentro de 10 días por 3 noches y 2 huéspedes → abrir "Casa campestre demo". Mostrar fotos, servicios, mapa y reseñas. |
| 3:15 – 3:45 | Huésped | **Consultar disponibilidad** | Elegir llegada y salida en el calendario → **Consultar**. "El sistema confirma que las fechas están libres." → **Continuar**. |
| 3:45 – 4:30 | Huésped | **Confirmar reserva** | Mostrar la cotización: 3 noches × $200.000, limpieza 5 %, servicio 10 % y total. **Confirmar** → pagar con la tarjeta 4242. "La reserva queda CONFIRMADA y el pago Aprobado. La misma operación no se cobra dos veces gracias a la clave de idempotencia." |
| 4:30 – 4:50 | Anfitriona | **Reservas** del anfitrión y notificaciones | Mostrar que la reserva le llegó a la anfitriona y que esas fechas ya aparecen ocupadas. |
| 4:50 – 5:00 | Presentador | GitHub Actions → job **E2E** | "Este mismo flujo lo ejecuta Playwright en cada pull request; aquí está el video de la última corrida." |

## Si algo sale mal

| Problema | Qué hacer |
|---|---|
| La foto no sube (503) | El backend no tiene `CLOUDINARY_URL`. Usar un alojamiento ya ACTIVO de la demostración y seguir desde la búsqueda |
| La búsqueda no muestra el alojamiento | Revisar que el administrador lo aprobó y que las fechas buscadas están dentro del periodo disponible |
| "Fechas no disponibles" | Elegir otras fechas: alguien ya reservó esas en ensayos anteriores |
| El pago es rechazado | Revisar el número `4242 4242 4242 4242`; otras tarjetas de prueba simulan rechazos |
