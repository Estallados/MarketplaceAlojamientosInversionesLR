import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ReservasAdmin } from './reservas';
import { AdminReservasService } from '../services/admin-reservas.service';
import { ReservaAdminVista } from '../models/reserva-admin';

/**
 * HU-11 Bloquear publicaciones (SCRUM-266): vista de reservas marcadas para revisión manual.
 */
describe('ReservasAdmin (HU-11)', () => {
  let servicio: { todas: ReturnType<typeof vi.fn> };

  const base = { fechaEntrada: '2026-12-01', fechaSalida: '2026-12-03', nombreAnfitrion: 'Luis', ciudad: 'Bogotá', completada: false };
  const lista: ReservaAdminVista[] = [
    { ...base, idReserva: 3, estado: 'EN_REVISION', nombreHuesped: 'Ana', tituloAlojamiento: 'Casa' },
    { ...base, idReserva: 2, estado: 'CONFIRMADA', nombreHuesped: 'Ana', tituloAlojamiento: 'Loft' },
    { ...base, idReserva: 1, estado: 'CANCELADA', nombreHuesped: 'Pedro', tituloAlojamiento: 'Casa' },
  ];

  function crear() {
    TestBed.configureTestingModule({
      imports: [ReservasAdmin],
      providers: [{ provide: AdminReservasService, useValue: servicio }],
    });
    const c = TestBed.createComponent(ReservasAdmin).componentInstance;
    c.ngOnInit();
    return c;
  }

  beforeEach(() => {
    servicio = { todas: vi.fn().mockReturnValue(of(lista)) };
  });

  it('CA-112: muestra por defecto las reservas marcadas para revisión manual', () => {
    const c = crear();

    expect(c.filtroEstado()).toBe('EN_REVISION');
    expect(c.reservations().map((r) => r.idReserva)).toEqual([3]);
    expect(c.enRevision()).toBe(1);
    expect(c.etiqueta(lista[0])).toBe('En revisión');
  });

  it('permite ver todas las reservas y muestra el error de carga', () => {
    const c = crear();
    c.cambiarEstado('');
    expect(c.reservations().length).toBe(3);

    servicio.todas.mockReturnValue(throwError(() => new Error('500')));
    c.cargar();
    expect(c.errorCarga()).toBe(true);
  });
});
