import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { ResenasAnfitrion } from './resenas-anfitrion';
import { ResenaService } from '../services/resena.service';
import { PublicacionHostService } from '../../alojamientos/services/publicacion-host.service';
import { Resena, ResumenResenas } from '../models/resena';

/**
 * HU-03 (SCRUM-256): reseñas de los alojamientos del anfitrión.
 */
describe('ResenasAnfitrion (HU-03)', () => {
  const resena = (id: number, idAlojamiento: number, titulo: string): Resena => ({
    idResena: id,
    calificacion: 4,
    comentario: 'Bien',
    fecha: '2026-11-10T10:00:00',
    idAlojamiento,
    tituloAlojamiento: titulo,
    nombreUsuario: 'Ana',
  });
  const resumen = (resenas: Resena[], promedio: number | null): ResumenResenas => ({
    idAlojamiento: null,
    promedio,
    cantidad: resenas.length,
    resenas: { contenido: resenas, pagina: 0, tamanio: 10, totalElementos: resenas.length, totalPaginas: 1 },
  });

  let deAnfitrion: ReturnType<typeof vi.fn>;

  function crear() {
    TestBed.configureTestingModule({
      imports: [ResenasAnfitrion],
      providers: [
        { provide: ResenaService, useValue: { deAnfitrion, mensajeDeError: (_e: unknown, d: string) => d } },
        {
          provide: PublicacionHostService,
          useValue: {
            obtenerMisAlojamientos: () =>
              of({
                contenido: [
                  { idAlojamiento: 7, titulo: 'Loft' },
                  { idAlojamiento: 3, titulo: 'Casa' },
                ],
                pagina: 0,
                totalPaginas: 1,
              }),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ResenasAnfitrion);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  beforeEach(() => {
    deAnfitrion = vi.fn((_p: number, _t: number, id?: number) =>
      of(id === 7 ? resumen([resena(2, 7, 'Loft')], 4) : resumen([resena(2, 7, 'Loft'), resena(1, 3, 'Casa')], 4.5)),
    );
  });

  it('muestra el promedio y la cantidad de reseñas de todos sus alojamientos', () => {
    const c = crear();

    expect(deAnfitrion).toHaveBeenCalledWith(0, 10, undefined);
    expect(c.average()).toBe('4,5');
    expect(c.cantidad()).toBe(2);
    expect(c.accommodations().map((a) => a.titulo)).toEqual(['Casa', 'Loft']);
  });

  it('CA-019: al elegir un alojamiento muestra únicamente las reseñas asociadas a él', () => {
    const c = crear();

    c.cambiarFiltro('7');

    expect(deAnfitrion).toHaveBeenLastCalledWith(0, 10, 7);
    expect(c.reviews().every((r) => r.idAlojamiento === 7)).toBe(true);
    expect(c.nombreFiltro()).toBe('Loft');
  });
});
