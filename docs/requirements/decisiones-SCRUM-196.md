# Decisiones de SCRUM-196

## 1. Referencia CA-195 en HU-02

HU-02 (notificaciones) citaba `(CA-195)` en sus observaciones como si fuera un criterio propio. CA-195 (*Rechazo con motivo*) pertenece a **RF-027 / HU-32**.
Se reescribe la cita como evento de origen: el rechazo de una publicación (CA-195, HU-32) genera una notificación que HU-02 registra y muestra (CA-010, CA-013). Los CA de HU-02 siguen siendo CA-010 y CA-013. Mismo ajuste en las observaciones de RF-003.

## 2. Dónde se asigna la política de cancelación

**Decisión propuesta: HU-15 (Crear publicación de alojamiento / RF-017).**

- RF-002 / CA-001 exigen que toda reserva cancelada tenga una política *asociada a la publicación*; si la política se asignara solo en HU-16 (edición), podrían existir publicaciones ACTIVA sin política.
- Al asignarla en la creación, CA-097 (información mínima para enviar a revisión) garantiza que ninguna publicación llegue a ACTIVA sin política.
- HU-16 conserva la posibilidad de **modificarla**; el cambio aplica solo a reservas nuevas (misma regla que CA-185 para servicios adicionales).

Cambios aplicados: CA-094 incluye *política de cancelación* entre los datos de la publicación; observaciones de HU-01, HU-15, HU-16, RF-017 y dependencias de RF-002.

> ⚠️ Requiere confirmación del equipo y revisar que la implementación de CA-094 (commit `598b01a`) capture la política al crear la publicación.

## Registro detallado de cambios

