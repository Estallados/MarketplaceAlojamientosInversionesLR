// Generador de carga sin dependencias (Node 22) para el backend del marketplace (marketplaceInversionesLRBack).
// Modelo cerrado (usuarios virtuales con tiempo de espera) y modelo abierto (tasa de llegada constante: no se frena
// cuando el servidor se pone lento).
//   node carga.mjs <escenario> [base_url] [usuarios.json] [carpeta_resultados]
// Escenarios: validacion | humo | carga | estres | pico | login | login_fallido | resistencia | concurrencia
// Variables: MEZCLA (frontend | paginada), ESCALONES (lista de rps del estrés), BASE_RPS, PICO_RPS, RESISTENCIA_RPS,
//            RESISTENCIA_MIN, RESISTENCIA_RAMPA, ABORTAR_ERR_PCT, SUFIJO.
// Seguridad: solo corre contra localhost; corta la subida si los errores superan el umbral y aborta todo si el
// backend deja de responder al health durante 30 s. Ctrl+C detiene el generador al instante.
import http from 'node:http';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { monitorEventLoopDelay, performance } from 'node:perf_hooks';
import { randomUUID } from 'node:crypto';

const [escenario = 'humo', BASE = 'http://localhost:8081',
  archivoUsuarios = `${process.env.TMPDIR || '/tmp'}/marketplace-alojamientos-carga/usuarios.json`,
  salida = new URL('./resultados', import.meta.url).pathname] = process.argv.slice(2);
const destino = new URL(BASE);
if (!['localhost', '127.0.0.1'].includes(destino.hostname)) throw new Error('Estas pruebas solo corren contra localhost');
const MEZCLA = process.env.MEZCLA || 'frontend';
if (!['frontend', 'paginada'].includes(MEZCLA)) throw new Error('MEZCLA debe ser frontend o paginada');
const TIMEOUT_MS = 10000, MAX_EN_VUELO = 3000, ABORTAR_ERR_PCT = +process.env.ABORTAR_ERR_PCT || 50;
// Muestras de health fallidas seguidas (una cada 5 s) antes de abortar: 6 = 30 s. Súbelo solo para medir recuperaciones largas.
const SALUD_MAX_FALLOS = +process.env.SALUD_MAX_FALLOS || 6;
const agente = new http.Agent({ keepAlive: true, maxSockets: 4000 });
const usuarios = JSON.parse(readFileSync(archivoUsuarios, 'utf8'));
const huespedes = usuarios.filter(u => u.rol === 'HUESPED');
const CIUDADES = ['Bogotá', 'Medellín', 'Cartagena', 'Cali', 'Santa Marta', 'Manizales', 'Barranquilla', 'Bucaramanga', 'Pereira', 'Villa de Leyva'];
const TIPOS = ['Apartamento', 'Casa', 'Cabaña', 'Estudio', 'Finca'];
const azar = n => Math.floor(Math.random() * n);
const de = l => l[azar(l.length)];
const fecha = dias => new Date(Date.now() + dias * 864e5).toISOString().slice(0, 10);
const esperar = ms => new Promise(r => setTimeout(r, ms));

function pedir(metodo, ruta, { token, cuerpo, cabeceras, timeout = TIMEOUT_MS } = {}) {
  return new Promise(ok => {
    const datos = cuerpo === undefined ? null : JSON.stringify(cuerpo);
    const inicio = performance.now();
    const req = http.request({ host: destino.hostname, port: destino.port, path: ruta, method: metodo, agent: agente, timeout,
      headers: { Accept: 'application/json', ...(datos ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(datos) } : {}),
        ...(token ? { Authorization: 'Bearer ' + token } : {}), ...cabeceras } }, res => {
      const trozos = []; res.on('data', t => trozos.push(t));
      res.on('end', () => ok({ estado: res.statusCode, ms: performance.now() - inicio, cuerpo: Buffer.concat(trozos).toString(), cabeceras: res.headers }));
      res.on('error', e => ok({ estado: 0, ms: performance.now() - inicio, error: e.code || 'ERROR_RESPUESTA' }));
    });
    req.on('timeout', () => req.destroy(Object.assign(new Error('t'), { code: 'TIMEOUT' })));
    req.on('error', e => ok({ estado: 0, ms: performance.now() - inicio, error: e.code || e.message }));
    if (datos) req.write(datos);
    req.end();
  });
}

