import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { MisResenas } from './mis-resenas';
import { ResenaService } from '../services/resena.service';
import { Resena } from '../models/resena';

/**
 * HU-03 (SCRUM-256): reseñas del huésped.
 */
describe('MisResenas (HU-03)', () => {
  const publicada: Resena = {
    idResena: 2,
    calificacion: 5,
    comentario: 'Excelente',
    estado: 'PUBLICADA',
    fecha: '2026-11-10T10:00:00',
    idReserva: 15,
    tituloAlojamiento: 'Casa en la playa',
  };
  const oculta: Resena = { ...publicada, idResena: 1, estado: 'OCULTA', tituloAlojamiento: 'Loft' };

  function crear(mias: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      imports: [MisResenas],
      providers: [provideRouter([]), { provide: ResenaService, useValue: { mias } }],
    });
    const fixture = TestBed.createComponent(MisResenas);
    fixture.detectChanges();
    return fixture;
  }

  it('CA-018: muestra cada reseña con su alojamiento, fecha y estado', () => {
    const fixture = crear(vi.fn().mockReturnValue(of([publicada, oculta])));
    const c = fixture.componentInstance;
    const texto = fixture.nativeElement.textContent as string;

    expect(c.reviews().length).toBe(2);
    expect(texto).toContain('Casa en la playa');
    expect(texto).toContain('10/11/2026');
    expect(c.estaOculta(oculta)).toBe(true);
    expect(texto).toContain('Oculta por moderación');
  });

  it('muestra el estado vacío y el error de carga', () => {
    const vacia = crear(vi.fn().mockReturnValue(of([])));
    expect(vacia.nativeElement.textContent).toContain('Aún no has dejado reseñas');

    TestBed.resetTestingModule();
    const error = crear(vi.fn().mockReturnValue(throwError(() => new Error('500'))));
    expect(error.componentInstance.errorCarga()).toBe(true);
  });
});
