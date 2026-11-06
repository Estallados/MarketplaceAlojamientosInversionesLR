# Historias de usuario

> Cada HU cita los requisitos definitivos de [catalogo-requisitos.md](catalogo-requisitos.md) (SCRUM-196).

## HU-01

Como huésped, quiero cancelar una reserva aplicando la política de cancelación asociada, para gestionar cambios en mi estadía y conocer las condiciones aplicables a la cancelación.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-002
- **Criterios de aceptación:** CA-001, CA-004, CA-007, CA-009
- **Observaciones:** Depende de una reserva existente y de una política de cancelación asociada. La cancelación debe conservar su información y liberar el período reservado únicamente cuando corresponda. La política aplicada es la asignada a la publicación al crearla (HU-15 / RF-017).

## HU-02

Como usuario de la plataforma, quiero recibir y consultar notificaciones sobre eventos relevantes, para mantenerme informado sobre las confirmaciones y cancelaciones relacionadas con mis reservas.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-003
- **Criterios de aceptación:** CA-010, CA-013
- **Observaciones:** Las notificaciones se gestionan dentro de la plataforma. Los eventos contemplados incluyen confirmaciones y cancelaciones de reservas, y el rechazo de publicaciones (evento originado en CA-195 de HU-32 / RF-027; no es un criterio de esta HU); el proyecto no exige correo, SMS ni notificaciones push.

## HU-03

Como huésped, quiero calificar y reseñar un alojamiento después de mi estancia y consultar las reseñas asociadas a cada alojamiento, para compartir mi experiencia y conocer la de otros huéspedes antes de tomar una decisión.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-004
- **Criterios de aceptación:** CA-016, CA-018, CA-019
- **Observaciones:** Solo se puede reseñar una reserva completada y asociada al alojamiento evaluado; la reseña conserva la relación entre huésped, reserva, alojamiento, fecha y contenido. Las reseñas mostradas deben corresponder a la publicación consultada y las ocultadas mediante moderación no deben mostrarse públicamente.

## HU-04

Como administrador, quiero consultar un dashboard con indicadores de usuarios, publicaciones activas, reservas, ingresos estimados y ciudades con mayor demanda, para obtener una visión general de la información de gestión del sistema.

- **Prioridad (MoSCoW):** Should
- **Requisitos:** RF-005
- **Criterios de aceptación:** CA-022
- **Observaciones:** El dashboard lo consulta el ADMINISTRADOR. Los indicadores deben construirse con la información registrada en el sistema.

## HU-05

Como administrador, quiero exportar reportes de gestión en PDF o Excel, para disponer de la información del sistema en un formato que pueda consultar y conservar.

- **Prioridad (MoSCoW):** Could
- **Requisitos:** RF-006
- **Criterios de aceptación:** CA-027
- **Observaciones:** Solo el ADMINISTRADOR exporta reportes. La exportación debe utilizar la información disponible en el sistema y registrar quién solicitó el reporte y cuándo.

## HU-06

Como usuario, quiero registrarme proporcionando mis datos y seleccionando mi rol, para crear una cuenta y acceder a las funcionalidades correspondientes.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-001
- **Criterios de aceptación:** CA-030, CA-031
- **Observaciones:** Corresponde al RF-001 Registro de usuarios. Los roles contemplados son HUESPED, ANFITRION y ADMINISTRADOR.

## HU-07

Como usuario registrado, quiero iniciar sesión con mis credenciales, para acceder de forma segura a las funcionalidades disponibles según mi rol.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-008
- **Criterios de aceptación:** CA-034, CA-035, CA-037
- **Observaciones:** Corresponde al RF-008 Inicio de sesión. Incluye validación de credenciales, identificación del rol y restricción de acceso.

## HU-08

Como usuario registrado, quiero recuperar mi contraseña cuando no pueda acceder a mi cuenta, para volver a utilizar el sistema.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-009
- **Criterios de aceptación:** CA-038
- **Observaciones:** Corresponde al RF-009 Recuperación de contraseña. El proceso está definido como simulado según el alcance del proyecto.