// ---- Validación ligera del cuerpo: un 200 con un cuerpo que no corresponde también es un error ------------------
const VALIDA = {
  pagina: c => c.startsWith('{') && c.includes('"contenido":['),
  alojamiento: c => c.startsWith('{') && c.includes('"idAlojamiento"'),
  calendario: c => c.includes('"periodos"'),
  resenas: c => c.includes('"promedio"') || c.includes('"resenas"'),
  objeto: c => c.startsWith('{'),
  cotizacion: c => c.includes('"idCotizacion"'),
  reserva: c => c.includes('"idReserva"'),
  token: c => c.includes('"token"'),
};

// ---- Métricas por etapa y operación -------------------------------------------------
const etapas = []; let etapa = null; let abortado = null;
function nuevaEtapa(nombre, objetivo) { etapa = { nombre, objetivo, inicio: performance.now(), ops: new Map(), descartadas: 0, retrasoMaxMs: 0 }; etapas.push(etapa); }
function anotar(op, r, esperados, valida) {
  if (!etapa) return;
  let o = etapa.ops.get(op); if (!o) etapa.ops.set(op, o = { ms: [], ok: 0, err: 0, estados: {} });
  o.ms.push(r.ms);
  let clave = r.error || String(r.estado);
  let bien = esperados.includes(r.estado);
  if (bien && valida && r.estado >= 200 && r.estado < 300 && !valida(r.cuerpo || '')) { bien = false; clave = 'CUERPO_INVALIDO'; }
  o.estados[clave] = (o.estados[clave] || 0) + 1;
  bien ? o.ok++ : o.err++;
}
async function op(nombre, metodo, ruta, opciones = {}, esperados = [200], valida) {
  const r = await pedir(metodo, ruta, opciones); anotar(nombre, r, esperados, valida); return r;
}
const pct = (o, p) => o.length ? o[Math.min(o.length - 1, Math.floor(o.length * p))] : 0;
function resumen(ms, ok, err, seg) {
  const o = [...ms].sort((a, b) => a - b); const r = v => Math.round(v * 10) / 10;
  const media = ms.length ? ms.reduce((s, v) => s + v, 0) / ms.length : 0;
  return { n: ms.length, rps: r(ms.length / seg), ok, err, errPct: r(100 * err / Math.max(1, ms.length)),
    media: r(media), p50: r(pct(o, .5)), p90: r(pct(o, .9)), p95: r(pct(o, .95)), p99: r(pct(o, .99)), max: r(o.at(-1) || 0) };
}
function cerrarEtapa() {
  if (!etapa) return null;
  const seg = (performance.now() - etapa.inicio) / 1000; const ms = []; let ok = 0, err = 0; const por = {}; const estados = {};
  for (const [k, o] of etapa.ops) { for (const v of o.ms) ms.push(v); ok += o.ok; err += o.err; por[k] = { ...resumen(o.ms, o.ok, o.err, seg), estados: o.estados };
    for (const [e, n] of Object.entries(o.estados)) estados[e] = (estados[e] || 0) + n; }
  etapa.resultado = { nombre: etapa.nombre, objetivo: etapa.objetivo, segundos: Math.round(seg), ...resumen(ms, ok, err, seg), descartadas: etapa.descartadas, retrasoMaxMs: Math.round(etapa.retrasoMaxMs), estados, operaciones: por };
  const r = etapa.resultado;
  console.log(`${r.nombre.padEnd(20)} obj=${String(r.objetivo).padEnd(7)} rps=${String(r.rps).padEnd(7)} media=${String(r.media).padEnd(8)} p50=${String(r.p50).padEnd(8)} p95=${String(r.p95).padEnd(8)} p99=${String(r.p99).padEnd(8)} err=${r.errPct}% desc=${r.descartadas} ${JSON.stringify(estados)}`);
  etapa = null; return r;
}

