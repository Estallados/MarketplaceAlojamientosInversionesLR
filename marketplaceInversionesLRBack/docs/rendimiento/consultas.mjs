// Cuenta las consultas SQL que lanza cada endpoint del backend. Requiere: ./entorno.sh iniciar --sql
//   node consultas.mjs <usuarios.json> <app-sql.log> <salida.json>
// Incluye páginas de 9 (Explorar), de 12 y 20, y de 100 para comparar.
import http from 'node:http';
import { readFileSync, writeFileSync } from 'node:fs';
const [archivo, log, salida] = process.argv.slice(2);
const u = JSON.parse(readFileSync(archivo, 'utf8')); const h = u.find(x => x.rol === 'HUESPED' && x.id > 1500).token;
const PUERTO = +process.env.CARGA_PUERTO || 8081;
const pedir = (ruta, token, cuerpo) => new Promise(ok => http.get({ host: 'localhost', port: PUERTO, path: ruta, headers: token ? { Authorization: 'Bearer ' + token } : {} }, r => { const t = []; r.on('data', d => t.push(d)); r.on('end', () => ok(cuerpo ? Buffer.concat(t).toString() : r.statusCode)); }).on('error', e => ok(e.code)));
const lineas = () => readFileSync(log, 'latin1').split('\n').filter(l => l.includes('org.hibernate.SQL')).length;
const esperar = ms => new Promise(r => setTimeout(r, ms));
const llegada = new Date(Date.now() + 100 * 864e5).toISOString().slice(0, 10), salidaF = new Date(Date.now() + 103 * 864e5).toISOString().slice(0, 10);
const casos = [
  ['Búsqueda por ciudad (12)', '/alojamientos?ciudad=Cali&tamanio=12'],
  ['Búsqueda por ciudad (100)', '/alojamientos?pagina=0&tamanio=100&ciudad=Cali'],
  ['Búsqueda con fechas (12)', `/alojamientos?ciudad=Cali&llegada=${llegada}&salida=${salidaF}&huespedes=2&tamanio=12`],
  ['Búsqueda con fechas (100)', `/alojamientos?pagina=0&tamanio=100&ciudad=Cali&llegada=${llegada}&salida=${salidaF}&huespedes=2`],
  ['Catálogo /disponibles (20)', '/alojamientos/disponibles?tamanio=20'],
  ['Catálogo /disponibles (100)', '/alojamientos/disponibles?pagina=0&tamanio=100'],
  ['Catálogo filtrado (9, Explorar)', '/alojamientos/disponibles?pagina=0&tamanio=9&tipo=casa&amenidades=wifi'],
  ['Opciones de filtros (catálogo)', '/alojamientos/filtros'],
  ['Opciones de filtros (búsqueda)', '/alojamientos/filtros?ciudad=Cali&huespedes=2'],
  ['Detalle de alojamiento', '/alojamientos/10'], ['Disponibilidad', '/alojamientos/10/disponibilidad'], ['Reseñas', '/alojamientos/10/resenas'],
  ['Tipos', '/alojamientos/tipos'],
  ['Perfil (token)', '/usuarios/perfil', h], ['Notificaciones no leídas', '/notificaciones/noleidas', h],
  ['Mis reservas (10)', '/reservas/mias?tamanio=10', h], ['Mis reservas (100)', '/reservas/mias?pagina=0&tamanio=100', h]];
// Solo contra el entorno de prueba: el alojamiento 1 sembrado se llama «Alojamiento de prueba 1».
if (!String(await pedir('/alojamientos/1', null, true)).includes('"Alojamiento de prueba 1"')) {
  console.error(`El backend de localhost:${PUERTO} no es el entorno de prueba (./entorno.sh iniciar --sql): no se envía nada más`);
  process.exit(1);
}
const res = [];
for (const [nombre, ruta, token] of casos) {
  await pedir(ruta, token); await esperar(300); const antes = lineas(); const estado = await pedir(ruta, token); await esperar(500);
  res.push({ nombre, ruta, estado, consultas: lineas() - antes }); console.log(nombre.padEnd(36), estado, res.at(-1).consultas);
}
writeFileSync(salida, JSON.stringify(res, null, 1));
