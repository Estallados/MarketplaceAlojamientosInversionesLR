import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { MisNotificaciones } from './mis-notificaciones';
import { NotificacionesService } from '../services/notificaciones.service';
import { Notificacion } from '../models/notificacion';

/**
 * HU-02 Recibir y consultar notificaciones (SCRUM-254).
 */
describe('MisNotificaciones (HU-02)', () => {
  let servicio: {
    sinLeer: ReturnType<typeof signal<number>>;
    listar: ReturnType<typeof vi.fn>;
    marcarLeida: ReturnType<typeof vi.fn>;
    marcarTodasLeidas: ReturnType<typeof vi.fn>;
    mensajeDeError: ReturnType<typeof vi.fn>;
  };

  const confirmada: Notificacion = {
    idNotificacion: 3,
    tipo: 'RESERVA_CONFIRMADA',
    mensaje: 'Tu reserva 15 del 2026-12-10 al 2026-12-13 está confirmada.',
    fecha: '2026-11-10T09:00:00',
    leida: false,
  };
  const cancelada: Notificacion = {
    idNotificacion: 2,
    tipo: 'RESERVA_CANCELADA',
    mensaje: 'Se canceló la reserva 9.',
    fecha: '2026-11-08T09:00:00',
    leida: true,
  };
  const rechazada: Notificacion = {
    idNotificacion: 4,
    tipo: 'ALOJAMIENTO_RECHAZADO',
    mensaje: 'Tu publicación "Casa" fue rechazada: faltan fotos.',
    fecha: '2026-11-11T07:00:00',
    leida: false,
  };

  function crear() {
    TestBed.configureTestingModule({
      imports: [MisNotificaciones],
      providers: [{ provide: NotificacionesService, useValue: servicio }],
    });
    const c = TestBed.createComponent(MisNotificaciones).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    servicio = {
      sinLeer: signal(0),
      listar: vi.fn().mockReturnValue(of([cancelada, confirmada, rechazada])),
      marcarLeida: vi.fn().mockReturnValue(of({ ...confirmada, leida: true })),
      marcarTodasLeidas: vi.fn().mockReturnValue(of(2)),
      mensajeDeError: vi.fn((_e: unknown, defecto: string) => defecto),
    };
  });

  it('CA-010: muestra las notificaciones de confirmación, cancelación y rechazo con su título', () => {
    const c = crear();

    expect(c.items().map((n) => c.titulo(n))).toEqual([
      'Publicación rechazada',
      'Reserva confirmada',
      'Reserva cancelada',
    ]);
    expect(c.sinLeer()).toBe(2);
    expect(servicio.sinLeer()).toBe(2);
  });

  it('CA-013: lista de la más reciente a la más antigua y marca una como leída', () => {
    const c = crear();
    expect(c.items().map((n) => n.idNotificacion)).toEqual([4, 3, 2]);

    c.abrir(confirmada);

    expect(servicio.marcarLeida).toHaveBeenCalledWith(3);
    expect(c.items().find((n) => n.idNotificacion === 3)?.leida).toBe(true);
    expect(c.sinLeer()).toBe(1);

    servicio.marcarLeida.mockClear();
    c.abrir(cancelada);
    expect(servicio.marcarLeida).not.toHaveBeenCalled();
  });

  it('CA-013: marca todas como leídas', () => {
    const c = crear();

    c.marcarTodasLeidas();

    expect(servicio.marcarTodasLeidas).toHaveBeenCalled();
    expect(c.sinLeer()).toBe(0);
    expect(c.aviso()).toContain('marcadas como leídas');
    c.ngOnDestroy();
  });

  it('CA-013: muestra el estado vacío y el error de carga', () => {
    servicio.listar.mockReturnValue(of([]));
    const vacia = crear();
    expect(vacia.items()).toEqual([]);
    expect(vacia.errorCarga()).toBe(false);

    servicio.listar.mockReturnValue(throwError(() => new Error('500')));
    vacia.reintentar();
    expect(vacia.errorCarga()).toBe(true);
  });
});
