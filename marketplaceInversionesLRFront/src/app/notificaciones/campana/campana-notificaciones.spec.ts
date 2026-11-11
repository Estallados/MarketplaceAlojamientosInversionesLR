import { TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { CampanaNotificaciones } from './campana-notificaciones';
import { NotificacionesService } from '../services/notificaciones.service';
import { SessionService } from '../../core/services/session.service';

@Component({ template: '' })
class Vacia {}

/**
 * HU-02 (SCRUM-254): campana con el contador de notificaciones sin leer.
 */
describe('CampanaNotificaciones (HU-02)', () => {
  const sinLeer = signal(0);
  const autenticado = signal(true);
  const actualizarContador = vi.fn(() => sinLeer.set(3));

  function crear() {
    TestBed.configureTestingModule({
      imports: [CampanaNotificaciones],
      providers: [
        provideRouter([{ path: 'otra', component: Vacia }]),
        { provide: NotificacionesService, useValue: { sinLeer, actualizarContador } },
        { provide: SessionService, useValue: { autenticado } },
      ],
    });
    const fixture = TestBed.createComponent(CampanaNotificaciones);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    sinLeer.set(0);
    autenticado.set(true);
    actualizarContador.mockClear();
  });

  it('CA-010: muestra el contador de sin leer y se actualiza al navegar', async () => {
    const fixture = crear();
    expect(actualizarContador).toHaveBeenCalledTimes(1);
    fixture.detectChanges();
    const enlace = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
    expect(enlace.textContent?.trim()).toBe('3');
    expect(enlace.getAttribute('aria-label')).toBe('Notificaciones, 3 sin leer');

    await TestBed.inject(Router).navigateByUrl('/otra');
    expect(actualizarContador).toHaveBeenCalledTimes(2);
  });

  it('sin sesión no consulta y deja el contador en cero', () => {
    autenticado.set(false);
    sinLeer.set(5);
    crear();

    expect(actualizarContador).not.toHaveBeenCalled();
    expect(sinLeer()).toBe(0);
  });
});
