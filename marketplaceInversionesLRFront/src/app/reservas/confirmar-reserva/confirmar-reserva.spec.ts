import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { ConfirmarReserva, ESPERA_RECOTIZAR_MS } from './confirmar-reserva';
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

  const urlAlojamiento = `${baseUrl}/alojamientos/7`;

  const urlConfiguracionPago = `${baseUrl}/pagos/configuracion`;

  /** GET /pagos/configuracion del backend local (modo SIMULADO, HU-27). */
  const configuracionPago = {
    modo: 'SIMULADO',
    clavePublica: null,
    moneda: 'cop',
    metodosPrueba: [
      { metodo: 'pm_card_visa', descripcion: 'Visa aprobada', resultado: 'APROBADO' },
      { metodo: 'pm_card_mastercard', descripcion: 'Mastercard aprobada', resultado: 'APROBADO' },
      { metodo: 'pm_card_chargeDeclined', descripcion: 'Rechazo genérico', resultado: 'RECHAZADO' },
      { metodo: 'pm_card_chargeDeclinedInsufficientFunds', descripcion: 'Fondos insuficientes', resultado: 'RECHAZADO' },
      { metodo: 'pm_card_error', descripcion: 'Falla de la pasarela (solo simulador)', resultado: 'ERROR' }
    ]
  };

  /** Alojamiento que devuelve GET /alojamientos/7 (capacidad y servicios, HU-31). */
  const alojamiento = {
    idAlojamiento: 7,
    titulo: 'Apartamento en Chapinero',
    capacidad: 4,
    serviciosAdicionales: [
      { idServicio: 3, nombre: 'Desayuno', costo: 50000 },
      { idServicio: 4, nombre: 'Traslado', costo: 80000 }
    ]
  };

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
    // Capacidad y servicios del alojamiento (HU-31); solo se piden si hay datos para cotizar.
    http
      .match(r => r.method === 'GET' && r.url === urlAlojamiento)
      .forEach(r => r.flush(alojamiento));
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

  /** Escribe en un campo del modal como lo haría el usuario. */
  function escribir(selector: string, valor: string): void {
    const campo = elemento().querySelector<HTMLInputElement>(selector)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  /** Escribe los datos de una tarjeta de prueba en el modal de pago (HU-27). */
  async function escribirTarjeta(numero: string, vencimiento = '12/40', cvc = '123'): Promise<void> {
    escribir('[data-numero]', numero);
    escribir('[data-vencimiento]', vencimiento);
    escribir('[data-cvc]', cvc);
    await estable();
  }

  /**
   * Pulsa "Confirmar reserva". Si se abre el modal de pago (HU-27), responde su configuración,
   * escribe la tarjeta de prueba indicada (por defecto la Visa aprobada) y pulsa "Pagar".
   */
  async function confirmar(numero = '4242 4242 4242 4242'): Promise<void> {
    boton('[data-confirmar]').click();
    await estable();
    if (!existe('[data-pago]')) {
      return; // reintento directo tras un error de conexión
    }
    http
      .match(r => r.method === 'GET' && r.url === urlConfiguracionPago)
      .forEach(r => r.flush(configuracionPago));
    await estable();
    await escribirTarjeta(numero);
    boton('[data-pagar]').click();
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

  it('CA-072 no confunde otro conflicto del backend con fechas ocupadas ni afirma que no se creó la reserva', async () => {

    await abrirConCotizacion();
    await confirmar();

    rechazar(pedidoReserva(), 409, 'El registro ya existe. Actualiza la página e intenta de nuevo.');
    await estable();

    expect(elemento().querySelector('[data-error-reserva]')?.getAttribute('data-tipo')).toBe('conflicto');
    expect(texto('[data-mensaje-error]')).not.toContain('dejaron de estar disponibles');
    expect(texto('[data-detalle-error]')).toContain('El registro ya existe');
    expect(texto('[data-error-reserva]')).not.toContain('No se creó ninguna reserva.');
    expect(existe('[data-sin-certeza]')).toBe(true);
    expect(existe('[data-reserva]')).toBe(false);
    expect(existe('[data-confirmar]')).toBe(false);
    expect(texto('[data-elegir-fechas]')).toBe('Revisar disponibilidad');
  });

  it('CA-072 un doble clic sobre Pagar envía una sola solicitud de reserva', async () => {

    await abrirConCotizacion();

    // "Confirmar reserva" abre el modal de pago (HU-27).
    boton('[data-confirmar]').click();
    await estable();
    http.expectOne(r => r.method === 'GET' && r.url === urlConfiguracionPago).flush(configuracionPago);
    await estable();
    await escribirTarjeta('4242 4242 4242 4242');

    const pagar = boton('[data-pagar]');
    pagar.click();
    pagar.click();
    await estable();

    // Mientras la petición está pendiente el botón queda deshabilitado.
    expect(boton('[data-pagar]').disabled).toBe(true);
    expect(texto('[data-pagar]')).toBe('Procesando pago...');
    boton('[data-pagar]').click();
    await estable();

    // expectOne falla si hubo más de una petición.
    pedidoReserva().flush(reserva, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(existe('[data-reserva]')).toBe(true);
    expect(existe('[data-pago]')).toBe(false);
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
    expect(req.request.body).toEqual({ idCotizacion: 42, metodoPago: 'pm_card_visa' });
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
    expect(req.request.body).toEqual({ idCotizacion: 43, metodoPago: 'pm_card_visa' });
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
    expect(error(409, 'El registro ya existe. Actualiza la página e intenta de nuevo.').tipo).toBe('conflicto');
    expect(error(422).tipo).toBe('clave-reutilizada');
    expect(error(0).tipo).toBe('conexion');
    expect(error(500).tipo).toBe('servidor');
    expect(error(502).tipo).toBe('pago-rechazado');
    expect(clasificarErrorReserva(new Error('x')).tipo).toBe('desconocido');
  });

  // ======================================
  // HU-31 VER COTIZACIÓN DE LA ESTADÍA Y AGREGAR SERVICIOS ADICIONALES
  // ======================================

  /** Cotización del backend con los servicios del alojamiento (serviciosDisponibles). */
  const cotizacionSinServicios: Cotizacion = {
    ...cotizacion,
    valorAdicionales: 0,
    total: 690000,
    serviciosAdicionales: [],
    serviciosDisponibles: alojamiento.serviciosAdicionales
  };

  /** Marca o desmarca un servicio por su nombre. */
  function servicio(nombre: string): HTMLInputElement {
    const etiqueta = Array.from(elemento().querySelectorAll<HTMLLabelElement>('[data-servicio]'))
      .find(l => l.textContent?.includes(nombre))!;
    return etiqueta.querySelector('input')!;
  }

  /** Ejecuta un cambio con temporizadores simulados y deja pasar la espera antes de recotizar. */
  async function cambiarYEsperar(cambio: () => void): Promise<void> {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    try {
      cambio();
      harness.detectChanges();
      vi.advanceTimersByTime(ESPERA_RECOTIZAR_MS);
    } finally {
      vi.useRealTimers();
    }
    await estable();
  }

  async function abrirSinServicios(url?: string): Promise<void> {
    await abrir(url);
    pedidoCotizacion().flush(cotizacionSinServicios);
    await estable();
  }

  it('CA-179 muestra noches, precio base, descuento, limpieza, tarifa de servicio, cada servicio con su costo y el total', async () => {

    await abrir();
    pedidoCotizacion().flush({
      ...cotizacion,
      descuento: 60000,
      valorAdicionales: 130000,
      total: 760000,
      serviciosAdicionales: alojamiento.serviciosAdicionales
    });
    await estable();

    const desglose = texto('[data-desglose]');
    expect(desglose).toContain('3 noches');
    expect(digitos(desglose)).toContain('600000'); // precio base
    expect(desglose).toContain('Descuento');
    expect(digitos(desglose)).toContain('60000');
    expect(desglose).toContain('Limpieza (5 %)');
    expect(desglose).toContain('Tarifa de servicio (10 %)');
    expect(desglose).toContain('Desayuno');
    expect(desglose).toContain('Traslado');
    expect(digitos(desglose)).toContain('80000');
    expect(digitos(texto('[data-total]'))).toBe('760000');
  });

  it('CA-080 el total es el del backend y coincide con la suma de los componentes mostrados', async () => {

    await abrirConCotizacion();

    const componentes = Array.from(elemento().querySelectorAll('[data-desglose] dd'))
      .map(dd => (dd.textContent ?? '').trim());
    const suma = componentes.reduce((total, valor) => {
      const numero = Number(valor.replace(/\D/g, ''));
      return valor.startsWith('−') ? total - numero : total + numero;
    }, 0);

    // precio base 600000 + limpieza 30000 + servicio 60000 + desayuno 50000
    expect(suma).toBe(740000);
    expect(digitos(texto('[data-total]'))).toBe(String(suma));
  });

  it('CA-084 muestra los porcentajes de limpieza y servicio que aplicó el backend, sin recalcularlos', async () => {

    await abrir();
    pedidoCotizacion().flush({
      ...cotizacionSinServicios,
      porcentajeLimpieza: 7.5,
      valorLimpieza: 45000,
      porcentajeServicio: 12,
      valorServicio: 72000,
      total: 717000
    });
    await estable();

    const desglose = texto('[data-desglose]');
    expect(desglose).toContain('Limpieza (7.5 %)');
    expect(digitos(desglose)).toContain('45000');
    expect(desglose).toContain('Tarifa de servicio (12 %)');
    expect(digitos(desglose)).toContain('72000');
    expect(digitos(texto('[data-total]'))).toBe('717000');
  });

  it('CA-183 lista los servicios adicionales del alojamiento con su costo para elegirlos', async () => {

    await abrirSinServicios();

    const servicios = elemento().querySelectorAll('[data-servicio]');
    expect(servicios.length).toBe(2);
    expect(texto('[data-personalizar]')).toContain('Servicio opcional');
    expect(digitos(servicios[1].textContent ?? '')).toBe('80000');
    expect(servicio('Desayuno').checked).toBe(false);
  });

  it('CA-183 al elegir un servicio recotiza con el backend y el total incluye solo los elegidos', async () => {

    await abrirSinServicios();

    await cambiarYEsperar(() => servicio('Desayuno').click());

    const req = pedidoCotizacion();
    expect(req.request.body).toEqual({
      idAlojamiento: 7,
      fechaEntrada: llegada,
      fechaSalida: salida,
      serviciosSeleccionados: [3]
    });
    req.flush({ ...cotizacion, idCotizacion: 43, serviciosDisponibles: alojamiento.serviciosAdicionales });
    await estable();

    expect(texto('[data-desglose]')).toContain('Desayuno');
    expect(texto('[data-desglose]')).not.toContain('Traslado');
    expect(digitos(texto('[data-total]'))).toBe('740000');
    expect(servicio('Desayuno').checked).toBe(true);
  });

  it('CA-183 al quitar un servicio recotiza sin él y el total deja de incluirlo', async () => {

    await abrirSinServicios();
    await cambiarYEsperar(() => servicio('Desayuno').click());
    pedidoCotizacion().flush({ ...cotizacion, serviciosDisponibles: alojamiento.serviciosAdicionales });
    await estable();

    await cambiarYEsperar(() => servicio('Desayuno').click());
    const req = pedidoCotizacion();
    expect(req.request.body.serviciosSeleccionados).toBeUndefined();
    req.flush(cotizacionSinServicios);
    await estable();

    expect(texto('[data-desglose]')).not.toContain('Desayuno');
    expect(digitos(texto('[data-total]'))).toBe('690000');
  });

  it('CA-183 mientras recotiza no deja confirmar y varios clics seguidos piden una sola cotización', async () => {

    await abrirSinServicios();

    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    try {
      servicio('Desayuno').click();
      servicio('Traslado').click();
      harness.detectChanges();

      // Antes de que llegue la cotización nueva no se puede confirmar la anterior.
      expect(boton('[data-confirmar]').disabled).toBe(true);
      expect(existe('[data-recotizando]')).toBe(true);
      http.expectNone(r => r.method === 'POST' && r.url === urlCotizaciones);

      vi.advanceTimersByTime(ESPERA_RECOTIZAR_MS);
    } finally {
      vi.useRealTimers();
    }
    await estable();

    const req = pedidoCotizacion();
    expect(req.request.body.serviciosSeleccionados).toEqual([3, 4]);
    req.flush({
      ...cotizacion,
      idCotizacion: 44,
      valorAdicionales: 130000,
      total: 820000,
      serviciosAdicionales: alojamiento.serviciosAdicionales,
      serviciosDisponibles: alojamiento.serviciosAdicionales
    });
    await estable();

    expect(boton('[data-confirmar]').disabled).toBe(false);
    expect(texto('[data-confirmar]')).toContain('820');

    // Se confirma la cotización nueva, no la anterior.
    await confirmar();
    expect(pedidoReserva().request.body).toEqual({ idCotizacion: 44, metodoPago: 'pm_card_visa' });
  });

  it('CA-183 indica cuando el alojamiento no tiene servicios adicionales', async () => {

    await harness.navigateByUrl(`/alojamientos/7/reservar?llegada=${llegada}&salida=${salida}`, ConfirmarReserva);
    http.expectOne(r => r.method === 'GET' && r.url === urlAlojamiento).flush({ ...alojamiento, serviciosAdicionales: [] });
    pedidoCotizacion().flush({ ...cotizacionSinServicios, serviciosDisponibles: [] });
    await estable();

    expect(existe('[data-servicio]')).toBe(false);
    expect(texto('[data-sin-servicios]')).toContain('no tiene servicios adicionales');
  });

  it('CA-083 rechaza por capacidad con el motivo del backend y permite elegir menos huéspedes', async () => {

    await abrir(`/alojamientos/7/reservar?llegada=${llegada}&salida=${salida}&huespedes=6`);
    rechazar(pedidoCotizacion(), 400, 'La cantidad de huéspedes (6) supera la capacidad del alojamiento (4).');
    await estable();

    expect(existe('[data-resumen]')).toBe(false);
    expect(texto('[data-error-reserva]')).toContain('supera la capacidad del alojamiento (4)');
    expect(existe('[data-confirmar]')).toBe(false);
    expect(texto('[data-personalizar]')).toContain('Capacidad máxima: 4 huéspedes');

    const selector = elemento().querySelector<HTMLSelectElement>('[data-huespedes-select]')!;
    expect(selector.value).toBe('6');

    await cambiarYEsperar(() => {
      selector.value = '2';
      selector.dispatchEvent(new Event('change'));
    });

    const req = pedidoCotizacion();
    expect(req.request.body.huespedes).toBe(2);
    req.flush(cotizacionSinServicios);
    await estable();

    expect(existe('[data-error-reserva]')).toBe(false);
    expect(texto('[data-huespedes]')).toBe('2');
    expect(boton('[data-confirmar]').disabled).toBe(false);
  });

  it('CA-083 rechaza la cotización si las fechas no están disponibles e informa el motivo', async () => {

    await abrir();
    rechazar(pedidoCotizacion(), 409, 'El alojamiento no está disponible en esas fechas.');
    await estable();

    expect(existe('[data-resumen]')).toBe(false);
    expect(elemento().querySelector('[data-error-reserva]')?.getAttribute('data-tipo')).toBe('fechas-ocupadas');
    expect(existe('[data-elegir-fechas]')).toBe(true);
  });

  it('CA-082 con la salida igual o anterior a la llegada no cotiza e indica que las fechas no son válidas', async () => {

    await abrir(`/alojamientos/7/reservar?llegada=${salida}&salida=${salida}`);
    http.expectNone(r => r.url === urlCotizaciones);
    expect(texto('[data-sin-datos]')).toContain('no son válidas');
    expect(existe('[data-personalizar]')).toBe(false);

    TestBed.inject(HttpTestingController).verify();
    await abrir(`/alojamientos/7/reservar?llegada=${salida}&salida=${llegada}`);
    http.expectNone(r => r.url === urlCotizaciones);
    expect(texto('[data-sin-datos]')).toContain('no son válidas');
  });

  it('el selector de huéspedes va de 1 a la capacidad y envía la cantidad elegida', async () => {

    await abrirSinServicios();

    const opciones = Array.from(elemento().querySelectorAll<HTMLOptionElement>('[data-huespedes-select] option'))
      .map(o => o.value);
    expect(opciones).toEqual(['1', '2', '3', '4']);

    const selector = elemento().querySelector<HTMLSelectElement>('[data-huespedes-select]')!;
    await cambiarYEsperar(() => {
      selector.value = '4';
      selector.dispatchEvent(new Event('change'));
    });
    const req = pedidoCotizacion();
    expect(req.request.body.huespedes).toBe(4);
    req.flush({ ...cotizacionSinServicios, huespedes: 4 });
    await estable();
    expect(texto('[data-huespedes]')).toBe('4');
  });

  it('después de crear la reserva ya no se pueden cambiar huéspedes ni servicios', async () => {

    await abrirConCotizacion();
    await confirmar();
    pedidoReserva().flush(reserva);
    await estable();
    pedidoDisponibilidad().flush({ disponible: false });
    await estable();

    expect(existe('[data-personalizar]')).toBe(false);
  });

  // ======================================
  // HU-27 PAGO DE LA RESERVA EN AMBIENTE DE PRUEBA
  // ======================================

  /** Abre el modal "Confirma y paga" con la configuración indicada. */
  async function abrirPago(config: object = configuracionPago): Promise<void> {
    await abrirConCotizacion();
    boton('[data-confirmar]').click();
    await estable();
    http.expectOne(r => r.method === 'GET' && r.url === urlConfiguracionPago).flush(config);
    await estable();
  }

  const pagoAprobado = {
    idPago: 9,
    valor: 740000,
    estado: 'APROBADO',
    idReserva: 15,
    referencia: 'sim_pi_123',
    proveedor: 'SIMULADO',
    metodo: 'pm_card_visa'
  };

  it('CA-160 se paga escribiendo una tarjeta de prueba: solo viaja el método de prueba, nunca los datos de la tarjeta', async () => {

    await abrirPago();

    expect(texto('[data-pago]')).toContain('Confirma y paga');
    expect(texto('[data-desglose-pago]')).toContain('Desayuno');
    expect(texto('[data-ambiente-prueba]')).toContain('no se realizan cobros reales');
    expect(boton('[data-pagar]').disabled).toBe(true);
    expect(texto('[data-completar]')).toContain('Completa los datos de la tarjeta');

    escribir('[data-titular]', 'Ana Pérez');
    await escribirTarjeta('4000000000009995');

    // Se formatea y se reconoce la marca mientras se escribe.
    expect(elemento().querySelector<HTMLInputElement>('[data-numero]')!.value).toBe('4000 0000 0000 9995');
    expect(texto('[data-marca]')).toBe('Visa');
    expect(boton('[data-pagar]').disabled).toBe(false);
    expect(texto('[data-pagar]')).toContain('740');

    boton('[data-pagar]').click();
    await estable();

    const req = pedidoReserva();
    expect(req.request.body).toEqual({ idCotizacion: 42, metodoPago: 'pm_card_chargeDeclinedInsufficientFunds' });
    const cuerpo = JSON.stringify(req.request.body);
    expect(cuerpo).not.toContain('4000');
    expect(cuerpo).not.toContain('12/40');
    expect(cuerpo).not.toContain('Ana');
    rechazar(req, 402, 'El pago fue rechazado: fondos insuficientes. La reserva no quedó pagada.');
    await estable();
  });

  it('CA-160 una tarjeta que no es de prueba se rechaza sin llamar al backend', async () => {

    await abrirPago();
    // Número válido (Luhn) que no es una tarjeta de prueba.
    await escribirTarjeta('4111 1111 1111 1111');

    expect(texto('[data-error-numero]')).toBe('Usa una tarjeta de prueba: en este ambiente no se aceptan tarjetas reales.');
    expect(boton('[data-pagar]').disabled).toBe(true);
    boton('[data-pagar]').click();
    await estable();
    http.expectNone(r => r.method === 'POST' && r.url === urlReservas);
  });

  it('CA-160 valida el número, el vencimiento y el CVC antes de habilitar el pago', async () => {

    await abrirPago();

    await escribirTarjeta('4242 4242 4242 4241');
    expect(texto('[data-error-numero]')).toBe('El número de tarjeta no es válido.');
    expect(boton('[data-pagar]').disabled).toBe(true);

    await escribirTarjeta('4242 4242 4242 4242', '01/20');
    expect(texto('[data-error-vencimiento]')).toContain('vencida');
    expect(boton('[data-pagar]').disabled).toBe(true);

    await escribirTarjeta('4242 4242 4242 4242', '12/40', '12');
    expect(boton('[data-pagar]').disabled).toBe(true);

    await escribirTarjeta('4242 4242 4242 4242', '12/40', '123');
    expect(existe('[data-error-numero]')).toBe(false);
    expect(existe('[data-error-vencimiento]')).toBe(false);
    expect(boton('[data-pagar]').disabled).toBe(false);
  });

  it('CA-160 muestra solo las tarjetas de prueba que ofrece el backend y permite usarlas', async () => {

    await abrirPago();

    const ayuda = Array.from(elemento().querySelectorAll<HTMLButtonElement>('[data-usar-tarjeta]'))
      .map(b => (b.textContent ?? '').trim());
    expect(ayuda).toEqual([
      '4242 4242 4242 4242',
      '5555 5555 5555 4444',
      '4000 0000 0000 0002',
      '4000 0000 0000 9995',
      '9999 9999 9999 9995'
    ]);
    expect(texto('[data-ayuda-tarjetas]')).toContain('Fondos insuficientes');

    elemento().querySelectorAll<HTMLButtonElement>('[data-usar-tarjeta]')[1].click();
    await estable();
    expect(elemento().querySelector<HTMLInputElement>('[data-numero]')!.value).toBe('5555 5555 5555 4444');
    expect(texto('[data-marca]')).toBe('Mastercard');
  });

  it('CA-160 con Stripe en modo de prueba usa las mismas tarjetas de prueba y lo indica', async () => {

    await abrirPago({ ...configuracionPago, modo: 'STRIPE_TEST', clavePublica: 'pk_test_123' });

    expect(texto('[data-ambiente-prueba]')).toContain('Modo de prueba de Stripe: no se realizan cobros reales');
    await escribirTarjeta('4242 4242 4242 4242');
    boton('[data-pagar]').click();
    await estable();
    const req = pedidoReserva();
    expect(req.request.body.metodoPago).toBe('pm_card_visa');
    rechazar(req, 402, 'El pago fue rechazado: prueba.');
    await estable();
  });

  it('CA-161 con el pago aprobado muestra la reserva pagada con el pago registrado y su referencia', async () => {

    await abrirPago();
    await escribirTarjeta('4242 4242 4242 4242');
    boton('[data-pagar]').click();
    await estable();

    expect(texto('[data-pagar]')).toBe('Procesando pago...');
    const req = pedidoReserva();
    expect(req.request.body.metodoPago).toBe('pm_card_visa');
    req.flush({ ...reserva, pago: pagoAprobado }, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(existe('[data-pago]')).toBe(false);
    expect(elemento().querySelector('[data-reserva]')?.getAttribute('data-estado')).toBe('CONFIRMADA');
    expect(elemento().querySelector('[data-pago-registrado]')?.getAttribute('data-resultado-pago')).toBe('APROBADO');
    expect(texto('[data-estado-pago]')).toContain('Aprobado');
    expect(digitos(texto('[data-estado-pago]'))).toBe('740000');
    expect(texto('[data-referencia-pago]')).toBe('sim_pi_123');
    expect(texto('[data-reserva] [data-ambiente-prueba]')).toContain('Pago simulado: ambiente de prueba, no se hizo ningún cobro real');
  });

  it('CA-162 con la tarjeta rechazada la reserva no queda pagada, se informa el motivo y se puede pagar con otra tarjeta', async () => {

    await abrirPago();
    await escribirTarjeta('4000 0000 0000 0002');
    boton('[data-pagar]').click();
    await estable();

    const primero = pedidoReserva();
    expect(primero.request.body.metodoPago).toBe('pm_card_chargeDeclined');
    const claveRechazada = primero.request.headers.get('Idempotency-Key');
    primero.flush(
      { message: 'El pago fue rechazado: tarjeta rechazada. La reserva no quedó pagada.', dato: { ...reserva, estado: 'CANCELADA' } },
      { status: 402, statusText: 'Payment Required' }
    );
    await estable();

    // El modal sigue abierto con el motivo; no se muestra ninguna reserva.
    expect(existe('[data-pago]')).toBe(true);
    expect(texto('[data-error-pago]')).toBe('El pago fue rechazado: tarjeta rechazada. La reserva no quedó pagada.');
    expect(existe('[data-reserva]')).toBe(false);
    expect(texto('[data-mensaje-error]')).toBe('El pago no se confirmó y la reserva no quedó pagada.');

    // Con otra tarjeta es otra petición, con otra clave.
    await escribirTarjeta('4242 4242 4242 4242');
    boton('[data-pagar]').click();
    await estable();

    const segundo = pedidoReserva();
    expect(segundo.request.body.metodoPago).toBe('pm_card_visa');
    expect(segundo.request.headers.get('Idempotency-Key')).not.toBe(claveRechazada);
    segundo.flush({ ...reserva, pago: pagoAprobado }, { status: 201, statusText: 'Created' });
    await estable();
    pedidoDisponibilidad().flush({ idAlojamiento: 7, fechaEntrada: llegada, fechaSalida: salida, disponible: false });
    await estable();

    expect(existe('[data-error-pago]')).toBe(false);
    expect(texto('[data-estado-pago]')).toContain('Aprobado');
  });

  it('CA-162 con la tarjeta reservada para la falla de la pasarela informa que el pago no se confirmó', async () => {

    await abrirPago();
    await escribirTarjeta('9999 9999 9999 9995');
    boton('[data-pagar]').click();
    await estable();

    const fallido = pedidoReserva();
    expect(fallido.request.body.metodoPago).toBe('pm_card_error');
    rechazar(fallido, 502, 'No se pudo confirmar el pago: falla de la pasarela. La reserva no quedó pagada.');
    await estable();

    expect(texto('[data-error-pago]')).toContain('No se pudo confirmar el pago');
    expect(texto('[data-error-pago]')).toContain('La reserva no quedó pagada.');
    expect(existe('[data-reserva]')).toBe(false);
    expect(existe('[data-pago-registrado]')).toBe(false);
  });

  it('si no carga la configuración de pagos no deja pagar y permite reintentar', async () => {

    await abrirConCotizacion();
    boton('[data-confirmar]').click();
    await estable();
    http.expectOne(r => r.url === urlConfiguracionPago).flush(null, { status: 500, statusText: 'Error' });
    await estable();

    expect(texto('[data-pago]')).toContain('No se pudo cargar la configuración de pagos');
    expect(boton('[data-pagar]').disabled).toBe(true);

    boton('[data-recargar-config]').click();
    http.expectOne(r => r.url === urlConfiguracionPago).flush(configuracionPago);
    await estable();
    await escribirTarjeta('4242 4242 4242 4242');
    expect(boton('[data-pagar]').disabled).toBe(false);
  });

  it('"Cancelar" cierra el modal sin crear la reserva y descarta los datos de la tarjeta', async () => {

    await abrirPago();
    await escribirTarjeta('4242 4242 4242 4242');
    boton('[data-cancelar-pago]').click();
    await estable();

    expect(existe('[data-pago]')).toBe(false);
    http.expectNone(r => r.method === 'POST' && r.url === urlReservas);

    // Al volver a abrirlo, los campos están vacíos.
    boton('[data-confirmar]').click();
    await estable();
    http.expectOne(r => r.url === urlConfiguracionPago).flush(configuracionPago);
    await estable();
    expect(elemento().querySelector<HTMLInputElement>('[data-numero]')!.value).toBe('');
  });
});