| Documento | ID | Campo | Antes | Después |
|---|---|---|---|---|
| Criterios de aceptación | CA-072 | RF / RNF relacionado | RF-021, RNF-028 | RF-021, RNF-019 |
| Criterios de aceptación | CA-072 | Requerimiento | Creación de reserva por el huésped | Creación de reserva por el huésped; Prevención de reservas superpuestas con revalidación |
| Criterios de aceptación | CA-074 | RF / RNF relacionado | RNF-029 | RNF-019 |
| Criterios de aceptación | CA-074 | Requerimiento | Revalidación de disponibilidad al confirmar | Prevención de reservas superpuestas con revalidación |
| Criterios de aceptación | CA-075 | RF / RNF relacionado | RNF-030 | RNF-020 |
| Criterios de aceptación | CA-076 | Requerimiento | Bloqueo automático del período reservado | Bloquear período reservado |
| Criterios de aceptación | CA-080 | RF / RNF relacionado | RNF-031 | RNF-021 |
| Criterios de aceptación | CA-082 | RF / RNF relacionado | RNF-027 | RNF-018 |
| Criterios de aceptación | CA-083 | RF / RNF relacionado | RF-021, RNF-027 | RF-021, RNF-018 |
| Criterios de aceptación | CA-083 | Requerimiento | Creación de reserva por el huésped | Creación de reserva por el huésped; Validación cronológica de fechas |
| Criterios de aceptación | CA-086 | RF / RNF relacionado | RNF-031 | RNF-021 |
| Criterios de aceptación | CA-098 | RF / RNF relacionado | RF-017 | RF-017, RF-018 |
| Criterios de aceptación | CA-098 | Requerimiento | Crear publicación de alojamiento | Crear publicación de alojamiento; Gestión y actualización de publicación |
| Criterios de aceptación | CA-113 | RF / RNF relacionado | RNF-005 | RNF-004 |
| Criterios de aceptación | CA-113 | Requerimiento | Protección de operaciones | Protección de operaciones e información de usuarios |
| Criterios de aceptación | CA-114 | RF / RNF relacionado | RNF-005 | RNF-004 |
| Criterios de aceptación | CA-114 | Requerimiento | Protección de operaciones | Protección de operaciones e información de usuarios |
| Criterios de aceptación | CA-131 | RF / RNF relacionado | RNF-004 | RNF-003 |
| Criterios de aceptación | CA-131 | Requerimiento | Control de acceso por roles | Control de acceso por autenticación y roles |
| Criterios de aceptación | CA-133 | RF / RNF relacionado | RNF-006 | RNF-004 |
| Criterios de aceptación | CA-133 | Requerimiento | Protección de información de usuarios | Protección de operaciones e información de usuarios |
| Criterios de aceptación | CA-134 | RF / RNF relacionado | RNF-007 | RNF-003 |
| Criterios de aceptación | CA-134 | Requerimiento | Validación de autenticación para operaciones protegidas | Control de acceso por autenticación y roles |
| Criterios de aceptación | CA-135 | RF / RNF relacionado | RNF-008 | RNF-005 |
| Criterios de aceptación | CA-136 | RF / RNF relacionado | RNF-009 | RNF-006 |
| Criterios de aceptación | CA-137 | RF / RNF relacionado | RNF-010 | RNF-006 |
| Criterios de aceptación | CA-137 | Requerimiento | Identificación del usuario que realiza la acción | Registro de operaciones relevantes |
| Criterios de aceptación | CA-138 | RF / RNF relacionado | RNF-011 | RNF-006 |
| Criterios de aceptación | CA-138 | Requerimiento | Registro de fecha y estado de las operaciones | Registro de operaciones relevantes |
| Criterios de aceptación | CA-139 | RF / RNF relacionado | RNF-012 | RNF-007 |
| Criterios de aceptación | CA-140 | RF / RNF relacionado | RNF-013 | RNF-008 |
| Criterios de aceptación | CA-140 | Requerimiento | Prevención de operaciones duplicadas | Prevención de duplicados y consistencia ante reintentos |
| Criterios de aceptación | CA-141 | RF / RNF relacionado | RNF-014 | RNF-008 |
| Criterios de aceptación | CA-141 | Requerimiento | Consistencia ante reintentos | Prevención de duplicados y consistencia ante reintentos |
| Criterios de aceptación | CA-142 | RF / RNF relacionado | RNF-015 | RNF-009 |
| Criterios de aceptación | CA-142 | Requerimiento | Tiempo de respuesta en consulta de alojamientos | Tiempo de respuesta de consultas |
| Criterios de aceptación | CA-143 | RF / RNF relacionado | RNF-016 | RNF-009 |
| Criterios de aceptación | CA-143 | Requerimiento | Tiempo de respuesta de disponibilidad | Tiempo de respuesta de consultas |
| Criterios de aceptación | CA-144 | RF / RNF relacionado | RNF-017 | RNF-010 |
| Criterios de aceptación | CA-145 | RF / RNF relacionado | RNF-018 | RNF-011 |
| Criterios de aceptación | CA-146 | RF / RNF relacionado | RNF-019 | RNF-012 |
| Criterios de aceptación | CA-147 | RF / RNF relacionado | RNF-020 | RNF-013 |
| Criterios de aceptación | CA-147 | Requerimiento | Claridad de formularios | Claridad de formularios y mensajes |
| Criterios de aceptación | CA-148 | RF / RNF relacionado | RNF-021 | RNF-014 |
| Criterios de aceptación | CA-149 | RF / RNF relacionado | RNF-022 | RNF-013 |
| Criterios de aceptación | CA-149 | Requerimiento | Mensajes de confirmación y error | Claridad de formularios y mensajes |
| Criterios de aceptación | CA-150 | RF / RNF relacionado | RNF-023 | RNF-015 |
| Criterios de aceptación | CA-151 | RF / RNF relacionado | RNF-024 | RNF-016 |
| Criterios de aceptación | CA-152 | RF / RNF relacionado | RNF-025 | RNF-017 |
| Criterios de aceptación | CA-152 | Requerimiento | Registro de operaciones rechazadas | Conservación de evidencias e historial |
| Criterios de aceptación | CA-153 | RF / RNF relacionado | RNF-026 | RNF-017 |
| Criterios de aceptación | CA-153 | Requerimiento | Conservación del historial | Conservación de evidencias e historial |
| Criterios de aceptación | CA-155 | Requerimiento | Recuperación ante fallos | Recuperación y continuidad ante fallos |
| Criterios de aceptación | CA-156 | RF / RNF relacionado | RNF-003 | RNF-002 |
| Criterios de aceptación | CA-156 | Requerimiento | Continuidad de información ante fallos | Recuperación y continuidad ante fallos |
| Criterios de aceptación | CA-164 | RF / RNF relacionado | RNF-032 | RNF-022 |
| Criterios de aceptación | CA-174 | RF / RNF relacionado | RNF-033 | RNF-023 |
| Criterios de aceptación | CA-177 | RF / RNF relacionado | RNF-033 | RNF-023 |
| Criterios de aceptación | CA-178 | RF / RNF relacionado | RNF-033 | RNF-023 |
| Historias de usuario | HU-07 | Observaciones (cita RF) | Corresponde al RF-009 Inicio de sesión. Incluye validación de credenciales, identificación del rol y restricción de acceso. | Corresponde al RF-008 Inicio de sesión. Incluye validación de credenciales, identificación del rol y restricción de acceso. |
| Historias de usuario | HU-08 | Observaciones (cita RF) | Corresponde al RF-010 Recuperación de contraseña. El proceso está definido como simulado según el alcance del proyecto. | Corresponde al RF-009 Recuperación de contraseña. El proceso está definido como simulado según el alcance del proyecto. |
| Historias de usuario | HU-09 | Observaciones (cita RF) | Corresponde al RF-011 Consulta de perfil y al RF-012 Actualización de perfil. El acceso requiere autenticación y el usuario solo puede modificar su propio perfil. | Corresponde al RF-010 Consulta de perfil y al RF-011 Actualización de perfil. El acceso requiere autenticación y el usuario solo puede modificar su propio perfil. |
| Historias de usuario | HU-10 | Observaciones (cita RF) | Corresponde al RF-013 Activación y desactivación de cuenta. El usuario gestiona únicamente su propia cuenta. | Corresponde al RF-012 Activación y desactivación de cuenta. El usuario gestiona únicamente su propia cuenta. |
| Historias de usuario | HU-11 | Observaciones (cita RF) | Corresponde al RF-014 Bloqueo de publicaciones. La acción debe conservar las reservas futuras (marcándolas para revisión manual), impedir nuevas reservas y registrarse para auditoría. | Corresponde al RF-013 Bloqueo de publicaciones. La acción debe conservar las reservas futuras (marcándolas para revisión manual), impedir nuevas reservas y registrarse para auditoría. |
| Historias de usuario | HU-12 | Observaciones (cita RF) | Corresponde al RF-015 Restricción de usuarios. La restricción y la acción administrativa deben quedar registradas. | Corresponde al RF-014 Restricción de usuarios. La restricción y la acción administrativa deben quedar registradas. |
| Historias de usuario | HU-13 | Observaciones (cita RF) | Corresponde al RF-016 Consulta de eventos de auditoría. El acceso está restringido a usuarios con permisos administrativos. | Corresponde al RF-015 Consulta de eventos de auditoría. El acceso está restringido a usuarios con permisos administrativos. |
| Historias de usuario | HU-14 | Observaciones (cita RF) | Corresponde al RF-017 Moderación de reseñas. La acción de moderación debe conservarse para trazabilidad. | Corresponde al RF-016 Moderación de reseñas. La acción de moderación debe conservarse para trazabilidad. |
| Historias de usuario | HU-15 | Observaciones (cita RF) | Corresponde al RF-018 Crear publicación de alojamiento. El usuario debe estar autenticado con rol ANFITRION, la publicación debe quedar inicialmente en estado BORRADOR y deben validarse la información mínima, la capacidad y el precio. | Corresponde al RF-017 Crear publicación de alojamiento. El usuario debe estar autenticado con rol ANFITRION, la publicación debe quedar inicialmente en estado BORRADOR y deben validarse la información mínima, la capacidad y el precio. |
| Historias de usuario | HU-16 | Observaciones (cita RF) | Corresponde al RF-019 Gestión y actualización de publicación. Solo el propietario de la publicación o un administrador puede realizar modificaciones (RNF-004). Los cambios en los servicios adicionales no deben alterar las reservas ya confirmadas. | Corresponde al RF-018 Gestión y actualización de publicación. Solo el propietario de la publicación o un administrador puede realizar modificaciones (RNF-004). Los cambios en los servicios adicionales no deben alterar las reservas ya confirmadas. |
| Historias de usuario | HU-17 | Observaciones (cita RF) | Corresponde al RF-019 Gestión y actualización de publicación y al RF-028 Gestión del estado de revisión. Los estados permitidos son BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA y BLOQUEADA. Al enviarla a revisión pasa a PENDIENTE_REVISION y solo se activa cuando un administrador la aprueba (HU-32). | Corresponde al RF-018 Gestión y actualización de publicación y al RF-027 Gestión del estado de revisión. Los estados permitidos son BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA y BLOQUEADA. Al enviarla a revisión pasa a PENDIENTE_REVISION y solo se activa cuando un administrador la aprueba (HU-32). |
| Historias de usuario | HU-18 | Observaciones (cita RF) | Corresponde al RF-020 Consultar publicaciones registradas. La publicación debe encontrarse disponible para consulta según su estado. El mapa muestra la ubicación registrada del alojamiento. | Corresponde al RF-019 Consultar publicaciones registradas. La publicación debe encontrarse disponible para consulta según su estado. El mapa muestra la ubicación registrada del alojamiento. |
| Historias de usuario | HU-19 | Observaciones (cita RF) | Corresponde al RF-020 Consultar publicaciones registradas. El anfitrión debe visualizar sus propias publicaciones junto con el estado de cada una. | Corresponde al RF-019 Consultar publicaciones registradas. El anfitrión debe visualizar sus propias publicaciones junto con el estado de cada una. |
| Historias de usuario | HU-20 | Observaciones (cita RF) | Corresponde al RF-026 Registrar y actualizar períodos disponibles. Requiere un alojamiento previamente registrado; el período debe quedar asociado al alojamiento y no puede modificarse de forma que afecte una reserva. | Corresponde al RF-025 Registrar y actualizar períodos disponibles. Requiere un alojamiento previamente registrado; el período debe quedar asociado al alojamiento y no puede modificarse de forma que afecte una reserva. |
| Historias de usuario | HU-21 | Observaciones (cita RF) | Corresponde al RF-027 Consultar disponibilidad. Requiere una publicación ACTIVA, períodos de disponibilidad registrados y las fechas seleccionadas por el huésped, con salida posterior a la llegada. | Corresponde al RF-026 Consultar disponibilidad. Requiere una publicación ACTIVA, períodos de disponibilidad registrados y las fechas seleccionadas por el huésped, con salida posterior a la llegada. |
| Historias de usuario | HU-22 | Observaciones (cita RF) | Basada en RF-022 y RF-021. Solo se reserva sobre alojamientos ACTIVA y con el monto cotizado. Se rechazan períodos superpuestos y confirmaciones simultáneas sobre las mismas fechas. Al confirmar una reserva válida, el período se bloquea automáticamente y aparece como no disponible para otros huéspedes. | Basada en RF-021 y RF-020. Solo se reserva sobre alojamientos ACTIVA y con el monto cotizado. Se rechazan períodos superpuestos y confirmaciones simultáneas sobre las mismas fechas. Al confirmar una reserva válida, el período se bloquea automáticamente y aparece como no disponible para otros huéspedes. |
| Historias de usuario | HU-23 | Observaciones (cita RF) | Basada en RF-025. Solo aparecen publicaciones ACTIVA. Los filtros pueden combinarse y, al seleccionar varios servicios, el alojamiento debe ofrecerlos todos. | Basada en RF-024. Solo aparecen publicaciones ACTIVA. Los filtros pueden combinarse y, al seleccionar varios servicios, el alojamiento debe ofrecerlos todos. |
| Historias de usuario | HU-25 | Observaciones (cita RF) | Basada en RF-023. El huésped ve sus reservas y el anfitrión solo las de sus alojamientos. | Basada en RF-022. El huésped ve sus reservas y el anfitrión solo las de sus alojamientos. |
| Historias de usuario | HU-02 | Observaciones (CA-195) | Las notificaciones se gestionan dentro de la plataforma. Los eventos contemplados incluyen confirmaciones y cancelaciones de reservas, y el rechazo de publicaciones (CA-195); el proyecto no exige correo, SMS ni notificaciones push. | Las notificaciones se gestionan dentro de la plataforma. Los eventos contemplados incluyen confirmaciones y cancelaciones de reservas, y el rechazo de publicaciones (evento originado en CA-195 de HU-32 / RF-027; no es un criterio de esta HU); el proyecto no exige correo, SMS ni notificaciones push. |
| Requerimientos funcionales | RF-003 | Observaciones (CA-195) | Los eventos pueden incluir confirmaciones o cancelaciones de reservas y el rechazo de publicaciones (CA-195). El proyecto no exige correo, SMS ni notificaciones push. | Los eventos pueden incluir confirmaciones o cancelaciones de reservas y el rechazo de publicaciones (evento originado en CA-195 de RF-027 / HU-32). El proyecto no exige correo, SMS ni notificaciones push. |
| Criterios de aceptación | CA-094 | Condición de aceptación | Dado que el usuario tiene el rol ANFITRION, cuando registra un nuevo alojamiento, entonces el sistema debe permitir crear la publicación ingresando título, descripción, ubicación, tipo, capacidad, precio, servicios, reglas e imágenes, y guardarla en estado BORRADOR. | Dado que el usuario tiene el rol ANFITRION, cuando registra un nuevo alojamiento, entonces el sistema debe permitir crear la publicación ingresando título, descripción, ubicación, tipo, capacidad, precio, servicios, reglas, política de cancelación e imágenes, y guardarla en estado BORRADOR. |
| Historias de usuario | HU-15 | Observaciones | Corresponde al RF-017 Crear publicación de alojamiento. El usuario debe estar autenticado con rol ANFITRION, la publicación debe quedar inicialmente en estado BORRADOR y deben validarse la información mínima, la capacidad y el precio. | Corresponde al RF-017 Crear publicación de alojamiento. El usuario debe estar autenticado con rol ANFITRION, la publicación debe quedar inicialmente en estado BORRADOR y deben validarse la información mínima, la capacidad y el precio. La política de cancelación se asigna al crear la publicación y forma parte de la información mínima exigida para enviarla a revisión (CA-094, CA-097). |
| Historias de usuario | HU-16 | Observaciones | Corresponde al RF-018 Gestión y actualización de publicación. Solo el propietario de la publicación o un administrador puede realizar modificaciones (RNF-004). Los cambios en los servicios adicionales no deben alterar las reservas ya confirmadas. | Corresponde al RF-018 Gestión y actualización de publicación. Solo el propietario de la publicación o un administrador puede realizar modificaciones (RNF-004). Los cambios en los servicios adicionales no deben alterar las reservas ya confirmadas. El anfitrión puede cambiar la política de cancelación de su publicación; el cambio aplica solo a reservas nuevas y no altera las ya confirmadas. |
| Historias de usuario | HU-01 | Observaciones | Depende de una reserva existente y de una política de cancelación asociada. La cancelación debe conservar su información y liberar el período reservado únicamente cuando corresponda. | Depende de una reserva existente y de una política de cancelación asociada. La cancelación debe conservar su información y liberar el período reservado únicamente cuando corresponda. La política aplicada es la asignada a la publicación al crearla (HU-15 / RF-017). |
| Requerimientos funcionales | RF-017 | Observaciones | Debe quedar inicialmente en estado BORRADOR. | Debe quedar inicialmente en estado BORRADOR. Incluye la asignación obligatoria de la política de cancelación. |
| Requerimientos funcionales | RF-002 | Dependencias | Reserva existente y cancelación procesada. | Reserva existente y cancelación procesada; política de cancelación asignada a la publicación (RF-017). |

La matriz de trazabilidad además sincronizó *Criterio* y *Condición de aceptación* con el documento de CA (72 celdas).