// ---- Telemetría del backend y corte por indisponibilidad -------------------------------
const admin = await iniciarAdmin();
const serie = []; const lazo = monitorEventLoopDelay({ resolution: 20 }); lazo.enable();
let saludFallida = 0;
async function metrica(nombre, seleccion = 'VALUE') {
  const r = await pedir('GET', '/actuator/metrics/' + nombre, { token: admin, timeout: 4000 });
  try { return JSON.parse(r.cuerpo).measurements.find(m => m.statistic === seleccion)?.value ?? null; } catch { return null; }
}
async function iniciarAdmin() {
  const r = await pedir('POST', '/auth/login', { cuerpo: { correo: 'admin@carga.local', contrasenia: 'Carga#2026' } });
  if (r.estado !== 200) throw new Error('El login del administrador falló: ' + r.estado + ' ' + (r.cuerpo || r.error));
  return JSON.parse(r.cuerpo).token;
}
// CPU real de cada proceso (100 % = un núcleo), leída de /proc: separa backend de base de datos.
const TRABAJO = archivoUsuarios.replace(/\/[^/]*$/, ''); const previo = {};
function cpuProceso(nombre) {
  try {
    const pid = readFileSync(`${TRABAJO}/${nombre}.pid`, 'utf8').trim();
    const c = readFileSync(`/proc/${pid}/stat`, 'utf8').split(') ')[1].split(' ');
    const tics = +c[11] + +c[12], ahora = performance.now(); const p = previo[nombre]; previo[nombre] = { tics, ahora };
    return p ? Math.round((tics - p.tics) * 1000 / (ahora - p.ahora)) : null;
  } catch { return null; }
}
function rssMB(nombre) {
  try { const pid = readFileSync(`${TRABAJO}/${nombre}.pid`, 'utf8').trim();
    return Math.round(+readFileSync(`/proc/${pid}/status`, 'utf8').match(/VmRSS:\s+(\d+)/)[1] / 1024); } catch { return null; }
}
const telemetria = setInterval(async () => {
  const salud = await pedir('GET', '/actuator/health', { timeout: 3000 });
  saludFallida = salud.estado === 200 ? 0 : saludFallida + 1;
  const [hilos, hikPend, hikAct, heap, cpu, gcPausa] = await Promise.all(['tomcat.threads.busy', 'hikaricp.connections.pending', 'hikaricp.connections.active', 'jvm.memory.used?tag=area:heap', 'process.cpu.usage', 'jvm.gc.pause'].map(n => metrica(n, n === 'jvm.gc.pause' ? 'TOTAL_TIME' : 'VALUE')));
  serie.push({ t: Math.round(performance.now() / 1000), cpuBackend: cpuProceso('app'), cpuH2: cpuProceso('h2'), rssBackendMB: rssMB('app'), etapa: etapa?.nombre, salud: salud.estado || salud.error, saludMs: Math.round(salud.ms), hilos, hikPend, hikAct, heapMB: heap && Math.round(heap / 1e6), cpuPct: cpu && Math.round(cpu * 100), gcPausaTotalS: gcPausa && Math.round(gcPausa * 10) / 10, retrasoGeneradorMs: Math.round(lazo.max / 1e6) });
  lazo.reset();
  if (saludFallida >= SALUD_MAX_FALLOS && !abortado) { abortado = `El backend no respondió al health durante ${SALUD_MAX_FALLOS * 5} s: prueba abortada`; console.log(abortado); }
}, 5000); telemetria.unref();

