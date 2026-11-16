// Servidor que imita la API de Cloudinary para las pruebas E2E (SCRUM-205).
//
// El backend sube las fotos a Cloudinary y en el CI no hay cuenta. Con
// CLOUDINARY_URL=cloudinary://clave:secreto@e2e?upload_prefix=http://localhost:9000
// el SDK de Cloudinary manda las peticiones a este servidor en lugar de api.cloudinary.com.
// Responde lo mínimo que lee CloudinaryService (public_id, secure_url, resources, result)
// y entrega la foto de prueba para cualquier URL de imagen, así el navegador la muestra.
//
// Uso: node e2e/cloudinary-falso.mjs   (puerto 9000, o PUERTO=xxxx)

import { createServer } from 'node:http';
import { readFileSync } from 'node:fs';

const PUERTO = Number(process.env.PUERTO ?? 9000);
const BASE = `http://localhost:${PUERTO}`;
const FOTO = readFileSync(new URL('./fixtures/alojamiento.jpg', import.meta.url));

/** Lee un campo de un cuerpo multipart/form-data o urlencoded. */
function campo(cuerpo, nombre) {
  const multipart = cuerpo.match(new RegExp(`name="${nombre}"\\r\\n\\r\\n([^\\r]*)`));
  if (multipart) return multipart[1];
  const urlencoded = new URLSearchParams(cuerpo).get(nombre);
  return urlencoded ?? null;
}

function json(res, datos) {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(datos));
}

createServer((req, res) => {
  const partes = [];
  req.on('data', (parte) => partes.push(parte));
  req.on('end', () => {
    const cuerpo = Buffer.concat(partes).toString('latin1');
    const ruta = req.url.split('?')[0];
    console.log(req.method, ruta);

    // Subida: POST /v1_1/<nube>/image/upload
    if (req.method === 'POST' && ruta.endsWith('/image/upload')) {
      const carpeta = campo(cuerpo, 'folder');
      const nombre = campo(cuerpo, 'public_id') ?? `foto-${Date.now()}`;
      const publicId = carpeta ? `${carpeta}/${nombre}` : nombre;
      return json(res, {
        public_id: publicId,
        version: 1,
        format: 'jpg',
        resource_type: 'image',
        width: 800,
        height: 533,
        secure_url: `${BASE}/e2e/image/upload/v1/${publicId}.jpg`,
      });
    }
    // Etiquetas y contexto: POST /v1_1/<nube>/image/explicit
    if (req.method === 'POST' && ruta.endsWith('/image/explicit')) {
      return json(res, { public_id: campo(cuerpo, 'public_id') });
    }
    // Borrado: POST /v1_1/<nube>/image/destroy
    if (req.method === 'POST' && ruta.endsWith('/image/destroy')) {
      return json(res, { result: 'ok' });
    }
    // Listado por etiqueta al arrancar: GET /v1_1/<nube>/resources/image/tags/<etiqueta>
    if (req.method === 'GET' && ruta.includes('/resources/')) {
      return json(res, { resources: [] });
    }
    // Cualquier otra GET es una imagen que pide el navegador
    if (req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Access-Control-Allow-Origin': '*' });
      return res.end(FOTO);
    }
    json(res, {});
  });
}).listen(PUERTO, () => console.log(`Cloudinary falso en ${BASE}`));
