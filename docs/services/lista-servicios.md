# Descomposición en servicios

Lista de servicios del Marketplace de alojamientos con su responsabilidad, los datos que gestiona, sus dependencias y su integración con el proveedor de pagos. Es la base de la [matriz servicio–requisito–HU–proceso](matriz-servicios-requisitos.csv).

Los identificadores de requisitos (RF, RNF), historias (HU) y criterios (CA) siguen el [catálogo único de requisitos](../requirements/catalogo-requisitos.md) (SCRUM-196).

## Criterios de la descomposición

- Un servicio tiene una sola responsabilidad y es dueño de sus datos: solo él escribe sobre ellos. Los demás servicios los consultan o le piden una acción.
- La descomposición sigue los 14 módulos del Diseño Orientado a Servicios (SOA). Dos módulos se dividen en dos servicios porque tienen responsabilidades distintas: *Identidad y acceso* (S01 y S02) y *Reservas* (S07 y S09 con S08 como módulo de pagos).
- Se despliega como un monolito modular: cada servicio es un paquete de la aplicación Spring Boot, con su propia capa de servicio y sus tablas.

## Resumen

| ID | Servicio | Módulo SOA | Requisitos responsables | Proceso BPMN |
|---|---|---|---|---|
| S01 | Identidad y acceso | Identidad y Acceso | RF-001, RF-008, RF-009 | PB-04 |
| S02 | Gestión de usuarios | Identidad y Acceso | RF-010, RF-011, RF-012 | PB-04 |
| S03 | Alojamientos y publicaciones | Alojamientos | RF-017, RF-018, RF-019, RF-027 | PB-01 |
| S04 | Disponibilidad | Disponibilidad | RF-020, RF-025, RF-026 | PB-05 |
| S05 | Búsqueda | Búsqueda | RF-024 | PB-02 |
| S06 | Tarifas y cotización | Cotización | RF-023 | PB-02 |
| S07 | Reservas | Reservas | RF-021, RF-022 | PB-02 |
| S08 | Pagos | Pagos | RF-028 | PB-02 |
| S09 | Cancelaciones | Reservas | RF-002 | PB-03 |
| S10 | Reseñas | Reseñas | RF-004 | Por confirmar |
| S11 | Notificaciones | Notificaciones | RF-003 | PB-06 |
| S12 | Mensajería | Mensajería | RF-029 | PB-06 |
| S13 | Favoritos | Favoritos | RF-007 | Por confirmar |
| S14 | Auditoría y trazabilidad | Auditoría y Trazabilidad | RF-015 | PB-07 |
| S15 | Administración y moderación | Administración y Moderación | RF-013, RF-014, RF-016 | PB-07 |
| S16 | Analítica y reportes | Analítica y Reportes | RF-005, RF-006 | PB-07 |

## Servicios

### S01 · Identidad y acceso

- **Responsabilidad:** registro de usuarios con rol, inicio de sesión, recuperación de contraseña simulada, verificación de correo, segundo factor (TOTP) y emisión y validación del JWT con el rol del usuario.
- **Datos que gestiona:** `Usuario` (credenciales, rol, estado, correo verificado, datos del segundo factor), `CodigoVerificacion`, `TokenRecuperacion`.
- **Depende de:** S14 (registra accesos y cambios de credenciales); servicio de correo (SMTP) para códigos de verificación.
- **Lo usan:** todos los servicios para autenticar y autorizar por rol.

### S02 · Gestión de usuarios

- **Responsabilidad:** consultar y actualizar el perfil propio, activar o desactivar la cuenta propia y aplicar la restricción de una cuenta cuando la decide el administrador (S15).
- **Datos que gestiona:** datos básicos de `Usuario` y su `estado` (ACTIVO, INACTIVO, RESTRINGIDO).
- **Depende de:** S01 (autenticación), S14.

### S03 · Alojamientos y publicaciones

- **Responsabilidad:** crear y actualizar publicaciones, gestionar servicios adicionales y referencias de imágenes, controlar los estados de revisión (BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA, BLOQUEADA) y registrar la política de cancelación asignada a cada publicación.
- **Datos que gestiona:** `Alojamiento`, `ServicioAdicional`, `Imagen` (ruta o referencia), `HistorialEstadoAlojamiento`. Lee `PoliticaCancelacion`.
- **Depende de:** S01, S11 (aviso de rechazo), S14. S15 cambia los estados de las publicaciones, pero S03 no depende de S15.
- **Lo usan:** S04 y S05 (solo ACTIVA es visible y reservable), S06 (precio y política), S12 y S13 (existencia del alojamiento), S15 (aprobación, bloqueo y estados).
- **Por decidir:** la geocodificación de direcciones (RNF-022, mapa) y el almacenamiento de imágenes. El código actual usa Nominatim y Cloudinary; el alcance del MVP los deja como opcionales.