## HU-09

Como usuario autenticado, quiero consultar y actualizar la información de mi perfil, para conocer y mantener actualizados los datos asociados a mi cuenta.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-010, RF-011
- **Criterios de aceptación:** CA-042, CA-044, CA-046, CA-047, CA-049
- **Observaciones:** Corresponde al RF-010 Consulta de perfil y al RF-011 Actualización de perfil. El acceso requiere autenticación y el usuario solo puede modificar su propio perfil.

## HU-10

Como usuario autenticado, quiero activar o desactivar mi cuenta, para controlar el estado de mi propia cuenta en la plataforma.

- **Prioridad (MoSCoW):** Should
- **Requisitos:** RF-012
- **Criterios de aceptación:** CA-050, CA-053
- **Observaciones:** Corresponde al RF-012 Activación y desactivación de cuenta. El usuario gestiona únicamente su propia cuenta.

## HU-11

Como administrador, quiero bloquear publicaciones cuando requieran una acción administrativa, para evitar que continúen visibles en la plataforma.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-013
- **Criterios de aceptación:** CA-054, CA-112
- **Observaciones:** Corresponde al RF-013 Bloqueo de publicaciones. La acción debe conservar las reservas futuras (marcándolas para revisión manual), impedir nuevas reservas y registrarse para auditoría.

## HU-12

Como administrador, quiero restringir cuentas de usuarios cuando sea necesario, para impedir acciones no permitidas y mantener el control de la plataforma.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-014
- **Criterios de aceptación:** CA-058, CA-059
- **Observaciones:** Corresponde al RF-014 Restricción de usuarios. La restricción y la acción administrativa deben quedar registradas.

## HU-13

Como administrador, quiero consultar los eventos de auditoría, para revisar las acciones administrativas y cambios relevantes realizados en la plataforma.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-015
- **Criterios de aceptación:** CA-062, CA-065
- **Observaciones:** Corresponde al RF-015 Consulta de eventos de auditoría. El acceso está restringido a usuarios con permisos administrativos.

## HU-14

Como administrador, quiero ocultar reseñas que incumplan las reglas de moderación, para mantener contenido adecuado en la plataforma.

- **Prioridad (MoSCoW):** Should
- **Requisitos:** RF-016
- **Criterios de aceptación:** CA-066, CA-069
- **Observaciones:** Corresponde al RF-016 Moderación de reseñas. La acción de moderación debe conservarse para trazabilidad.

## HU-15

Como anfitrión, quiero crear una publicación de alojamiento, para poder ofrecer mi propiedad dentro del Marketplace.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-017, RF-018
- **Criterios de aceptación:** CA-094, CA-097, CA-098
- **Observaciones:** Corresponde al RF-017 Crear publicación de alojamiento. El usuario debe estar autenticado con rol ANFITRION, la publicación debe quedar inicialmente en estado BORRADOR y deben validarse la información mínima, la capacidad y el precio. La política de cancelación se asigna al crear la publicación y forma parte de la información mínima exigida para enviarla a revisión (CA-094, CA-097).

## HU-16

Como anfitrión propietario, quiero actualizar la información de mi publicación y definir servicios adicionales con su costo, para mantener actualizados los datos, precios, servicios y reglas de mi alojamiento y ofrecerlos a los huéspedes durante la cotización.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-018, RNF-004
- **Criterios de aceptación:** CA-100, CA-101, CA-113, CA-114, CA-185
- **Observaciones:** Corresponde al RF-018 Gestión y actualización de publicación. Solo el propietario de la publicación o un administrador puede realizar modificaciones (RNF-004). Los cambios en los servicios adicionales no deben alterar las reservas ya confirmadas. El anfitrión puede cambiar la política de cancelación de su publicación; el cambio aplica solo a reservas nuevas y no altera las ya confirmadas.

## HU-17