// ---- Mezclas de tráfico (viaje del usuario en el marketplace) ---------------------------
// frontend: lo que pide el frontend. Inicio, Explorar y Buscar piden siempre la página 0 con tamanio=100 y
//           filtran por tipo, precio, amenidades y orden en el navegador; Mis reservas pide tamanio=100.
// paginada: el mismo recorrido con páginas pequeñas (12 y 20) y filtros y orden en el servidor. Muestra cuánto
//           cuesta el backend cuando el cliente pide solo lo que muestra.
// Cada proceso empieza en un bloque de alojamientos al azar: así el calentamiento, la validación y el escenario
// (que comparten base) no piden las mismas fechas del mismo alojamiento.
let ranura = azar(3700) * 150;
const frontend = [
  [22, () => op('buscar_ciudad', 'GET', `/alojamientos?pagina=0&tamanio=100&ciudad=${encodeURIComponent(de(CIUDADES))}`, {}, [200], VALIDA.pagina)],
  [13, () => { const d = 20 + azar(200); return op('buscar_fechas', 'GET', `/alojamientos?pagina=0&tamanio=100&ciudad=${encodeURIComponent(de(CIUDADES))}&llegada=${fecha(d)}&salida=${fecha(d + 3)}&huespedes=2`, {}, [200], VALIDA.pagina); }],
  [5, () => op('buscar_huespedes', 'GET', `/alojamientos?pagina=0&tamanio=100&ciudad=${encodeURIComponent(de(CIUDADES))}&huespedes=${2 + azar(5)}`, {}, [200], VALIDA.pagina)],
  [15, () => op('disponibles', 'GET', '/alojamientos/disponibles?pagina=0&tamanio=100', {}, [200], VALIDA.pagina)],
  [18, () => op('detalle', 'GET', `/alojamientos/${1 + azar(4000)}`, {}, [200], VALIDA.alojamiento)],
  [7, () => op('disponibilidad', 'GET', `/alojamientos/${1 + azar(4000)}/disponibilidad`, {}, [200], VALIDA.calendario)],
  [5, () => op('resenas', 'GET', `/alojamientos/${1 + azar(4000)}/resenas`, {}, [200], VALIDA.resenas)],
  [3, () => op('tipos', 'GET', '/alojamientos/tipos', {}, [200], VALIDA.objeto)],
  [3, () => op('perfil', 'GET', '/usuarios/perfil', { token: de(huespedes).token }, [200], VALIDA.objeto)],
  [3, () => op('noleidas', 'GET', '/notificaciones/noleidas', { token: de(huespedes).token }, [200], VALIDA.objeto)],
  [3, () => op('mis_reservas', 'GET', '/reservas/mias?pagina=0&tamanio=100', { token: de(huespedes).token }, [200], VALIDA.pagina)],
  [2, () => cotizarYReservar(false)],
  [1, () => cotizarYReservar(true)],
];
const paginada = [
  [22, () => op('buscar_ciudad', 'GET', `/alojamientos?ciudad=${encodeURIComponent(de(CIUDADES))}&pagina=${azar(5)}&tamanio=12`, {}, [200], VALIDA.pagina)],
  [13, () => { const d = 20 + azar(200); return op('buscar_fechas', 'GET', `/alojamientos?ciudad=${encodeURIComponent(de(CIUDADES))}&llegada=${fecha(d)}&salida=${fecha(d + 3)}&huespedes=2&tamanio=12`, {}, [200], VALIDA.pagina); }],
  [5, () => op('buscar_filtros', 'GET', `/alojamientos?tipo=${encodeURIComponent(de(TIPOS))}&precioMinimo=100000&precioMaximo=400000&amenidades=wifi&orden=precio_menor&tamanio=12`, {}, [200], VALIDA.pagina)],
  [12, () => op('disponibles', 'GET', `/alojamientos/disponibles?pagina=${azar(5)}&tamanio=20`, {}, [200], VALIDA.pagina)],
  [3, () => op('mejor_calificados', 'GET', '/alojamientos/disponibles?orden=mejor_calificados&tamanio=20', {}, [200], VALIDA.pagina)],
  ...frontend.slice(4, 10),
  [3, () => op('mis_reservas', 'GET', '/reservas/mias?tamanio=10', { token: de(huespedes).token }, [200], VALIDA.pagina)],
  ...frontend.slice(11),
];
const planes = MEZCLA === 'paginada' ? paginada : frontend;
const totalPeso = planes.reduce((s, p) => s + p[0], 0);
function elegir() { let x = Math.random() * totalPeso; for (const [p, f] of planes) if ((x -= p) < 0) return f(); }
async function cotizarYReservar(reservar) {
  // Fechas propias de cada intento: aquí se mide el camino feliz, no el choque de fechas.
  const n = ranura++; const u = de(huespedes); const dia = 20 + (n % 150) * 4;
  const c = await op('cotizar', 'POST', '/cotizaciones', { token: u.token, cuerpo: { idAlojamiento: 1 + (Math.floor(n / 150) % 4000), fechaEntrada: fecha(dia), fechaSalida: fecha(dia + 3), huespedes: 2 } }, [201], VALIDA.cotizacion);
  if (!reservar || c.estado !== 201) return;
  // El frontend envía Idempotency-Key al reservar (reserva.service.ts), aunque el backend no la usa en esta ruta.
  await op('reservar', 'POST', '/reservas', { token: u.token, cabeceras: { 'Idempotency-Key': randomUUID() }, cuerpo: { idCotizacion: JSON.parse(c.cuerpo).idCotizacion, metodoPago: 'pm_card_visa' } }, [201], VALIDA.reserva);
}

