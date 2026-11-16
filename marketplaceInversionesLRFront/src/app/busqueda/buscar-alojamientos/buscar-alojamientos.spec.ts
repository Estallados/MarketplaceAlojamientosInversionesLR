import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BuscarAlojamientos } from './buscar-alojamientos';
import { BusquedaService } from '../services/busqueda.service';
import { AlojamientoResultado, FiltrosExplorar, PaginaResultados } from '../models/busqueda';

describe('BuscarAlojamientos (HU-23)', () => {
  let servicio: BusquedaService;
  let filtros: ReturnType<typeof vi.fn>;
  let pagina: ReturnType<typeof vi.fn>;

  const lista: AlojamientoResultado[] = [
    { idAlojamiento: 1, titulo: 'Casa', ciudad: 'Bogotá', tipo: 'Casa', capacidad: 4, precioNoche: 200000, amenidades: ['Wifi', 'Piscina'] },
    { idAlojamiento: 2, titulo: 'Loft', ciudad: 'Bogotá', tipo: 'Apartamento', capacidad: 2, precioNoche: 100000, amenidades: ['Wifi'] },
  ];
  const opciones: FiltrosExplorar = {
    tipos: ['Apartamento', 'Cabaña', 'Casa'],
    amenidades: ['Chimenea', 'Piscina', 'Wifi'],
    precioMinimo: 100000,
    precioMaximo: 300000,
  };
  const respuesta: PaginaResultados = { contenido: lista, pagina: 0, totalElementos: 20, totalPaginas: 3 };

  function crear(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [BuscarAlojamientos],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    servicio = TestBed.inject(BusquedaService);
    filtros = vi.spyOn(servicio, 'filtros').mockReturnValue(of(opciones)) as unknown as ReturnType<typeof vi.fn>;
    pagina = vi.spyOn(servicio, 'pagina').mockReturnValue(of(respuesta)) as unknown as ReturnType<typeof vi.fn>;
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    // Como en la aplicación: la primera detección de cambios ejecuta ngOnInit una sola vez
    const fixture = TestBed.createComponent(BuscarAlojamientos);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  /** Argumentos de la última página pedida: [búsqueda, filtros, página, tamaño]. */
  const ultimaPagina = () => pagina.mock.calls.at(-1);

  afterEach(() => vi.useRealTimers());

  it('pide solo la página que muestra y arma los filtros con las opciones del servidor', () => {
    const c = crear();

    expect(filtros).toHaveBeenCalledWith({});
    expect(ultimaPagina()).toEqual([{}, { orden: undefined }, 0, 9]);
    expect(c.paginados().map((a) => a.idAlojamiento)).toEqual([1, 2]);
    expect(c.total()).toBe(20);
    expect(c.totalPaginas()).toBe(3);
    expect(c.types()).toEqual(['Todos', 'Apartamento', 'Cabaña', 'Casa']);
    expect(c.services()).toEqual(['Chimenea', 'Piscina', 'Wifi']);
    expect([c.precioMin(), c.precioMax()]).toEqual([100000, 300000]);
  });

  it('CA-087: busca por ciudad, fechas y huéspedes en el backend', () => {
    const c = crear();
    const manana = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
    const pasado = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
    c.ciudad.set(' Bogotá ');
    c.entrada.set(manana);
    c.salida.set(pasado);
    c.huespedes.set('2');
    c.buscar();

    const busqueda = { ciudad: 'Bogotá', llegada: manana, salida: pasado, huespedes: 2 };
    expect(filtros).toHaveBeenLastCalledWith(busqueda);
    expect(ultimaPagina()).toEqual([busqueda, { orden: undefined }, 0, 9]);
    expect(c.busquedaAplicada()).toBe(true);
  });

  it('CA-087: el tipo, los servicios y el orden se filtran en el servidor', () => {
    const c = crear();

    c.elegirTipo('Casa');
    expect(ultimaPagina()?.[1]).toEqual({ orden: undefined, tipo: 'Casa' });

    c.toggleServicio('Wifi', true);
    c.onSortChange({ target: { value: 'Precio menor' } } as unknown as Event);
    expect(ultimaPagina()?.[1]).toEqual({ orden: 'precio_menor', tipo: 'Casa', amenidades: ['Wifi'] });

    c.limpiarFiltros();
    expect(ultimaPagina()?.[1]).toEqual({ orden: undefined });
  });

  it('el precio se envía solo si se acota y espera a que el usuario suelte el control', () => {
    vi.useFakeTimers();
    const c = crear();
    const llamadas = pagina.mock.calls.length;

    c.actualizarPrecioMax({ target: { value: '250000' } } as unknown as Event);
    c.actualizarPrecioMax({ target: { value: '150000' } } as unknown as Event);
    expect(pagina.mock.calls.length).toBe(llamadas);

    vi.advanceTimersByTime(300);
    expect(pagina.mock.calls.length).toBe(llamadas + 1);
    expect(ultimaPagina()?.[1]).toEqual({ orden: undefined, precioMaximo: 150000 });
  });

  it('valida las fechas antes de buscar', () => {
    const c = crear();
    const llamadas = filtros.mock.calls.length;
    c.entrada.set('2020-01-01');
    c.salida.set('2020-01-05');
    c.buscar();

    expect(c.errorBusqueda()).toContain('pasado');
    expect(filtros.mock.calls.length).toBe(llamadas);
  });

  it('pagina en el servidor y muestra el estado de error', () => {
    const c = crear();

    c.irAPagina(2);
    expect(ultimaPagina()?.[2]).toBe(2);
    expect(c.paginaActual()).toBe(2);

    filtros.mockReturnValue(throwError(() => new Error('500')));
    c.reintentar();
    expect(c.errorCarga()).toBe(true);
    expect(c.stays()).toEqual([]);
  });

  it('descarta la respuesta de una petición anterior que llega tarde', () => {
    const c = crear();
    const lenta = new Subject<PaginaResultados>();
    pagina.mockReturnValueOnce(lenta);
    c.elegirTipo('Casa');
    c.elegirTipo('Cabaña');

    lenta.next({ contenido: [lista[1]], totalElementos: 1, totalPaginas: 1 });
    expect(c.paginados().map((a) => a.idAlojamiento)).toEqual([1, 2]);
  });

  it('desde el inicio con ?tipo= pide solo los alojamientos de ese tipo', () => {
    const c = crear({ tipo: 'Cabaña' });
    expect(c.filterType()).toBe('Cabaña');
    expect(ultimaPagina()?.[1]).toEqual({ orden: undefined, tipo: 'Cabaña' });
  });
});