Como anfitrión o administrador, quiero enviar la publicación a revisión y gestionar su estado, para controlar cuándo puede mostrarse y recibir nuevas reservas.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-018, RF-027
- **Criterios de aceptación:** CA-104, CA-108, CA-157, CA-159
- **Observaciones:** Corresponde al RF-018 Gestión y actualización de publicación y al RF-027 Gestión del estado de revisión. Los estados permitidos son BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA y BLOQUEADA. Al enviarla a revisión pasa a PENDIENTE_REVISION y solo se activa cuando un administrador la aprueba (HU-32).

## HU-18

Como huésped, quiero consultar la información detallada de un alojamiento, incluida su ubicación en un mapa, para conocer sus características antes de realizar una reserva.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-019, RNF-022
- **Criterios de aceptación:** CA-093, CA-164
- **Observaciones:** Corresponde al RF-019 Consultar publicaciones registradas. La publicación debe encontrarse disponible para consulta según su estado. El mapa muestra la ubicación registrada del alojamiento.

## HU-19

Como anfitrión, quiero consultar mis publicaciones y sus estados, para poder administrar los alojamientos que tengo registrados.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-019
- **Criterios de aceptación:** CA-103
- **Observaciones:** Corresponde al RF-019 Consultar publicaciones registradas. El anfitrión debe visualizar sus propias publicaciones junto con el estado de cada una.

## HU-20

Como anfitrión, quiero registrar los periodos en los que mi alojamiento está disponible, para indicar cuándo puede recibir reservas.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-025
- **Criterios de aceptación:** CA-115, CA-117, CA-118
- **Observaciones:** Corresponde al RF-025 Registrar y actualizar períodos disponibles. Requiere un alojamiento previamente registrado; el período debe quedar asociado al alojamiento y no puede modificarse de forma que afecte una reserva.

## HU-21

Como huésped, quiero consultar la disponibilidad de un alojamiento para unas fechas seleccionadas, para saber si puedo continuar con el proceso de reserva.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-026, RNF-018
- **Criterios de aceptación:** CA-082, CA-119, CA-120
- **Observaciones:** Corresponde al RF-026 Consultar disponibilidad. Requiere una publicación ACTIVA, períodos de disponibilidad registrados y las fechas seleccionadas por el huésped, con salida posterior a la llegada.

## HU-22

Como huésped, quiero crear una reserva sobre un alojamiento activo indicando fechas y cotización previa, para asegurar mi estadía y que las fechas queden bloqueadas automáticamente para otros huéspedes.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-020, RF-021, RNF-019, RNF-020, RNF-021
- **Criterios de aceptación:** CA-070, CA-071, CA-072, CA-074, CA-075, CA-076, CA-086
- **Observaciones:** Basada en RF-021 y RF-020. Solo se reserva sobre alojamientos ACTIVA y con el monto cotizado. Se rechazan períodos superpuestos y confirmaciones simultáneas sobre las mismas fechas. Al confirmar una reserva válida, el período se bloquea automáticamente y aparece como no disponible para otros huéspedes.

## HU-23

Como huésped, quiero buscar alojamientos por ciudad, fechas, capacidad, tipo, rango de precio y servicios, para encontrar y comparar las opciones que se ajusten a mi presupuesto y mis necesidades.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-024
- **Criterios de aceptación:** CA-087
- **Observaciones:** Basada en RF-024. Solo aparecen publicaciones ACTIVA. Los filtros pueden combinarse y, al seleccionar varios servicios, el alojamiento debe ofrecerlos todos.

## HU-24

Como anfitrión o administrador, quiero configurar los porcentajes de la tarifa de limpieza y de servicio, para que las cotizaciones usen los valores definidos.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-023
- **Criterios de aceptación:** CA-084
- **Observaciones:** Tanto el anfitrión como el administrador pueden modificar los porcentajes de las tarifas de limpieza y servicio. Si no se configuran estos valores, se aplicarán los porcentajes predeterminados del sistema.

## HU-25

Como huésped o anfitrión, quiero consultar el estado e historial de mis reservas, para hacerles seguimiento.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-022
- **Criterios de aceptación:** CA-078, CA-079
- **Observaciones:** Basada en RF-022. El huésped ve sus reservas y el anfitrión solo las de sus alojamientos.

