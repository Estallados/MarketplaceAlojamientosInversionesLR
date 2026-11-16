-- Datos de prueba de la colección Newman (SCRUM-204).
-- Se ejecuta después de arrancar el backend con DEMO_POBLAR=true, que ya creó las políticas de
-- cancelación y los usuarios de la demostración (Laura Gómez es la anfitriona).
-- Las publicaciones se insertan por SQL porque crearlas por la API exige subir imágenes a Cloudinary,
-- que no está disponible en el CI. El resto del flujo (revisión, aprobación, reserva...) va por la API.

-- Publicación completa, pendiente de revisión, con fechas disponibles: el administrador la aprueba en la colección
INSERT INTO alojamiento (titulo, descripcion, ubicacion, ciudad, tipo, capacidad, habitaciones, camas, banos,
                         hora_entrada, hora_salida, precio_noche, reglas, estado, id_anfitrion, id_politica,
                         fecha_creacion, fecha_actualizacion)
SELECT 'Apartamento Newman', 'Publicación de prueba de la colección Postman/Newman.', 'Calle 10 # 5-20', 'Cali',
       'Apartamento', 2, 1, 1, 1, '15:00', '11:00', 150000, 'No se permite fumar.', 'PENDIENTE_REVISION',
       u.id_usuario, p.id_politica, NOW(), NOW()
FROM usuario u, politica_cancelacion p
WHERE u.nombre = 'Laura Gómez' AND p.nombre = 'FLEXIBLE';

INSERT INTO periodo_disponibilidad (id_alojamiento, fecha_inicio, fecha_fin)
SELECT id_alojamiento, CURRENT_DATE + 1, CURRENT_DATE + 120
FROM alojamiento
WHERE titulo = 'Apartamento Newman';

-- Borrador incompleto: enviarlo a revisión debe responder 409 con los datos que faltan
INSERT INTO alojamiento (titulo, ciudad, estado, id_anfitrion, fecha_creacion, fecha_actualizacion)
SELECT 'Borrador Newman', 'Cali', 'BORRADOR', u.id_usuario, NOW(), NOW()
FROM usuario u
WHERE u.nombre = 'Laura Gómez';
