import { TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { Home } from './home';
import { BusquedaService } from '../../busqueda/services/busqueda.service';
import { AlojamientoResultado } from '../../busqueda/models/busqueda';
import { FavoritoService } from '../../favoritos/services/favorito.service';
import { RolUsuario, SessionService } from '../../core/services/session.service';

@Component({ template: '' })
class Vacia {}

describe('Home (página de inicio)', () => {
  const rol = signal<RolUsuario | null>(null);
  const cerrarSesion = vi.fn();
  const toggle = vi.fn();

  const lista: AlojamientoResultado[] = [
    { idAlojamiento: 1, titulo: 'Casa del lago', ciudad: 'Guatapé', tipo: 'Casa', capacidad: 6, precioNoche: 320000, amenidades: ['Wifi'], imagenes: ['https://ejemplo.com/1.jpg'], promedioCalificacion: 4.8 },
    { idAlojamiento: 2, titulo: 'Loft', ciudad: 'Bogotá', tipo: 'Apartamento', capacidad: 1, precioNoche: 150000, amenidades: [] },
  ];

  let disponibles: ReturnType<typeof vi.fn>;
  let tipos: ReturnType<typeof vi.fn>;

  function crear(opciones: { alojamientos?: unknown; tipos?: unknown } = {}) {
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [
        provideRouter([
          { path: 'explorar', component: Vacia },
          { path: 'alojamientos/:id', component: Vacia },
        ]),
        { provide: SessionService, useValue: { rol, autenticado: () => rol() !== null, tieneRol: (...r: RolUsuario[]) => rol() !== null && r.includes(rol()!), cerrarSesion } },
        {
          provide: FavoritoService,
          useValue: {
            disponible: () => rol() === 'Huésped',
            esFavorito: () => false,
            mensaje: signal<string | null>(null),
            cargarSiCorresponde: vi.fn(),
            toggle,
          },
        },
      ],
    });
    const servicio = TestBed.inject(BusquedaService);
    disponibles = vi
      .spyOn(servicio, 'disponibles')
      .mockReturnValue((opciones.alojamientos ?? of(lista)) as never) as unknown as ReturnType<typeof vi.fn>;
    tipos = vi
      .spyOn(servicio, 'cantidadPorTipo')
      .mockReturnValue((opciones.tipos ?? of({ Casa: 3, Apartamento: 5, Finca: 0, Cabaña: 3 })) as never) as unknown as ReturnType<typeof vi.fn>;
    const fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
    return fixture;
  }

  const el = (f: { nativeElement: HTMLElement }) => f.nativeElement;

  beforeEach(() => {
    rol.set(null);
    cerrarSesion.mockClear();
    toggle.mockClear();
  });

  it('muestra los alojamientos disponibles con enlace a su detalle', () => {
    const f = crear();
    expect(disponibles).toHaveBeenCalledWith(100);
    const tarjetas = el(f).querySelectorAll('[data-alojamiento]');
    expect(tarjetas.length).toBe(2);
    expect(tarjetas[0].textContent).toContain('Casa del lago');
    expect(tarjetas[0].querySelector('a')?.getAttribute('href')).toBe('/alojamientos/1');
    expect(tarjetas[1].textContent).toContain('Sin fotografía');
    expect(tarjetas[1].textContent).toContain('Hasta 1 huésped');
  });

  it('ordena los tipos por cantidad, omite los vacíos y los enlaza a Explorar filtrado', () => {
    const f = crear();
    expect(tipos).toHaveBeenCalled();
    expect(f.componentInstance.tipos().map((t) => t.nombre)).toEqual(['Apartamento', 'Cabaña', 'Casa']);
    const enlace = el(f).querySelector('[data-tipo]') as HTMLAnchorElement;
    expect(enlace.getAttribute('href')).toBe('/explorar?tipo=Apartamento');
    expect(enlace.textContent).toContain('5 alojamientos');
  });

  it('ilustra cada tipo con la foto de un alojamiento real o, si no hay, con una foto de referencia por nombre', () => {
    const f = crear();
    const porNombre = Object.fromEntries(f.componentInstance.tipos().map((t) => [t.nombre, t.foto]));
    expect(porNombre['Casa']).toBe('https://ejemplo.com/1.jpg');
    expect(porNombre['Cabaña']).toContain('images.unsplash.com');
    expect(porNombre['Apartamento']).toContain('images.unsplash.com');
    expect(porNombre['Cabaña']).not.toBe(porNombre['Apartamento']);
  });

  it('si fallan las consultas muestra el error y permite reintentar', () => {
    const f = crear({ alojamientos: throwError(() => new Error('x')), tipos: throwError(() => new Error('x')) });
    const alertas = el(f).querySelectorAll('[role="alert"]');
    expect(alertas.length).toBe(2);
    expect(alertas[0].textContent).toContain('No se pudieron cargar los alojamientos.');

    disponibles.mockReturnValue(of(lista));
    (alertas[0].querySelector('button') as HTMLButtonElement).click();
    f.detectChanges();
    expect(el(f).querySelectorAll('[data-alojamiento]').length).toBe(2);
  });

  it('la búsqueda valida las fechas y abre Explorar con los filtros', () => {
    const f = crear();
    const c = f.componentInstance;
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    c.entrada.set('2099-01-10');
    c.buscar();
    expect(c.errorBusqueda()).toBe('Indica la fecha de entrada y la de salida.');
    expect(navegar).not.toHaveBeenCalled();

    c.ciudad.set(' Medellín ');
    c.salida.set('2099-01-12');
    c.huespedes.set('2');
    c.buscar();
    expect(c.errorBusqueda()).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/explorar'], {
      queryParams: { ciudad: 'Medellín', llegada: '2099-01-10', salida: '2099-01-12', huespedes: 2 },
    });
  });

  it('"Publicar alojamiento": el visitante va al registro como anfitrión y el anfitrión a sus alojamientos', () => {
    const f = crear();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const navegarUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    f.componentInstance.publicar();
    expect(navegar).toHaveBeenCalledWith(['/registro'], { queryParams: { rol: 'anfitrion' } });

    rol.set('Anfitrión');
    f.componentInstance.publicar();
    expect(navegarUrl).toHaveBeenCalledWith('/host/alojamientos');
  });

  it('el huésped confirma el cierre de sesión antes de registrarse como anfitrión', () => {
    rol.set('Huésped');
    const f = crear();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    (el(f).querySelector('[data-publicar]') as HTMLButtonElement).click();
    f.detectChanges();
    expect(el(f).querySelector('[role="dialog"]')).not.toBeNull();
    expect(cerrarSesion).not.toHaveBeenCalled();

    (el(f).querySelector('[data-aceptar]') as HTMLButtonElement).click();
    f.detectChanges();
    expect(cerrarSesion).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith(['/registro'], { queryParams: { rol: 'anfitrion' } });
    expect(el(f).querySelector('[role="dialog"]')).toBeNull();
  });

  it('el huésped puede guardar un favorito sin abrir el detalle', () => {
    rol.set('Huésped');
    const f = crear();
    (el(f).querySelector('[data-favorito]') as HTMLButtonElement).click();
    expect(toggle).toHaveBeenCalledWith(1);
  });

  it('el administrador no ve la sección para publicar', () => {
    rol.set('Administrador');
    const f = crear();
    expect(el(f).querySelector('[data-publicar]')).toBeNull();
    expect(el(f).querySelector('[data-favorito]')).toBeNull();
  });
});