### S04 · Disponibilidad

- **Responsabilidad:** registrar y actualizar los períodos disponibles de un alojamiento, bloquear el período al confirmar una reserva, liberarlo al cancelar y responder si un alojamiento está libre para unas fechas.
- **Datos que gestiona:** `PeriodoDisponibilidad`.
- **Depende de:** S03 (solo publicaciones ACTIVA), S14.
- **Lo usan:** S05 (filtro de fechas), S06 (cotizar), S07 (revalidar antes de confirmar), S09 (liberar período).
- **Regla crítica:** la revalidación y el bloqueo se hacen dentro de una transacción con bloqueo pesimista sobre el alojamiento (RNF-019). Una consulta previa no basta.

### S05 · Búsqueda

- **Responsabilidad:** buscar alojamientos por ciudad, fechas, capacidad, tipo, precio y servicios, mostrando solo las publicaciones ACTIVA.
- **Datos que gestiona:** ninguno propio. Solo lee S03 y S04.
- **Depende de:** S03, S04.

### S06 · Tarifas y cotización

- **Responsabilidad:** mantener los porcentajes de limpieza y servicio y calcular la cotización: noches, precio base, tarifas, descuentos y total (RN-009).
- **Datos que gestiona:** `ConfiguracionTarifa`, `Cotizacion` (con fecha de expiración).
- **Depende de:** S03 (precio y servicios), S04 (fechas libres), S14.
- **Lo usa:** S07 (la reserva exige una cotización previa vigente).

### S07 · Reservas

- **Responsabilidad:** crear la reserva sobre un alojamiento ACTIVA con cotización previa, revalidar la disponibilidad, bloquear el período, coordinar el cobro, confirmar o cancelar la reserva y consultar su estado e historial. Garantiza la idempotencia de la solicitud (RNF-008) con la cabecera `Idempotency-Key`.
- **Datos que gestiona:** `Reserva`, `RegistroIdempotencia`.
- **Flujo en tres pasos:**
  1. Validación con bloqueo pesimista del alojamiento y estado `PENDIENTE_PAGO`.
  2. Cobro en S08, fuera de la transacción de bloqueo.
  3. `CONFIRMADA` si el pago se aprueba, o cancelación de la reserva pendiente si falla.
  Una reserva pendiente de pago que supera el tiempo límite se cancela con una tarea programada.
- **Depende de:** S06, S04, S08, S11, S14.
- **Lo usan:** S09 (cancelar), S10 (solo reservas completadas), S12 (contexto de la conversación), S16 (indicadores).

### S08 · Pagos (integración con el proveedor de pagos)

- **Responsabilidad:** cobrar y devolver el valor de una reserva mediante el proveedor de pagos, en modo de pruebas. No se realiza ningún cobro real (RF-028, RN-016).
- **Datos que gestiona:** `Pago` (referencia, proveedor, valor, estado, valor reembolsado, referencia de reembolso).
- **Depende de:** proveedor de pagos (ver sección siguiente) y S14. No depende de S07: recibe la referencia de la reserva como dato (`idReserva`).
- **Lo usan:** S07 (cobro) y S09 (reembolso).

### S09 · Cancelaciones

- **Responsabilidad:** aplicar la política de cancelación de la publicación, calcular la devolución, pedir el reembolso, liberar o conservar el período y registrar usuario, fecha, motivo, devolución y estado final (RF-002).
- **Datos que gestiona:** `Cancelacion`. Lee `PoliticaCancelacion` (a través de S03).
- **Depende de:** S07 (estado de la reserva), S08 (reembolso), S04 (liberar período), S11 (aviso), S14.

### S10 · Reseñas

- **Responsabilidad:** registrar una reseña y una calificación solo para una reserva completada y consultar las reseñas visibles de un alojamiento. La moderación la hace S15.
- **Datos que gestiona:** `Resena` (calificación, comentario, estado PUBLICADA u OCULTA).
- **Depende de:** S07 (reserva completada), S03, S14.
- **Proceso BPMN:** por confirmar. Ningún proceso PB-01 a PB-07 cubre la reseña del huésped.

### S11 · Notificaciones

- **Responsabilidad:** registrar notificaciones dentro de la plataforma para los eventos relevantes (confirmación y cancelación de reservas, rechazo de publicaciones) y permitir su consulta y marcado como leídas. No se envían correos, SMS ni notificaciones push (fuera de alcance).
- **Datos que gestiona:** `Notificacion`.
- **Recibe eventos de:** S07, S09, S03, S15.

### S12 · Mensajería

