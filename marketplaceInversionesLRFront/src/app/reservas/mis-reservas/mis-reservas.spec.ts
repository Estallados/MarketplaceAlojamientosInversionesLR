import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { MisReservas } from './mis-reservas';
import { API_URL } from '../../core/services/api-url';
import { PaginaReservas, Reserva } from '../models/reserva';
import { enDias, reservaDePrueba } from '../testing/reservas-de-prueba';
import { routes } from '../../app.routes';

/**
 * HU-25 Consultar estado e historial de reservas (SCRUM-276): Mis reservas del huésped.
 */
describe('MisReservas (HU-25)', () => {
  const url = 'http://api/reservas/mias';
  let http: HttpTestingController;

  const pagina = (contenido: Reserva[], numero = 0, totalPaginas = 1): PaginaReservas => ({
    contenido, pagina: numero, tamanio: 100, totalElementos: contenido.length, totalPaginas
  });

  const todas: Reserva[] = [
    reservaDePrueba(1, { tituloAlojamiento: 'Casa futura' }),
    reservaDePrueba(2, { fechaEntrada: enDias(-1), fechaSalida: enDias(2), tituloAlojamiento: 'Casa en curso' }),
    reservaDePrueba(3, { fechaEntrada: enDias(-10), fechaSalida: enDias(-7), completada: true }),
    reservaDePrueba(4, {
      estado: 'CANCELADA',
      cancelacion: { fecha: '2026-11-05T09:00:00', valorReembolso: 370000, motivo: 'Cambio de planes' }
    }),
    reservaDePrueba(5, { estado: 'PENDIENTE_PAGO' })
  ];

  function crear() {
    TestBed.configureTestingModule({
      imports: [MisReservas],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }]
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(MisReservas);
    fixture.detectChanges();
    return fixture;
  }

  const pedido = (numero = 0) => http.expectOne(r => r.url === url && r.params.get('pagina') === String(numero));
  const items = (f: { nativeElement: HTMLElement }) =>
    Array.from(f.nativeElement.querySelectorAll('[data-reserva-item]') as NodeListOf<HTMLElement>);
  const pestanas = (f: { nativeElement: HTMLElement }) =>
    Array.from(f.nativeElement.querySelectorAll('[data-pestana]') as NodeListOf<HTMLButtonElement>);

  afterEach(() => http.verify());

  it('CA-078 consulta todas las reservas del huésped y las agrupa por su estado actual', () => {
    const fixture = crear();
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).toBeTruthy();

    pedido(0).flush(pagina(todas.slice(0, 3), 0, 2));
    pedido(1).flush(pagina(todas.slice(3), 1, 2));
    fixture.detectChanges();

    expect(pestanas(fixture).map(b => b.textContent?.trim())).toEqual([
      'Próximas 2', 'Activas 1', 'Finalizadas 1', 'Canceladas 1'
    ]);
    // Próximas: la confirmada futura y la pendiente de pago, con su estado.
    expect(items(fixture).map(i => i.querySelector('[data-etiqueta]')?.textContent?.trim())).toEqual([
      'Confirmada', 'Pendiente de pago'
    ]);
  });

  it('CA-078 muestra en cada pestaña las reservas con su estado, fechas, huéspedes y total', () => {
    const fixture = crear();
    pedido().flush(pagina(todas));
    fixture.detectChanges();

    pestanas(fixture)[1].click();
    fixture.detectChanges();
    expect(items(fixture).length).toBe(1);
    expect(items(fixture)[0].textContent).toContain('Casa en curso');
    expect(items(fixture)[0].querySelector('[data-etiqueta]')?.textContent?.trim()).toBe('Activa');

    pestanas(fixture)[2].click();
    fixture.detectChanges();
    expect(items(fixture)[0].querySelector('[data-etiqueta]')?.textContent?.trim()).toBe('Finalizada');

    pestanas(fixture)[3].click();
    fixture.detectChanges();
    const cancelada = items(fixture)[0];
    expect(cancelada.querySelector('[data-etiqueta]')?.textContent?.trim()).toBe('Cancelada');
    expect(cancelada.textContent).toContain('Cancelada el 05/11/2026');
    expect(cancelada.textContent?.replace(/\D/g, '')).toContain('370000');

    pestanas(fixture)[0].click();
    fixture.detectChanges();
    const futura = items(fixture)[0].textContent ?? '';
    const [a, m, d] = enDias(10).split('-');
    expect(futura).toContain(`${d}/${m}/${a}`);
    expect(futura).toContain('#1');
    expect(futura.replace(/\D/g, '')).toContain('740000');
  });

  it('CA-078 indica cuando no hay reservas en una categoría', () => {
    const fixture = crear();
    pedido().flush(pagina([]));
    fixture.detectChanges();

    expect(items(fixture).length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('No hay reservas en esta categoría');
  });

  it('CA-078 si la consulta falla lo informa y permite reintentar', () => {
    const fixture = crear();
    pedido().flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar tus reservas');

    (Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find(b => b.textContent?.includes('Reintentar'))!).click();
    pedido().flush(pagina(todas.slice(0, 1)));
    fixture.detectChanges();
    expect(items(fixture).length).toBe(1);
  });

  it('"Ver detalles" abre el detalle de la reserva; las acciones de otras historias no se muestran', () => {
    const fixture = crear();
    pedido().flush(pagina(todas));
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (items(fixture)[0].querySelector('[data-ver-detalle]') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/reservas', 1]);
    expect(fixture.nativeElement.textContent).not.toContain('Calificar estancia');
  });

  it('CA-001 "Cancelar reserva" aparece solo en las cancelables y abre el detalle con ?accion=cancelar', () => {
    const fixture = crear();
    pedido().flush(pagina([
      ...todas,
      reservaDePrueba(6, { estado: 'EN_REVISION', tituloAlojamiento: 'Casa en revisión' })
    ]));
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const conCancelar = () => items(fixture)
      .filter(i => i.querySelector('[data-cancelar]'))
      .map(i => Number(i.getAttribute('data-id')));

    // Próximas: 1 (CONFIRMADA futura), 5 (PENDIENTE_PAGO) y 6 (EN_REVISION).
    expect(conCancelar()).toEqual([1, 6]);

    (items(fixture)[0].querySelector('[data-cancelar]') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/reservas', 1], { queryParams: { accion: 'cancelar' } });

    // Activas (ya empezó), Finalizadas y Canceladas: sin botón.
    for (const tab of [1, 2, 3]) {
      (fixture.nativeElement.querySelectorAll('[data-pestana]')[tab] as HTMLButtonElement).click();
      fixture.detectChanges();
      expect(conCancelar()).toEqual([]);
    }
  });

  it('las rutas de reservas exigen el rol correspondiente', () => {
    const roles = (path: string) => routes.find(r => r.path === path)?.data?.['roles'];
    expect(roles('reservas')).toEqual(['Huésped']);
    expect(roles('reservas/:id')).toEqual(['Huésped']);
    expect(roles('host/reservas')).toEqual(['Anfitrión']);
    expect(roles('host/reservas/:id')).toEqual(['Anfitrión']);
  });
});
