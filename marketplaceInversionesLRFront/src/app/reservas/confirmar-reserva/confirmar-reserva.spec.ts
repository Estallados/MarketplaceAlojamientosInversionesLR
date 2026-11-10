import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { ConfirmarReserva } from './confirmar-reserva';
import { Cotizacion } from '../../cotizacion/models/cotizacion';
import { Reserva } from '../models/reserva';
import { clasificarErrorReserva } from '../validaciones/errores-reserva';
import { diaSiguiente, hoyLocal } from '../../disponibilidad/validaciones/fechas-estadia';
import { API_URL } from '../../core/services/api-url';
import { routes } from '../../app.routes';

// ======================================
// HU-22 CREAR RESERVA CON BLOQUEO AUTOMÁTICO DE FECHAS
// Pantalla real con los servicios HTTP reales; solo el backend se
// sustituye con HttpTestingController (prueba unitaria, no integración).
// ======================================

describe('ConfirmarReserva (HU-22)', () => {

  const baseUrl = 'http://localhost:8081';

  const urlCotizaciones = `${baseUrl}/cotizaciones`;

  const urlReservas = `${baseUrl}/reservas`;

  const urlDisponibilidad = `${baseUrl}/alojamientos/7/disponibilidad/consultar`;

  let http: HttpTestingController;

  let harness: RouterTestingHarness;

  /** Fecha AAAA-MM-DD a n días de hoy (hora local). */
  function enDias(n: number): string {
    let fecha = hoyLocal();
    for (let i = 0; i < n; i++) {
      fecha = diaSiguiente(fecha);
    }
    return fecha;
  }

  const llegada = enDias(10);

  const salida = enDias(13);

  const cotizacion: Cotizacion = {
    idCotizacion: 42,
    idAlojamiento: 7,
    fechaEntrada: llegada,
    fechaSalida: salida,
    huespedes: 2,
    noches: 3,
    precioNoche: 200000,
    precioBase: 600000,
    descuento: 0,
    porcentajeLimpieza: 5,
    valorLimpieza: 30000,
    porcentajeServicio: 10,
    valorServicio: 60000,
    valorAdicionales: 50000,
    total: 740000,
    serviciosAdicionales: [{ idServicio: 3, nombre: 'Desayuno', costo: 50000 }],
    fechaExpiracion: '2026-11-07T11:00:00',
    idReserva: null
  };

  const reserva: Reserva = {
    idReserva: 15,
    fechaEntrada: llegada,
    fechaSalida: salida,
    noches: 3,
    huespedes: 2,
    estado: 'CONFIRMADA',
    total: 740000,
    idHuesped: 12,
    idAlojamiento: 7,
    idCotizacion: 42,
    tituloAlojamiento: 'Apartamento en Chapinero',
    fechaCreacion: '2026-11-07T10:35:00'
  };

  /** JWT sin firma válida pero con exp futuro: SessionService solo revisa el vencimiento. */
  function tokenVigente(): string {
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    return `e30.${payload}.firma`;
  }

  beforeEach(async () => {

    // Sesión de huésped: la ruta exige el rol HUESPED.
    sessionStorage.setItem('token', tokenVigente());
    sessionStorage.setItem('rol', 'HUESPED');
    sessionStorage.setItem('correo', 'huesped@correo.com');

    await TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl }
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);

    harness = await RouterTestingHarness.create();
  });

  afterEach(() => {
    // Ninguna petición sin responder ni inesperada.
    http.verify();
    sessionStorage.clear();
  });

  // ======================================
  // UTILIDADES
  // ======================================

  async function abrir(
    url = `/alojamientos/7/reservar?llegada=${llegada}&salida=${salida}`
  ): Promise<void> {
    await harness.navigateByUrl(url, ConfirmarReserva);
    await estable();
  }

  /** Abre la pantalla y responde la cotización. */
  async function abrirConCotizacion(): Promise<void> {
    await abrir();
    pedidoCotizacion().flush(cotizacion);
    await estable();
  }

  function elemento(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  async function estable(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function existe(selector: string): boolean {
    return elemento().querySelector(selector) !== null;
  }

  function texto(selector: string): string {
    return elemento().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function boton(selector: string): HTMLButtonElement {
    return elemento().querySelector<HTMLButtonElement>(selector)!;
  }

  async function confirmar(): Promise<void> {
    boton('[data-confirmar]').click();
    await estable();
  }

  function pedidoCotizacion(): TestRequest {
    return http.expectOne(r => r.method === 'POST' && r.url === urlCotizaciones);
  }

  function pedidoReserva(): TestRequest {
    return http.expectOne(r => r.method === 'POST' && r.url === urlReservas);
  }

  function pedidoDisponibilidad(): TestRequest {
    return http.expectOne(r => r.method === 'GET' && r.url === urlDisponibilidad);
  }

  function rechazar(req: TestRequest, status: number, message: string): void {
    req.flush({ message }, { status, statusText: 'Error' });
  }

  /** Solo el número, sin separadores ni símbolo de moneda. */
  function digitos(valor: string): string {
    return valor.replace(/\D/g, '');
  }

  // ======================================
  // CA-070 CREAR LA RESERVA CON EL MONTO COTIZADO
  // ======================================

  it('CA-070 cotiza con el endpoint real y muestra el resumen de la cotización vigente', async () => {

    await abrir();

    const req = pedidoCotizacion();
    expect(req.request.body).toEqual({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida });

    req.flush(cotizacion);
    await estable();

    expect(existe('[data-resumen]')).toBe(true);
    expect(texto('[data-huespedes]')).toBe('2');
    expect(texto('[data-desglose]')).toContain('Desayuno');
    expect(digitos(texto('[data-total]'))).toBe('740000');
    expect(texto('[data-expiracion]')).toContain('11:00');
    expect(boton('[data-confirmar]').disabled).toBe(false);
  });

  it('CA-070 al confirmar crea la reserva con POST /reservas y muestra la confirmación con el monto cotizado', async () => {

    await abrirConCotizacion();
    await confirmar();

    pedidoReserva().flush(reserva, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(existe('[data-reserva]')).toBe(true);
    expect(texto('[data-titulo-reserva]')).toContain('Tu reserva quedó confirmada');
    expect(texto('[data-id-reserva]')).toBe('#15');
    expect(digitos(texto('[data-total-reserva]'))).toBe('740000');
    expect(texto('[data-alojamiento]')).toBe('Apartamento en Chapinero');
    expect(existe('[data-confirmar]')).toBe(false);
    expect(existe('[data-error-reserva]')).toBe(false);
  });

  // ======================================
  // CA-071 ALOJAMIENTO NO ACTIVA
  // ======================================

  it('CA-071 informa que el alojamiento no está disponible para reservar y no presenta la reserva como creada', async () => {

    await abrirConCotizacion();
    await confirmar();

    rechazar(pedidoReserva(), 409, 'El alojamiento no está disponible para nuevas reservas (estado PAUSADA).');
    await estable();

    expect(elemento().querySelector('[data-error-reserva]')?.getAttribute('data-tipo')).toBe('no-activo');
    expect(texto('[data-mensaje-error]')).toBe('Este alojamiento no está disponible para reservar en este momento.');
    expect(texto('[data-detalle-error]')).toContain('estado PAUSADA');
    expect(texto('[data-error-reserva]')).toContain('No se creó ninguna reserva.');
    expect(existe('[data-reserva]')).toBe(false);
    // No se puede volver a confirmar la misma cotización.
    expect(existe('[data-confirmar]')).toBe(false);
  });

  it('CA-071 también muestra el motivo cuando el rechazo llega al cotizar y no permite confirmar', async () => {

    await abrir();

    rechazar(pedidoCotizacion(), 409, 'El alojamiento no está disponible para nuevas reservas (estado EN_REVISION).');
    await estable();

    expect(texto('[data-mensaje-error]')).toBe('Este alojamiento no está disponible para reservar en este momento.');
    expect(existe('[data-resumen]')).toBe(false);
    expect(existe('[data-confirmar]')).toBe(false);
  });

  // ======================================
  // CA-072 PERÍODOS SUPERPUESTOS Y SOLICITUDES SIMULTÁNEAS
  // ======================================

  it('CA-072 informa que las fechas dejaron de estar disponibles y permite volver a elegir fechas', async () => {

    await abrirConCotizacion();
    await confirmar();

    rechazar(pedidoReserva(), 409, 'El período ya no está disponible: otra reserva lo tomó.');
    await estable();

    expect(elemento().querySelector('[data-error-reserva]')?.getAttribute('data-tipo')).toBe('fechas-ocupadas');
    expect(texto('[data-mensaje-error]')).toContain('Las fechas seleccionadas dejaron de estar disponibles');
    expect(existe('[data-reserva]')).toBe(false);
    expect(existe('[data-confirmar]')).toBe(false);

    const enlace = elemento().querySelector<HTMLAnchorElement>('[data-elegir-fechas]')!;
    expect(enlace.getAttribute('href')).toBe(
      `/alojamientos/7/consultar-disponibilidad?llegada=${llegada}&salida=${salida}`
    );
  });

  it('CA-072 un doble clic sobre Confirmar envía una sola solicitud de reserva', async () => {

    await abrirConCotizacion();

    const confirmarBtn = boton('[data-confirmar]');
    confirmarBtn.click();
    confirmarBtn.click();
    await estable();

    // Mientras la petición está pendiente el botón queda deshabilitado.
    expect(boton('[data-confirmar]').disabled).toBe(true);
    expect(texto('[data-confirmar]')).toBe('Confirmando reserva...');
    boton('[data-confirmar]').click();
    await estable();

    // expectOne falla si hubo más de una petición.
    pedidoReserva().flush(reserva, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(existe('[data-reserva]')).toBe(true);
  });

  // ======================================
  // CA-074 REVALIDAR AL CONFIRMAR
  // ======================================

  it('CA-074 no da por vigente una consulta previa: confirma con POST /reservas y muestra el rechazo si el período ya no está libre', async () => {

    await abrirConCotizacion();

    // No hay consulta de disponibilidad local antes de confirmar: decide el backend.
    http.expectNone(r => r.url === urlDisponibilidad);

    await confirmar();

    const req = pedidoReserva();
    http.expectNone(r => r.url === urlDisponibilidad);

    rechazar(req, 409, 'El período ya no está disponible: otra reserva lo tomó.');
    await estable();

    expect(texto('[data-mensaje-error]')).toContain('dejaron de estar disponibles');
    expect(existe('[data-reserva]')).toBe(false);
    // Tras el rechazo no se consulta la disponibilidad ni se simula un bloqueo.
    http.expectNone(r => r.url === urlDisponibilidad);
  });

  // ======================================
  // CA-075 CONFIRMACIÓN INMEDIATA
  // ======================================

  it('CA-075 confirma en un solo paso, sin aprobación del anfitrión, y muestra el estado CONFIRMADA del backend', async () => {

    await abrirConCotizacion();

    expect(texto('main')).toContain('no necesita aprobación del anfitrión');

    await confirmar();
    pedidoReserva().flush(reserva, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(elemento().querySelector('[data-reserva]')?.getAttribute('data-estado')).toBe('CONFIRMADA');
    expect(texto('[data-estado-reserva]')).toBe('Confirmada');
  });

  it('CA-075 diferencia una reserva que el backend no dejó CONFIRMADA', async () => {

    await abrirConCotizacion();
    await confirmar();

    pedidoReserva().flush({ ...reserva, estado: 'PENDIENTE_PAGO' }, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(texto('[data-titulo-reserva]')).toBe('Tu reserva no quedó confirmada.');
    expect(texto('[data-estado-reserva]')).toBe('Pendiente de pago');
  });

  // ======================================
  // CA-076 BLOQUEO AUTOMÁTICO DEL PERÍODO
  // ======================================

  it('CA-076 después de confirmar vuelve a consultar la disponibilidad real y muestra el período como bloqueado', async () => {

    await abrirConCotizacion();
    await confirmar();

    pedidoReserva().flush(reserva, { status: 201, statusText: 'Created' });
    await estable();

    expect(existe('[data-bloqueo="consultando"]')).toBe(true);

    const req = pedidoDisponibilidad();
    expect(req.request.params.get('llegada')).toBe(llegada);
    expect(req.request.params.get('salida')).toBe(salida);

    req.flush({
      idAlojamiento: 7,
      fechaEntrada: llegada,
      fechaSalida: salida,
      disponible: false,
      motivo: 'El período incluye fechas reservadas.'
    });
    await estable();

    expect(existe('[data-bloqueo="bloqueado"]')).toBe(true);
    expect(texto('[data-bloqueo="bloqueado"]')).toContain('quedaron bloqueadas');
  });

  it('CA-076 no simula el bloqueo: si el backend aún muestra las fechas libres, lo indica tal cual', async () => {

    await abrirConCotizacion();
    await confirmar();

    pedidoReserva().flush(reserva, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: true });
    await estable();

    expect(existe('[data-bloqueo="bloqueado"]')).toBe(false);
    expect(existe('[data-bloqueo="disponible"]')).toBe(true);
  });

  // ======================================
  // CA-086 MONTO IGUAL AL DE LA COTIZACIÓN
  // ======================================

  it('CA-086 envía solo la referencia de la cotización, sin monto, aunque se altere el precio mostrado', async () => {

    await abrirConCotizacion();

    // Una modificación visual del total no cambia lo que se envía.
    elemento().querySelector('[data-total]')!.textContent = '$ 1';

    await confirmar();

    const req = pedidoReserva();
    expect(req.request.body).toEqual({ idCotizacion: 42 });
    expect(req.request.body).not.toHaveProperty('total');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();

    req.flush(reserva, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(digitos(texto('[data-total-reserva]'))).toBe('740000');
  });

  it('CA-086 muestra el total que devuelve el backend para la reserva, sin recalcularlo', async () => {

    await abrirConCotizacion();
    await confirmar();

    pedidoReserva().flush({ ...reserva, total: 740000.5 }, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(digitos(texto('[data-total-reserva]'))).toBe('7400005');
  });

  // ======================================
  // RNF-008 REINTENTOS SIN DUPLICADOS (CA-140, CA-141)
  // ======================================

  it('RNF-008 tras un error de conexión permite reintentar con la misma Idempotency-Key y no reenvía solo', async () => {

    await abrirConCotizacion();
    await confirmar();

    const primero = pedidoReserva();
    const clave = primero.request.headers.get('Idempotency-Key');
    primero.error(new ProgressEvent('error'), { status: 0, statusText: '' });
    await estable();

    expect(texto('[data-mensaje-error]')).toContain('No se pudo conectar con el servidor');
    expect(existe('[data-reserva]')).toBe(false);
    // No hay reintento automático.
    http.expectNone(r => r.url === urlReservas);

    expect(texto('[data-confirmar]')).toBe('Reintentar');
    await confirmar();

    const segundo = pedidoReserva();
    expect(segundo.request.headers.get('Idempotency-Key')).toBe(clave);

    segundo.flush(reserva, {
      status: 201,
      statusText: 'Created',
      headers: { 'Idempotent-Replayed': 'true' }
    });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(existe('[data-repetida]')).toBe(true);
  });

  it('RNF-008 usa una clave nueva después de una respuesta definitiva del backend', async () => {

    await abrirConCotizacion();
    await confirmar();

    const primero = pedidoReserva();
    const clave = primero.request.headers.get('Idempotency-Key');
    primero.flush(
      { message: 'El pago fue rechazado: La tarjeta no tiene fondos suficientes. La reserva no quedó pagada.', dato: { ...reserva, estado: 'CANCELADA' } },
      { status: 402, statusText: 'Payment Required' }
    );
    await estable();

    expect(texto('[data-mensaje-error]')).toBe('El pago no se confirmó y la reserva no quedó pagada.');

    await confirmar();
    const segundo = pedidoReserva();
    expect(segundo.request.headers.get('Idempotency-Key')).not.toBe(clave);
    rechazar(segundo, 500, 'Ocurrió un error inesperado.');
    await estable();
  });

  // ======================================
  // OTROS ERRORES Y DATOS DE ENTRADA
  // ======================================

  it('pide una cotización nueva cuando la anterior venció o ya se usó', async () => {

    await abrirConCotizacion();
    await confirmar();

    rechazar(pedidoReserva(), 409, 'La cotización venció o ya se usó; solicita una cotización nueva.');
    await estable();

    expect(texto('[data-mensaje-error]')).toContain('La cotización venció o ya se usó');
    expect(existe('[data-confirmar]')).toBe(false);

    boton('[data-recotizar]').click();
    await estable();

    pedidoCotizacion().flush({ ...cotizacion, idCotizacion: 43 });
    await estable();

    expect(existe('[data-error-reserva]')).toBe(false);
    await confirmar();
    const req = pedidoReserva();
    expect(req.request.body).toEqual({ idCotizacion: 43 });
    rechazar(req, 409, 'El período ya no está disponible: otra reserva lo tomó.');
    await estable();
  });

  it('envía los huéspedes de la URL al cotizar', async () => {

    await abrir(`/alojamientos/7/reservar?llegada=${llegada}&salida=${salida}&huespedes=3`);
    const req = pedidoCotizacion();
    expect(req.request.body).toEqual({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, huespedes: 3 });
    req.flush({ ...cotizacion, huespedes: 3 });
    await estable();

    expect(texto('[data-huespedes]')).toBe('3');
  });

  it('no cotiza sin fechas válidas y pide elegirlas', async () => {

    await abrir('/alojamientos/7/reservar');
    expect(existe('[data-sin-datos]')).toBe(true);
    http.expectNone(urlCotizaciones);
  });

  it('distingue errores de validación, sesión, conexión y servidor', () => {

    const error = (status: number, message = '') =>
      clasificarErrorReserva(new HttpErrorResponse({ status, error: message ? { message } : null }));

    expect(error(400, 'Debes indicar la cotización a reservar.')).toEqual({
      tipo: 'validacion',
      mensaje: 'Los datos de la reserva no son válidos.',
      detalle: 'Debes indicar la cotización a reservar.'
    });
    expect(error(401).tipo).toBe('sesion');
    expect(error(403, 'No puedes reservar tu propio alojamiento.').tipo).toBe('permiso');
    expect(error(404, 'La cotización no existe.').tipo).toBe('no-encontrado');
    expect(error(409, 'El alojamiento no está disponible en esas fechas.').tipo).toBe('fechas-ocupadas');
    expect(error(409, 'La petición con esa clave sigue en proceso.').tipo).toBe('en-proceso');
    expect(error(422).tipo).toBe('clave-reutilizada');
    expect(error(0).tipo).toBe('conexion');
    expect(error(500).tipo).toBe('servidor');
    expect(error(502).tipo).toBe('pago-rechazado');
    expect(clasificarErrorReserva(new Error('x')).tipo).toBe('desconocido');
  });
});
