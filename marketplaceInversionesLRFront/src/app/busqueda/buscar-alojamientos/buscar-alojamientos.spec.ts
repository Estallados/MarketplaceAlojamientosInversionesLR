import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { BuscarAlojamientos } from './buscar-alojamientos';
import { BusquedaService } from '../services/busqueda.service';
import { AlojamientoResultado } from '../models/busqueda';


describe('BuscarAlojamientos (HU-23)', () => {
  let servicio: BusquedaService;
  let disponibles: ReturnType<typeof vi.fn>;
  let buscar: ReturnType<typeof vi.fn>;

  const lista: AlojamientoResultado[] = [
    { idAlojamiento: 1, titulo: 'Casa', ciudad: 'Bogotá', tipo: 'Casa', capacidad: 4, precioNoche: 200000, amenidades: ['Wifi', 'Piscina'], promedioCalificacion: 4.2 },
    { idAlojamiento: 2, titulo: 'Loft', ciudad: 'Bogotá', tipo: 'Apartamento', capacidad: 2, precioNoche: 100000, amenidades: ['Wifi'], promedioCalificacion: 4.9 },
    { idAlojamiento: 3, titulo: 'Cabaña', ciudad: 'Guatavita', tipo: 'Cabaña', capacidad: 6, precioNoche: 300000, amenidades: ['Chimenea'], promedioCalificacion: 3.5 },
  ];

  function crear(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [BuscarAlojamientos],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
      ],
    });
    servicio = TestBed.inject(BusquedaService);
    disponibles = vi.spyOn(servicio, 'disponibles').mockReturnValue(of(lista)) as unknown as ReturnType<typeof vi.fn>;
    buscar = vi.spyOn(servicio, 'buscar').mockReturnValue(of([lista[0]])) as unknown as ReturnType<typeof vi.fn>;
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const c = TestBed.createComponent(BuscarAlojamientos).componentInstance;
    c.ngOnInit();
    return c;
  }

  it('CA-087: busca por ciudad, fechas y huéspedes en el backend', () => {
    const c = crear();
    expect(disponibles).toHaveBeenCalled();

    const manana = new Date(Date.now() + 86400000).toISOString().slice(0, 10);
    const pasado = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10);
    c.ciudad.set(' Bogotá ');
    c.entrada.set(manana);
    c.salida.set(pasado);
    c.huespedes.set('2');
    c.buscar();

    expect(buscar).toHaveBeenCalledWith({ ciudad: 'Bogotá', llegada: manana, salida: pasado, huespedes: 2 }, 100);
    expect(c.busquedaAplicada()).toBe(true);
    expect(c.visible().map((a) => a.idAlojamiento)).toEqual([1]);
  });

  it('CA-087: filtra por tipo, precio y servicios y ordena', () => {
    const c = crear();

    c.elegirTipo('Casa');
    expect(c.visible().map((a) => a.idAlojamiento)).toEqual([1]);

    c.elegirTipo('Todos');
    c.toggleServicio('Wifi', true);
    expect(c.visible().map((a) => a.idAlojamiento)).toEqual([1, 2]);

    c.precioMax.set(150000);
    expect(c.visible().map((a) => a.idAlojamiento)).toEqual([2]);

    c.limpiarFiltros();
    c.sortBy.set('Precio menor');
    expect(c.visible().map((a) => a.idAlojamiento)).toEqual([2, 1, 3]);
  });

  it('valida las fechas antes de buscar', () => {
    const c = crear();
    c.entrada.set('2020-01-01');
    c.salida.set('2020-01-05');
    c.buscar();

    expect(c.errorBusqueda()).toContain('pasado');
    expect(buscar).not.toHaveBeenCalled();
  });

  it('pagina los resultados y muestra el estado de error', () => {
    const c = crear();
    const muchos = Array.from({ length: 20 }, (_, i) => ({ ...lista[0], idAlojamiento: i + 1 }));
    c.stays.set(muchos);
    c.precioMin.set(0);
    c.precioMax.set(1000000);

    expect(c.totalPaginas()).toBe(3);
    expect(c.paginados().length).toBe(9);
    c.irAPagina(2);
    expect(c.paginados().length).toBe(2);

    disponibles.mockReturnValue(throwError(() => new Error('500')));
    c.reintentar();
    expect(c.errorCarga()).toBe(true);
    expect(c.stays()).toEqual([]);
  });
});