// ---- Modelos de carga --------------------------------------------------------------------
async function cerrado(fases, espera = [1000, 3000], accion = elegir) {
  let objetivo = 0, activos = 0, fin = false; const hilos = [];
  const vu = async id => { while (!fin) { if (id >= objetivo) { await esperar(250); continue; } await accion().catch(() => {}); await esperar(espera[0] + Math.random() * (espera[1] - espera[0])); } };
  for (const f of fases) {
    if (abortado) break;
    nuevaEtapa(f.nombre, f.vus + ' VU'); const desde = objetivo; const t0 = performance.now(); const pasos = Math.max(1, (f.rampa || 0) / 1000);
    for (let i = 1; i <= pasos; i++) { objetivo = Math.round(desde + (f.vus - desde) * i / pasos); while (activos < objetivo) hilos.push(vu(activos++)); if (f.rampa) await esperar(1000); }
    while (performance.now() - t0 < f.seg * 1000 && !abortado) await esperar(250);
    const r = cerrarEtapa();
    if (r.errPct > ABORTAR_ERR_PCT) { abortado = `Etapa ${r.nombre} con ${r.errPct} % de errores (> ${ABORTAR_ERR_PCT} %): se detiene la carga`; console.log(abortado); }
  }
  fin = true; await Promise.race([Promise.all(hilos), esperar(12000)]);
}
async function abierto(nombre, rps, seg, accion = elegir) {
  nuevaEtapa(nombre, rps + ' rps'); const e = etapa; let enVuelo = 0; const t0 = performance.now(); let lanzadas = 0;
  while (!abortado) {
    const t = performance.now() - t0; if (t >= seg * 1000) break;
    const debe = Math.floor(t / 1000 * rps);
    if (debe - lanzadas > 0) e.retrasoMaxMs = Math.max(e.retrasoMaxMs, (debe - lanzadas) / rps * 1000);
    while (lanzadas < debe) { lanzadas++; if (enVuelo >= MAX_EN_VUELO) { e.descartadas++; continue; } enVuelo++; accion().catch(() => {}).finally(() => enVuelo--); }
    await esperar(2);
  }
  const fin = performance.now() + TIMEOUT_MS; while (enVuelo > 0 && performance.now() < fin) await esperar(50);
  return cerrarEtapa();
}

