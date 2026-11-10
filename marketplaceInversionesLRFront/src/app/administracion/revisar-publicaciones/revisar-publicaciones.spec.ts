import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { RevisarPublicaciones } from './revisar-publicaciones';
import { AdminPublicacionesService } from '../services/admin-publicaciones.service';
import { PublicacionAdmin } from '../models/publicacion-admin';

/**
 * HU-32 Revisar, aprobar o rechazar publicaciones (SCRUM-250).
 */
describe('RevisarPublicaciones (HU-32)', () => {
  let componente: RevisarPublicaciones;
  let servicio: {
    todas: ReturnType<typeof vi.fn>;
    ejecutar: ReturnType<typeof vi.fn>;
    mensajeDeError: ReturnType<typeof vi.fn>;
  };

  const pendiente: PublicacionAdmin = {
    idAlojamiento: 8,
    titulo: 'Casa en la playa',
    nombreAnfitrion: 'Luis',
    ciudad: 'Cartagena',
    estado: 'PENDIENTE_REVISION',
  };
  const activa: PublicacionAdmin = { idAlojamiento: 9, titulo: 'Apto centro', ciudad: 'Bogotá', estado: 'ACTIVA' };

  beforeEach(() => {
    servicio = {
      todas: vi.fn().mockReturnValue(of([pendiente, activa])),
      ejecutar: vi.fn(),
      mensajeDeError: vi.fn((e: HttpErrorResponse, defecto: string) => e.error?.message ?? defecto),
    };
    TestBed.configureTestingModule({
      imports: [RevisarPublicaciones],
      providers: [provideRouter([]), { provide: AdminPublicacionesService, useValue: servicio }],
    });
    componente = TestBed.createComponent(RevisarPublicaciones).componentInstance;
    componente.ngOnInit();
  });

  it('CA-158: muestra la cola con las publicaciones pendientes de revisión', () => {
    expect(componente.filtroEstado()).toBe('PENDIENTE_REVISION');
    expect(componente.visibles()).toEqual([pendiente]);
    expect(componente.pendientes()).toBe(1);
    expect(componente.accionesDe(pendiente)).toEqual(['aprobar', 'rechazar']);
    expect(componente.accionesDe(activa)).toEqual([]);
  });

  it('CA-195: aprobar deja la publicación ACTIVA y muestra confirmación', () => {
    servicio.ejecutar.mockReturnValue(of({ ...pendiente, estado: 'ACTIVA' }));

    componente.pedirAccion('aprobar', pendiente);
    componente.confirmarAccion();

    expect(servicio.ejecutar).toHaveBeenCalledWith('aprobar', 8, '');
    expect(componente.publicaciones().find((p) => p.idAlojamiento === 8)?.estado).toBe('ACTIVA');
    expect(componente.mensaje()).toContain('aprobada');
    expect(componente.accion()).toBeNull();
  });

  it('CA-196: rechazar exige motivo y lo envía al backend', () => {
    servicio.ejecutar.mockReturnValue(of({ ...pendiente, estado: 'BORRADOR' }));
    componente.pedirAccion('rechazar', pendiente);

    componente.confirmarAccion();
    expect(componente.puedeConfirmar()).toBe(false);
    expect(servicio.ejecutar).not.toHaveBeenCalled();

    componente.motivo.set('  Faltan fotos del baño  ');
    componente.confirmarAccion();

    expect(servicio.ejecutar).toHaveBeenCalledWith('rechazar', 8, 'Faltan fotos del baño');
    expect(componente.publicaciones().find((p) => p.idAlojamiento === 8)?.estado).toBe('BORRADOR');
    expect(componente.mensaje()).toContain('rechazada');
  });

  it('CA-196: muestra el mensaje del backend si la acción no se puede aplicar', () => {
    servicio.ejecutar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'El alojamiento no está pendiente de revisión.' } })),
    );
    componente.pedirAccion('aprobar', pendiente);
    componente.confirmarAccion();

    expect(componente.errorAccion()).toBe('El alojamiento no está pendiente de revisión.');
    expect(componente.accion()).not.toBeNull();
  });
});
