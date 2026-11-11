import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { DetalleReservaAnfitrion } from './detalle-reserva-anfitrion';
import { API_URL } from '../../core/services/api-url';
import { reservaDePrueba } from '../testing/reservas-de-prueba';

/**
 * HU-25 Consultar estado e historial de reservas (SCRUM-276): detalle de una reserva para el anfitrión.
 */
describe('DetalleReservaAnfitrion (HU-25)', () => {
  let http: HttpTestingController;

  function crear() {
    TestBed.configureTestingModule({
      imports: [DetalleReservaAnfitrion],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '15' }) } } }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DetalleReservaAnfitrion);
    fixture.detectChanges();
    return fixture;
  }

  afterEach(() => http.verify());

  it('CA-079 muestra la reserva de su alojamiento con el huésped, las fechas, el estado y el total', () => {
    const fixture = crear();
    http.expectOne('http://api/reservas/15').flush(reservaDePrueba(15, {
      nombreHuesped: 'Ana Pérez', tituloAlojamiento: 'Casa en el lago', fechaEntrada: '2026-12-01', fechaSalida: '2026-12-04'
    }));
    fixture.detectChanges();

    const resumen = (fixture.nativeElement.querySelector('[data-resumen]')?.textContent ?? '').replace(/\s+/g, ' ');
    expect(resumen).toContain('#15');
    expect(resumen).toContain('Ana Pérez');
    expect(resumen).toContain('Casa en el lago');
    expect(resumen).toContain('01/12/2026');
    expect(resumen).toContain('04/12/2026');
    expect(fixture.nativeElement.querySelector('[data-etiqueta]')?.textContent?.trim()).toBe('Confirmada');
    expect((fixture.nativeElement.querySelector('[data-total]')?.textContent ?? '').replace(/\D/g, '')).toBe('740000');
  });

  it('CA-079 una reserva que no es de sus alojamientos (404) no se muestra', () => {
    const fixture = crear();
    http.expectOne('http://api/reservas/15').flush({ message: 'La reserva no existe.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('no es de uno de tus alojamientos');
    expect(fixture.nativeElement.querySelector('[data-resumen]')).toBeNull();
  });
});