- **Responsabilidad:** conversaciones entre huésped y anfitrión relacionadas con un alojamiento o una reserva. Solo pueden leerlas los participantes (RNF-004). Prioridad *Could*; opcional tras el MVP.
- **Datos que gestiona:** `Conversacion`, `Mensaje`.
- **Depende de:** S01, S03, S07, S11.

### S13 · Favoritos

- **Responsabilidad:** guardar, consultar y eliminar alojamientos favoritos del huésped, sin duplicados (RF-007). Prioridad *Could*.
- **Datos que gestiona:** `Favorito`.
- **Depende de:** S01, S03.
- **Proceso BPMN:** por confirmar. Ningún proceso PB-01 a PB-07 lo cubre.

### S14 · Auditoría y trazabilidad

- **Responsabilidad:** registrar las operaciones relevantes (usuario responsable, fecha, resultado y recurso afectado) y permitir su consulta. Los registros son de solo inserción: no se modifican ni se eliminan (RNF-006, RNF-007, RNF-015, RNF-016, RNF-017).
- **Datos que gestiona:** `EventoAuditoria`.
- **Lo usan:** todos los servicios, que le envían sus eventos.

### S15 · Administración y moderación

- **Responsabilidad:** bloquear publicaciones (las reservas futuras pasan a revisión manual), restringir cuentas, ocultar reseñas, aprobar o rechazar publicaciones pendientes de revisión y registrar cada acción en S14.
- **Datos que gestiona:** ninguno propio. Actúa sobre S02, S03 y S10.
- **Depende de:** S02, S03, S07, S10, S11, S14.

### S16 · Analítica y reportes

- **Responsabilidad:** calcular los indicadores del dashboard y exportar reportes en PDF o Excel. Registra quién exportó y cuándo.
- **Datos que gestiona:** `RegistroExportacion`. Solo lee los datos de otros servicios.
- **Depende de:** lectura de S01, S03, S07, S08, S09; S14.

## Dependencias entre servicios

| Servicio | Depende de |
|---|---|
| S01 Identidad y acceso | S14 |
| S02 Gestión de usuarios | S01, S14 |
| S03 Alojamientos | S01, S11, S14 |
| S04 Disponibilidad | S03, S14 |
| S05 Búsqueda | S03, S04 |
| S06 Tarifas y cotización | S03, S04, S14 |
| S07 Reservas | S04, S06, S08, S11, S14 |
| S08 Pagos | S14, proveedor de pagos |
| S09 Cancelaciones | S04, S07, S08, S11, S14 |
| S10 Reseñas | S03, S07, S14 |
| S11 Notificaciones | S14 |
| S12 Mensajería | S01, S03, S07, S11 |
| S13 Favoritos | S01, S03 |
| S14 Auditoría | ninguno |
| S15 Administración | S02, S03, S07, S10, S11, S14 |
| S16 Analítica | S01, S03, S07, S08, S09, S14 |

Revisión de ciclos: el grafo no tiene ciclos. S14 no depende de nadie; las llamadas que cruzan módulos van en una sola dirección (por ejemplo, S07 llama a S08, y S09 llama a S07 y a S08, pero ninguno de ellos llama a S09). Al implementarlo, mantener esta dirección.

## Integración con el proveedor de pagos

Proveedor: **Stripe**, en modo de pruebas. El proyecto no realiza cobros reales.

| Aspecto | Decisión |
|---|---|
| Servicio responsable | S08 Pagos. Ningún otro servicio llama a Stripe directamente. |
| Modo de pruebas | Se activa con una clave secreta de prueba (`sk_test_…`). S08 llama a la API de Stripe para crear el cobro (PaymentIntent) y la devolución (Refund). |
| Modo simulado | Sin clave, S08 usa un simulador local que devuelve el mismo resultado y no hace llamadas externas. Es el modo que se usa en las pruebas automatizadas. |
| Cliente web | La SPA carga Stripe.js para la confirmación del pago de prueba. La clave pública no es secreta. |
| Cobro | S07 llama a S08 fuera de la transacción de bloqueo del alojamiento. Si el pago se aprueba, la reserva pasa a `CONFIRMADA`; si falla, se cancela la reserva pendiente. |
| Reembolso | S09 llama a S08 para devolver el valor según la política aplicada. |
| Reintentos | La solicitud de cobro lleva la `Idempotency-Key`, de modo que un reintento no genera un segundo cobro (RNF-008). |
| Errores | Un error del proveedor deja la reserva en `PENDIENTE_PAGO` y el estado del pago en `ERROR`; no confirma la reserva. |

Pendiente de confirmar: la clave de prueba real del equipo (solo se conoce el nombre de la variable de entorno `STRIPE_SECRET_KEY`) y la versión de la API de Stripe que se fijará en el proyecto.
