import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { DetalleReserva } from './detalle-reserva';
import { API_URL } from '../../core/services/api-url';
import { reservaDePrueba } from '../testing/reservas-de-prueba';

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
    // Acciones de otras historias (HU-01, HU-03, HU-28) aún no se muestran.
    expect(texto(fixture)).not.toContain('Cancelar reserva');
    expect(texto(fixture)).not.toContain('Contactar al anfitrión');
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