// ---- Validación funcional con un único usuario virtual -----------------------------------------
async function validacion() {
  nuevaEtapa('validacion_1vu', '1 VU'); const fallos = []; const h = huespedes[7];
  const revisar = async (nombre, promesa, condicion) => { const r = await promesa; let ok = false; try { ok = condicion(r, r.cuerpo ? JSON.parse(r.cuerpo) : null); } catch { ok = false; }
    if (!ok) fallos.push(`${nombre}: ${r.estado || r.error} ${String(r.cuerpo).slice(0, 160)}`); console.log(`${ok ? 'OK   ' : 'FALLO'} ${nombre.padEnd(24)} ${r.estado || r.error} ${Math.round(r.ms)} ms`); return r; };
  const d = 40;
  // Cada operación de la mezcla, una vez y en secuencia (cotizar y cotizar+reservar incluidas).
  for (const [, f] of planes) {
    const antes = new Map([...etapa.ops].map(([k, o]) => [k, o.ms.length])); await f();
    for (const [nombre, o] of etapa.ops) {
      if (o.ms.length === (antes.get(nombre) || 0)) continue;
      const ok = o.err === 0; if (!ok) fallos.push(`${nombre}: ${JSON.stringify(o.estados)}`);
      console.log(`${ok ? 'OK   ' : 'FALLO'} ${nombre.padEnd(24)} ${JSON.stringify(o.estados)} ${Math.round(o.ms.at(-1))} ms`);
    }
  }
  await revisar('busqueda_solo_activas', pedir('GET', '/alojamientos?pagina=0&tamanio=100&ciudad=Cali'), (r, j) => r.estado === 200 && j.contenido.length > 0 && j.contenido.every(a => a.estado === 'ACTIVA' && a.ciudad === 'Cali'));
  await revisar('detalle_no_activa_404', pedir('GET', '/alojamientos/4500'), r => r.estado === 404);
  await revisar('perfil_sin_token_401', pedir('GET', '/usuarios/perfil'), r => r.estado === 401);
  await revisar('login_valido', pedir('POST', '/auth/login', { cuerpo: { correo: h.correo, contrasenia: 'Carga#2026' } }), (r, j) => r.estado === 200 && j.token);
  await revisar('login_invalido_401', pedir('POST', '/auth/login', { cuerpo: { correo: h.correo, contrasenia: 'Mala#1' } }), r => r.estado === 401);
  const c = await revisar('cotizar', pedir('POST', '/cotizaciones', { token: h.token, cuerpo: { idAlojamiento: 3999, fechaEntrada: fecha(d), fechaSalida: fecha(d + 3), huespedes: 2 } }), (r, j) => r.estado === 201 && j.noches === 3 && j.total > 0);
  const idc = c.estado === 201 ? JSON.parse(c.cuerpo).idCotizacion : -1;
  const rv = await revisar('reservar', pedir('POST', '/reservas', { token: h.token, cabeceras: { 'Idempotency-Key': randomUUID() }, cuerpo: { idCotizacion: idc, metodoPago: 'pm_card_visa' } }), (r, j) => r.estado === 201 && j.estado === 'CONFIRMADA');
  await revisar('cotizacion_reusada_409', pedir('POST', '/reservas', { token: h.token, cuerpo: { idCotizacion: idc, metodoPago: 'pm_card_visa' } }), r => r.estado === 409);
  await revisar('fechas_ocupadas_409', pedir('POST', '/cotizaciones', { token: huespedes[8].token, cuerpo: { idAlojamiento: 3999, fechaEntrada: fecha(d), fechaSalida: fecha(d + 3), huespedes: 2 } }), r => r.estado === 409);
  const idr = rv.estado === 201 ? JSON.parse(rv.cuerpo).idReserva : -1; const clave = randomUUID();
  await revisar('cancelar', pedir('POST', `/reservas/${idr}/cancelar`, { token: h.token, cabeceras: { 'Idempotency-Key': clave }, cuerpo: { motivo: 'Validación de la prueba de carga' } }), r => r.estado === 200);
  await revisar('cancelar_repetido_replay', pedir('POST', `/reservas/${idr}/cancelar`, { token: h.token, cabeceras: { 'Idempotency-Key': clave }, cuerpo: { motivo: 'Validación de la prueba de carga' } }), r => r.estado === 200 && r.cabeceras['idempotent-replayed'] === 'true');
  await revisar('metricas_actuator', pedir('GET', '/actuator/metrics/tomcat.threads.busy', { token: admin }), r => r.estado === 200);
  cerrarEtapa();
  console.log(fallos.length ? `VALIDACIÓN FALLIDA (${fallos.length}):\n  ${fallos.join('\n  ')}` : 'Validación correcta: todas las comprobaciones pasaron');
  return { fallos };
}