## HU-26

Como administrador, quiero que las operaciones relevantes realizadas en la plataforma queden registradas, para poder identificar quién realizó cada acción, cuándo ocurrió y cuál fue su resultado.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RNF-006, RNF-007
- **Criterios de aceptación:** CA-136, CA-137, CA-138, CA-139
- **Observaciones:** Basada en RNF-006 y RNF-007. Deben registrarse operaciones relevantes sobre usuarios, publicaciones, reservas, cancelaciones y reseñas, conservando el usuario responsable, la fecha y el resultado o estado de la operación.

## HU-27

Como huésped, quiero realizar el pago de mi reserva en un ambiente de prueba, para completar el proceso de reserva sin realizar una transacción real.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-028
- **Criterios de aceptación:** CA-160, CA-161, CA-162
- **Observaciones:** Debe ser demostrable.

## HU-28

Como huésped o anfitrión, quiero enviar y responder mensajes sobre un alojamiento, para resolver dudas y brindar información antes de realizar una reserva.

- **Prioridad (MoSCoW):** Could
- **Requisitos:** RF-029
- **Criterios de aceptación:** CA-165, CA-167, CA-169
- **Observaciones:** Requiere autenticación. Cada usuario solo debe acceder a las conversaciones en las que participa.

## HU-29

Como huésped, quiero guardar, consultar y eliminar alojamientos de mi lista de favoritos, para volver a revisarlos y mantener únicamente los que me interesan.

- **Prioridad (MoSCoW):** Could
- **Requisitos:** RF-007
- **Criterios de aceptación:** CA-170, CA-171, CA-173
- **Observaciones:** Funcionalidad opcional del proyecto, que solo se implementa si el MVP obligatorio queda completo. Los favoritos pertenecen al usuario autenticado y no debe generarse duplicidad al agregar nuevamente un alojamiento. Al eliminar un favorito, este debe dejar de aparecer en la lista.

## HU-30

Como usuario, quiero habilitar la autenticación multifactor y validar un segundo factor al iniciar sesión, para aumentar la seguridad de mi acceso e impedir accesos no autorizados a mi cuenta.

- **Prioridad (MoSCoW):** Could
- **Requisitos:** RNF-023
- **Criterios de aceptación:** CA-174, CA-177, CA-178
- **Observaciones:** Complementa el inicio de sesión existente y debe integrarse con su mecanismo de autenticación. La configuración queda asociada exclusivamente a la cuenta del usuario.

## HU-31

Como huésped, quiero ver la cotización de mi estadía antes de reservar y agregar servicios adicionales, para conocer el desglose del costo (noches, precio base, tarifa de limpieza, tarifa de servicio, servicios adicionales, descuentos y total) y decidir si continúo.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-021, RF-023, RNF-018, RNF-021
- **Criterios de aceptación:** CA-080, CA-082, CA-083, CA-084, CA-179, CA-183
- **Observaciones:** La transparencia en los precios es un objetivo del Marketplace, por eso el huésped debe conocer el costo total antes de comprometerse. Los servicios adicionales son opcionales, tienen un costo propio definido por el anfitrión y los porcentajes de las tarifas son configurables. Las fechas de salida deben ser posteriores a las de llegada y la capacidad no puede excederse.

## HU-32

Como administrador, quiero revisar las publicaciones en revisión y aprobarlas o rechazarlas, para que solo se activen las que cumplen las condiciones del proyecto.

- **Prioridad (MoSCoW):** Must
- **Requisitos:** RF-027
- **Criterios de aceptación:** CA-158, CA-195, CA-196
- **Observaciones:** La moderación protege a los huéspedes de información engañosa y mantiene la calidad de la oferta. Solo las publicaciones ACTIVAS reciben reservas y aparecen en búsquedas. Si se rechaza, el anfitrión debe conocer el motivo para poder corregir y volver a presentar la publicación. Toda decisión debe quedar registrada para auditoría.
