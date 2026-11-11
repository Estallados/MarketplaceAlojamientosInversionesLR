import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { DetalleReserva } from './detalle-reserva';
import { API_URL } from '../../core/services/api-url';
import { puedeCancelarReserva } from '../models/estado-reserva';
import { Reserva, SimulacionCancelacion } from '../models/reserva';
import { enDias, reservaDePrueba } from '../testing/reservas-de-prueba';

/**
 * HU-25 Consultar estado e historial de reservas (SCRUM-276): detalle de la reserva del huésped.
 */
describe('DetalleReserva (HU-25)', () => {
  let http: HttpTestingController;

  const desglose = JSON.stringify({
    idCotizacion: 42, noches: 3, precioNoche: 200000, precioBase: 600000, descuento: 60000,
    porcentajeLimpieza: 5, valorLimpieza: 30000, porcentajeServicio: 10, valorServicio: 60000,
    valorAdicionales: 50000, total: 680000,
    serviciosAdicionales: [{ idServicio: 3, nombre: 'Desayuno', costo: 50000 }]
  });

  function crear(id = '15') {
    TestBed.configureTestingModule({
      imports: [DetalleReserva],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DetalleReserva);
    fixture.detectChanges();
    return fixture;
  }

  const texto = (f: { nativeElement: HTMLElement }, sel = 'main') =>
    (f.nativeElement.querySelector(sel)?.textContent ?? '').replace(/\s+/g, ' ');

  afterEach(() => http.verify());

  it('CA-078 muestra el estado actual, los datos de la reserva y el desglose cotizado', () => {
    const fixture = crear();
    const req = http.expectOne('http://api/reservas/15');
    expect(req.request.method).toBe('GET');
    req.flush(reservaDePrueba(15, {
      total: 680000, desglose, nombrePolitica: 'Flexible', nombreAnfitrion: 'Luis',
      tipoAlojamiento: 'Casa', capacidadAlojamiento: 4, ubicacion: 'Calle 1 # 2-3'
    }));
    fixture.detectChanges();

    expect(texto(fixture, '[data-etiqueta]').trim()).toBe('Confirmada');
    const info = texto(fixture, '[data-informacion]');
    expect(info).toContain('#15');
    expect(info).toContain('Confirmada');
    const desg = texto(fixture, '[data-desglose]');
    expect(desg).toContain('× 3 noches');
    expect(desg).toContain('Descuento');
    expect(desg).toContain('Limpieza (5 %)');
    expect(desg).toContain('Tarifa de servicio (10 %)');
    expect(desg).toContain('Desayuno');
    expect(texto(fixture, '[data-total]').replace(/\D/g, '')).toBe('680000');
    expect(texto(fixture)).toContain('Flexible');
    expect(texto(fixture)).toContain('Calle 1 # 2-3');
    // Acciones de otras historias (HU-03, HU-28) aún no se muestran; cancelar (HU-01) se prueba abajo.
    expect(texto(fixture)).not.toContain('Contactar al anfitrión');
    expect(texto(fixture)).not.toContain('Calificar estancia');
  });

  it('CA-078 de una reserva cancelada muestra la fecha, la devolución y el motivo', () => {
    const fixture = crear();
    http.expectOne('http://api/reservas/15').flush(reservaDePrueba(15, {
      estado: 'CANCELADA',
      cancelacion: { fecha: '2026-11-05T09:00:00', valorReembolso: 370000, motivo: 'Cambio de planes' }
    }));
    fixture.detectChanges();

    expect(texto(fixture, '[data-etiqueta]').trim()).toBe('Cancelada');
    const cancelada = texto(fixture, '[data-cancelada]');
    expect(cancelada).toContain('Reserva cancelada');
    expect(cancelada.replace(/\D/g, '')).toContain('370000');
    expect(cancelada).toContain('Motivo: Cambio de planes');
  });

  it('CA-078 si la reserva no existe o no es del huésped lo indica y permite volver', () => {
    const fixture = crear();
    http.expectOne('http://api/reservas/15').flush({ message: 'La reserva no existe.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-error-carga]')?.textContent).toContain('no existe o no tienes acceso');
    expect(fixture.nativeElement.textContent).toContain('Volver a mis reservas');
  });

  it('no consulta con un identificador inválido', () => {
    const fixture = crear('abc');
    http.expectNone(() => true);
    expect(fixture.nativeElement.querySelector('[data-error-carga]')?.textContent).toContain('no es válido');
  });
});

/**
 * HU-01 Cancelar reserva aplicando la política de cancelación (SCRUM-252).
 */
describe('DetalleReserva — cancelar reserva (HU-01)', () => {
  let http: HttpTestingController;

  const simulacionDePrueba = (cambios: Partial<SimulacionCancelacion> = {}): SimulacionCancelacion => ({
    idReserva: 15, politica: 'MODERADA', diasAnticipacion: 10, porcentajeReembolso: 100,
    valorReembolso: 740000, totalPagado: 740000, ...cambios
  });

  function crear(reserva: Reserva = reservaDePrueba(15), query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [DetalleReserva],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '15' }), queryParamMap: convertToParamMap(query) } }
        }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(DetalleReserva);
    fixture.detectChanges();
    http.expectOne('http://api/reservas/15').flush(reserva);
    fixture.detectChanges();
    return { fixture, navegar };
  }

  const el = (f: ComponentFixture<DetalleReserva>, sel: string) =>
    f.nativeElement.querySelector(sel) as HTMLElement | null;
  const texto = (f: ComponentFixture<DetalleReserva>, sel: string) =>
    (el(f, sel)?.textContent ?? '').replace(/\s+/g, ' ');
  const confirmar = (f: ComponentFixture<DetalleReserva>) =>
    el(f, '[data-confirmar-cancelacion]') as HTMLButtonElement;

  function abrirModal(f: ComponentFixture<DetalleReserva>, simulacion = simulacionDePrueba()) {
    (el(f, '[data-abrir-cancelacion]') as HTMLButtonElement).click();
    f.detectChanges();
    const req = http.expectOne('http://api/reservas/15/simularcancelacion');
    expect(req.request.method).toBe('GET');
    req.flush(simulacion);
    f.detectChanges();
  }

  function escribirMotivo(f: ComponentFixture<DetalleReserva>, motivo: string) {
    const area = el(f, '[data-motivo]') as HTMLTextAreaElement;
    area.value = motivo;
    area.dispatchEvent(new Event('input'));
    f.detectChanges();
  }

  const pedidoCancelar = () => http.expectOne('http://api/reservas/15/cancelar');

  afterEach(() => http.verify());

  it('CA-001 antes de confirmar muestra la política y la devolución que calcula el backend', () => {
    const { fixture } = crear();
    (el(fixture, '[data-abrir-cancelacion]') as HTMLButtonElement).click();
    fixture.detectChanges();

    // Mientras el backend calcula no se puede confirmar.
    expect(el(fixture, '[data-simulacion-cargando]')).toBeTruthy();
    expect(confirmar(fixture).disabled).toBe(true);

    http.expectOne('http://api/reservas/15/simularcancelacion')
      .flush(simulacionDePrueba({ porcentajeReembolso: 50, valorReembolso: 370000, diasAnticipacion: 3 }));
    fixture.detectChanges();

    const simulacion = texto(fixture, '[data-simulacion]');
    expect(texto(fixture, '[data-politica]').trim()).toBe('MODERADA');
    expect(simulacion).toContain('Días de anticipación3');
    expect(simulacion).toContain('Devolución (50 %)');
    expect(texto(fixture, '[data-devolucion]').replace(/\D/g, '')).toBe('370000');
    // Sin motivo todavía no se puede confirmar.
    expect(confirmar(fixture).disabled).toBe(true);
    escribirMotivo(fixture, 'Cambio de planes');
    expect(confirmar(fixture).disabled).toBe(false);
  });

  it('CA-001 si la reserva no se puede cancelar muestra el motivo del backend y no deja confirmar', () => {
    const { fixture } = crear();
    (el(fixture, '[data-abrir-cancelacion]') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne('http://api/reservas/15/simularcancelacion')
      .flush({ message: 'Solo se pueden cancelar reservas futuras.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(texto(fixture, '[data-error-simulacion]')).toContain('Solo se pueden cancelar reservas futuras.');
    escribirMotivo(fixture, 'Cambio de planes');
    expect(confirmar(fixture).disabled).toBe(true);
  });

  it('CA-004 envía el motivo con Idempotency-Key y muestra el estado final registrado', () => {
    const { fixture } = crear();
    abrirModal(fixture);
    escribirMotivo(fixture, '  Cambio de planes  ');
    confirmar(fixture).click();
    fixture.detectChanges();

    const req = pedidoCancelar();
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ motivo: 'Cambio de planes' });
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(confirmar(fixture).textContent).toContain('Cancelando...');

    req.flush({
      idCancelacion: 9, motivo: 'Cambio de planes', politica: 'MODERADA', porcentajeReembolso: 100,
      valorReembolso: 740000, estado: 'COMPLETADA', estadoFinalReserva: 'CANCELADA',
      fecha: '2026-11-11T10:00:00', idUsuario: 12, idReserva: 15
    });
    fixture.detectChanges();

    // Recarga sin indicador de carga: el estado final lo da el backend.
    expect(el(fixture, '[aria-busy]')).toBeNull();
    http.expectOne('http://api/reservas/15').flush(reservaDePrueba(15, {
      estado: 'CANCELADA',
      cancelacion: { fecha: '2026-11-11T10:00:00', valorReembolso: 740000, motivo: 'Cambio de planes' }
    }));
    fixture.detectChanges();

    expect(el(fixture, '[data-modal-cancelacion]')).toBeNull();
    expect(texto(fixture, '[data-aviso]')).toContain('Tu reserva fue cancelada.');
    expect(texto(fixture, '[data-etiqueta]').trim()).toBe('Cancelada');
    const cancelada = texto(fixture, '[data-cancelada]');
    expect(cancelada).toContain('Reserva cancelada');
    expect(cancelada.replace(/\D/g, '')).toContain('740000');
    expect(cancelada).toContain('Motivo: Cambio de planes');
  });

  it('CA-007 una vez cancelada la reserva deja de ofrecerse para cancelar', () => {
    const { fixture } = crear();
    abrirModal(fixture);
    escribirMotivo(fixture, 'Viaje aplazado');
    confirmar(fixture).click();
    pedidoCancelar().flush({ estadoFinalReserva: 'CANCELADA' });
    http.expectOne('http://api/reservas/15').flush(reservaDePrueba(15, { estado: 'CANCELADA' }));
    fixture.detectChanges();

    expect(el(fixture, '[data-abrir-cancelacion]')).toBeNull();
    expect(texto(fixture, '[data-etiqueta]').trim()).toBe('Cancelada');
  });

  it('CA-009 si la política no da devolución lo indica y permite cancelar igual', () => {
    const { fixture } = crear();
    abrirModal(fixture, simulacionDePrueba({ politica: 'ESTRICTA', porcentajeReembolso: 0, valorReembolso: 0, diasAnticipacion: 2 }));

    expect(texto(fixture, '[data-politica]').trim()).toBe('ESTRICTA');
    expect(texto(fixture, '[data-devolucion]').replace(/\D/g, '')).toBe('0');
    expect(texto(fixture, '[data-sin-devolucion]')).toContain('no tiene devolución');

    escribirMotivo(fixture, 'Ya no viajo');
    confirmar(fixture).click();
    expect(pedidoCancelar().request.body).toEqual({ motivo: 'Ya no viajo' });
  });

  it('muestra el error del backend al cancelar y no cierra el modal', () => {
    const { fixture } = crear();
    abrirModal(fixture);
    escribirMotivo(fixture, 'Cambio de planes');
    confirmar(fixture).click();
    pedidoCancelar().flush(
      { message: 'La reserva no se puede cancelar en estado CANCELADA.' },
      { status: 409, statusText: 'Conflict' }
    );
    fixture.detectChanges();

    expect(el(fixture, '[data-modal-cancelacion]')).toBeTruthy();
    expect(texto(fixture, '[data-error-cancelacion]')).toContain('no se puede cancelar en estado CANCELADA');
    expect(confirmar(fixture).disabled).toBe(false);
  });

  it('tras un error de red reintenta con la misma Idempotency-Key; con otro motivo usa una nueva', () => {
    const { fixture } = crear();
    abrirModal(fixture);
    escribirMotivo(fixture, 'Cambio de planes');

    confirmar(fixture).click();
    const primero = pedidoCancelar();
    const clave = primero.request.headers.get('Idempotency-Key');
    primero.error(new ProgressEvent('error'), { status: 0 });
    fixture.detectChanges();
    expect(texto(fixture, '[data-error-cancelacion]')).toContain('No hay conexión con el servidor.');

    confirmar(fixture).click();
    const reintento = pedidoCancelar();
    expect(reintento.request.headers.get('Idempotency-Key')).toBe(clave);
    reintento.flush({ message: 'Esta Idempotency-Key ya se usó con una petición diferente.' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();
    expect(texto(fixture, '[data-error-cancelacion]')).toContain('ya se usó');

    escribirMotivo(fixture, 'Otro motivo');
    confirmar(fixture).click();
    expect(pedidoCancelar().request.headers.get('Idempotency-Key')).not.toBe(clave);
  });

  it('evita el doble clic: una sola petición de cancelación', () => {
    const { fixture } = crear();
    abrirModal(fixture);
    escribirMotivo(fixture, 'Cambio de planes');
    const boton = confirmar(fixture);
    boton.click();
    boton.click();
    fixture.componentInstance.confirmarCancelacion();

    expect(http.match('http://api/reservas/15/cancelar').length).toBe(1);
  });

  it('valida el motivo: obligatorio, sin solo espacios y de máximo 500 caracteres', () => {
    const { fixture } = crear();
    abrirModal(fixture);

    expect((el(fixture, '[data-motivo]') as HTMLTextAreaElement).getAttribute('maxlength')).toBe('500');
    escribirMotivo(fixture, '   ');
    expect(confirmar(fixture).disabled).toBe(true);
    escribirMotivo(fixture, 'x'.repeat(501));
    expect(confirmar(fixture).disabled).toBe(true);
    escribirMotivo(fixture, 'x'.repeat(500));
    expect(confirmar(fixture).disabled).toBe(false);
  });

  it('"Volver" cierra el modal sin cancelar nada', () => {
    const { fixture } = crear();
    abrirModal(fixture);
    const volver = Array.from(fixture.nativeElement.querySelectorAll('[data-modal-cancelacion] button') as NodeListOf<HTMLButtonElement>)
      .find(b => b.textContent?.trim() === 'Volver') as HTMLButtonElement;
    volver.click();
    fixture.detectChanges();

    expect(el(fixture, '[data-modal-cancelacion]')).toBeNull();
    http.expectNone('http://api/reservas/15/cancelar');
  });

  it('solo ofrece cancelar reservas CONFIRMADA o EN_REVISION que aún no empiezan', () => {
    expect(puedeCancelarReserva(reservaDePrueba(1))).toBe(true);
    expect(puedeCancelarReserva(reservaDePrueba(2, { estado: 'EN_REVISION' }))).toBe(true);
    expect(puedeCancelarReserva(reservaDePrueba(3, { estado: 'PENDIENTE_PAGO' }))).toBe(false);
    expect(puedeCancelarReserva(reservaDePrueba(4, { estado: 'CANCELADA' }))).toBe(false);
    expect(puedeCancelarReserva(reservaDePrueba(5, { fechaEntrada: enDias(0), fechaSalida: enDias(2) }))).toBe(false);
    expect(puedeCancelarReserva(reservaDePrueba(6, { fechaEntrada: enDias(-5), fechaSalida: enDias(-2), completada: true }))).toBe(false);

    const { fixture } = crear(reservaDePrueba(15, { fechaEntrada: enDias(-1), fechaSalida: enDias(2) }));
    expect(el(fixture, '[data-abrir-cancelacion]')).toBeNull();
  });

  it('con ?accion=cancelar abre el modal al cargar y limpia el parámetro', () => {
    const { fixture, navegar } = crear(reservaDePrueba(15), { accion: 'cancelar' });

    expect(el(fixture, '[data-modal-cancelacion]')).toBeTruthy();
    http.expectOne('http://api/reservas/15/simularcancelacion').flush(simulacionDePrueba());
    expect(navegar).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { accion: null }, replaceUrl: true }));
  });

  it('con ?accion=cancelar no abre el modal si la reserva ya no se puede cancelar', () => {
    const { fixture } = crear(reservaDePrueba(15, { estado: 'CANCELADA' }), { accion: 'cancelar' });

    expect(el(fixture, '[data-modal-cancelacion]')).toBeNull();
    http.expectNone('http://api/reservas/15/simularcancelacion');
  });
});