// ---- Escenarios --------------------------------------------------------------------------
const MIN = 60;
const escalones = (process.env.ESCALONES || '10,25,50,75,100,125,150,175,200,225,250,275,300,350,400,500,600,800,1000').split(',').map(Number);
const escenarios = {
  validacion,
  humo: () => cerrado([{ nombre: 'humo', vus: 5, seg: 30 }]),
  carga: () => cerrado([
    { nombre: 'rampa_50', vus: 50, seg: MIN, rampa: 30000 }, { nombre: 'normal_50', vus: 50, seg: 3 * MIN },
    { nombre: 'rampa_200', vus: 200, seg: MIN, rampa: 30000 }, { nombre: 'alta_200', vus: 200, seg: 3 * MIN },
    { nombre: 'rampa_400', vus: 400, seg: MIN, rampa: 30000 }, { nombre: 'punta_400', vus: 400, seg: 3 * MIN }]),
  async estres() {
    let quiebre = null;
    for (const rps of escalones) {
      const r = await abierto(`escalon_${rps}`, rps, MIN);
      if (abortado) { quiebre = abortado; break; }
      if (r.errPct > 20 || r.descartadas > rps * 5) { quiebre = 'Punto de quiebre: se detiene la subida.'; console.log(quiebre); break; }
    }
    // El estrés busca el quiebre: si el corte fue por indisponibilidad, se mide igual la recuperación a baja carga.
    // Si el backend sigue sin responder durante la recuperación, el corte vuelve a actuar y la prueba termina.
    abortado = null; saludFallida = 0; await esperar(5000);
    for (const n of [1, 2]) if (!abortado) await abierto(`recuperacion_${n}`, +process.env.RECUPERACION_RPS || 25, MIN);
    return { quiebre };
  },
  async pico() {
    const base = +process.env.BASE_RPS || 25, p = +process.env.PICO_RPS || 300;
    await abierto('antes', base, MIN); await abierto('pico', p, 30);
    // El pico busca saturar: aunque el corte por indisponibilidad actúe, se mide la recuperación a la carga base.
    const corte = abortado; abortado = null; saludFallida = 0;
    for (let n = 1; n <= (+process.env.PICO_MIN_DESPUES || 3); n++) if (!abortado) await abierto(`despues_${n}`, base, MIN);
    return { cortePorIndisponibilidad: corte };
  },
  async login() {
    const a = () => op('login_admin', 'POST', '/auth/login', { cuerpo: { correo: 'admin@carga.local', contrasenia: 'Carga#2026' } }, [200], VALIDA.token);
    for (const rps of [5, 10, 20, 40, 80, 120, 160, 240]) { const r = await abierto(`login_${rps}`, rps, 30, a); if (r.errPct > 20 || abortado) break; }
  },
  async login_fallido() {
    // Fuerza bruta distribuida: mide si una contraseña mala cuesta CPU y si algo la frena.
    const f = () => op('login_fallido', 'POST', '/auth/login', { cuerpo: { correo: de(huespedes).correo, contrasenia: 'Mala#' + azar(1e6) } }, [401]);
    const p = () => op('busqueda_victima', 'GET', `/alojamientos/${1 + azar(4000)}`, {}, [200], VALIDA.alojamiento);
    await abierto('solo_lecturas', 100, 30, p);
    for (const rps of [50, 100, 200]) { if (abortado) break; await abierto(`ataque_${rps}`, rps + 100, 30, () => Math.random() < 100 / (100 + rps) ? p() : f()); }
  },
  async resistencia() {
    const rps = +process.env.RESISTENCIA_RPS || 30, min = +process.env.RESISTENCIA_MIN || 15;
    // RESISTENCIA_RAMPA=1 sube como lo haría un día normal; sin ella se mide el golpe en frío tras un arranque.
    if (process.env.RESISTENCIA_RAMPA) for (const f of [.2, .4, .6, .8]) { if (abortado) break; await abierto(`rampa_${Math.round(rps * f)}`, Math.round(rps * f), MIN); }
    for (let i = 1; i <= min / 5 && !abortado; i++) await abierto(`tramo_${i * 5}min`, rps, 5 * MIN);
  },
  async concurrencia() {
    // 1. N huéspedes reservan a la vez las mismas fechas del mismo alojamiento: debe ganar exactamente uno.
    nuevaEtapa('doble_reserva', '100 x 20 rondas'); const rondas = []; const rep = {};
    for (let ronda = 0; ronda < 20; ronda++) {
      const id = 3900 + ronda; const dia = 100; const grupo = huespedes.slice(ronda * 100, ronda * 100 + 100);
      const cot = await Promise.all(grupo.map(u => op('cotizar', 'POST', '/cotizaciones', { token: u.token, cuerpo: { idAlojamiento: id, fechaEntrada: fecha(dia), fechaSalida: fecha(dia + 3), huespedes: 2 } }, [201])));
      const res = await Promise.all(grupo.map((u, i) => cot[i].estado === 201 ? op('reservar_mismas_fechas', 'POST', '/reservas', { token: u.token, cabeceras: { 'Idempotency-Key': randomUUID() }, cuerpo: { idCotizacion: JSON.parse(cot[i].cuerpo).idCotizacion, metodoPago: 'pm_card_visa' } }, [201, 409]) : { estado: -1 }));
      const conteo = {}; res.forEach(r => conteo[r.estado] = (conteo[r.estado] || 0) + 1); rondas.push({ alojamiento: id, ...conteo });
      for (const [k, v] of Object.entries(conteo)) rep[k] = (rep[k] || 0) + v;
    }
    const r1 = cerrarEtapa();
    // 2. POST /reservas no aplica Idempotency-Key; lo que impide el doble cobro es que la
    //    cotización es de un solo uso. Se envía la MISMA cotización 30 veces a la vez (con la misma clave, como un
    //    reintento del frontend): debe crearse una sola reserva y un solo pago.
    nuevaEtapa('misma_cotizacion', '30 x 20 rondas'); const misma = []; const paraCancelar = [];
    for (let ronda = 0; ronda < 20; ronda++) {
      const u = huespedes[1000 + ronda]; const dia = 200 + ronda * 5;
      const c = await pedir('POST', '/cotizaciones', { token: u.token, cuerpo: { idAlojamiento: 3800, fechaEntrada: fecha(dia), fechaSalida: fecha(dia + 3), huespedes: 2 } });
      const idc = JSON.parse(c.cuerpo).idCotizacion; const clave = randomUUID();
      const rs = await Promise.all(Array.from({ length: 30 }, () => op('reservar_misma_cotizacion', 'POST', '/reservas', { token: u.token, cabeceras: { 'Idempotency-Key': clave }, cuerpo: { idCotizacion: idc, metodoPago: 'pm_card_visa' } }, [200, 201, 409])));
      const creadas = new Set(rs.filter(x => [200, 201].includes(x.estado)).map(x => JSON.parse(x.cuerpo).idReserva));
      misma.push({ ronda, reservasDistintas: creadas.size, estados: rs.reduce((m, x) => (m[x.estado] = (m[x.estado] || 0) + 1, m), {}) });
      for (const id of creadas) paraCancelar.push({ u, id });
    }
    cerrarEtapa();
    // 3. Idempotencia de la cancelación: la misma cancelación con la misma Idempotency-Key 30 veces a la vez debe
    //    cancelar y reembolsar una sola vez (las demás reciben la respuesta guardada o 409 "en proceso").
    nuevaEtapa('idempotencia_cancelar', '30 x 20 rondas'); const canc = [];
    for (const { u, id } of paraCancelar) {
      const clave = randomUUID();
      const rs = await Promise.all(Array.from({ length: 30 }, () => op('cancelar_misma_clave', 'POST', `/reservas/${id}/cancelar`, { token: u.token, cabeceras: { 'Idempotency-Key': clave }, cuerpo: { motivo: 'Prueba de concurrencia' } }, [200, 409])));
      canc.push({ reserva: id, estados: rs.reduce((m, x) => { const k = x.estado + (x.cabeceras?.['idempotent-replayed'] ? '_replay' : ''); m[k] = (m[k] || 0) + 1; return m; }, {}) });
    }
    cerrarEtapa();
    console.log('Doble reserva por estado:', rep);
    return { rondas, ganadoresPorRonda: rondas.map(r => r[201] || 0), mismaCotizacion: misma, cancelaciones: canc, primeraEtapa: r1?.nombre };
  },
};

if (!escenarios[escenario]) throw new Error('Escenario desconocido: ' + escenario);
process.on('SIGINT', () => { abortado = 'Detenida manualmente (Ctrl+C)'; console.log('\n' + abortado); guardar({ detenidaManualmente: true }); process.exit(130); });
function guardar(extra) {
  clearInterval(telemetria);
  if (etapa) cerrarEtapa();
  mkdirSync(salida, { recursive: true });
  const archivo = `${salida}/${escenario}${process.env.SUFIJO || ''}.json`;
  writeFileSync(archivo, JSON.stringify({ escenario, mezcla: MEZCLA, base: BASE, fecha: new Date().toISOString(), abortado, etapas: etapas.map(e => e.resultado).filter(Boolean), telemetria: serie, extra }, null, 1));
  console.log(`Resultados en ${archivo}`);
}
console.log(`Escenario ${escenario} (mezcla ${MEZCLA}) contra ${BASE} (${new Date().toISOString()})`);
const extra = await escenarios[escenario]();
guardar(extra);
process.exit(escenario === 'validacion' && extra.fallos.length ? 1 : abortado ? 2 : 0);
