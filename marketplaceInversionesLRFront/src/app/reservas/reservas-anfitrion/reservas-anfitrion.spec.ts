import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { ReservasAnfitrion } from './reservas-anfitrion';
import { API_URL } from '../../core/services/api-url';
import { PaginaReservas, Reserva } from '../models/reserva';
import { reservaDePrueba } from '../testing/reservas-de-prueba';

/**
 * HU-25 Consultar estado e historial de reservas (SCRUM-276): reservas de los alojamientos del anfitrión.
 */
describe('ReservasAnfitrion (HU-25)', () => {
  const url = 'http://api/reservas/anfitrion';
  let http: HttpTestingController;

  const pagina = (contenido: Reserva[], numero = 0, totalElementos = contenido.length, totalPaginas = 1): PaginaReservas => ({
    contenido, pagina: numero, tamanio: 10, totalElementos, totalPaginas
  });

  function crear() {
    TestBed.configureTestingModule({
      imports: [ReservasAnfitrion],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }]
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ReservasAnfitrion);
    fixture.detectChanges();
    return fixture;
  }

  const filas = (f: { nativeElement: HTMLElement }) =>
    Array.from(f.nativeElement.querySelectorAll('[data-reserva-fila]') as NodeListOf<HTMLElement>);

  afterEach(() => http.verify());

  it('CA-079 consulta GET /reservas/anfitrion y muestra las reservas de sus alojamientos con su estado', () => {
    const fixture = crear();
    expect(fixture.nativeElement.textContent).toContain('Cargando reservas...');

    const req = http.expectOne(r => r.url === url);
    expect(req.request.params.get('pagina')).toBe('0');
    expect(req.request.params.get('tamanio')).toBe('10');
    req.flush(pagina([
      reservaDePrueba(15, { nombreHuesped: 'Ana Pérez', tituloAlojamiento: 'Casa en el lago' }),
      reservaDePrueba(16, { estado: 'CANCELADA', nombreHuesped: 'Juan Gómez' }),
      reservaDePrueba(17, { completada: true })
    ]));
    fixture.detectChanges();

    const lista = filas(fixture);
    expect(lista.length).toBe(3);
    expect(lista[0].textContent).toContain('#15');
    expect(lista[0].textContent).toContain('Ana Pérez');
    expect(lista[0].textContent).toContain('Casa en el lago');
    expect(lista[0].textContent).toContain('Confirmada');
    expect(lista[1].textContent).toContain('Cancelada');
    expect(lista[2].textContent).toContain('Finalizada');
    expect(fixture.nativeElement.textContent).toContain('Mostrando 1-3 de 3 reservas');
  });

  it('CA-079 pagina las reservas con el backend', () => {
    const fixture = crear();
    http.expectOne(r => r.url === url).flush(pagina([reservaDePrueba(1)], 0, 12, 2));
    fixture.detectChanges();

    (Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(b => b.textContent?.trim() === 'Siguiente')!).click();
    const req = http.expectOne(r => r.url === url);
    expect(req.request.params.get('pagina')).toBe('1');
    req.flush(pagina([reservaDePrueba(11), reservaDePrueba(12)], 1, 12, 2));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Mostrando 11-12 de 12 reservas');
  });

  it('CA-079 sin reservas en sus alojamientos lo indica', () => {
    const fixture = crear();
    http.expectOne(r => r.url === url).flush(pagina([]));
    fixture.detectChanges();

    expect(filas(fixture).length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Todavía no hay reservas registradas');
  });

  it('CA-079 si el usuario no es anfitrión (403) lo informa sin mostrar reservas', () => {
    const fixture = crear();
    http.expectOne(r => r.url === url).flush({ message: 'Acceso denegado' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso');
    expect(filas(fixture).length).toBe(0);
  });

  it('el código de la reserva abre su detalle', () => {
    const fixture = crear();
    http.expectOne(r => r.url === url).flush(pagina([reservaDePrueba(15)]));
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (filas(fixture)[0].querySelector('button') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/host/reservas', 15]);
  });
});
