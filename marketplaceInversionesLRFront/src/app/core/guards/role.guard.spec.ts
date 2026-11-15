import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter
} from '@angular/router';

import { rolesExcluidosGuard } from './role.guard';
import { RolUsuario, SessionService } from '../services/session.service';

// ======================================
// GUARDA DE ROLES EXCLUIDOS
// Consultar disponibilidad (HU-21) no está permitido al administrador ni al anfitrión.
// ======================================

describe('rolesExcluidosGuard', () => {

  let rolActual: RolUsuario | null = null;

  const sesion = {
    autenticado: () => rolActual !== null,
    tieneRol: (...roles: RolUsuario[]) =>
      rolActual !== null && roles.includes(rolActual)
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SessionService, useValue: sesion }
      ]
    });
  });

  function ejecutar(): boolean | UrlTree {
    const ruta = {
      data: { rolesExcluidos: ['Administrador', 'Anfitrión'] }
    } as unknown as ActivatedRouteSnapshot;

    return TestBed.runInInjectionContext(
      () => rolesExcluidosGuard(ruta, {} as RouterStateSnapshot)
    ) as boolean | UrlTree;
  }

  function aInicio(resultado: boolean | UrlTree): boolean {
    const router = TestBed.inject(Router);
    return resultado instanceof UrlTree &&
      router.serializeUrl(resultado) === '/home';
  }

  it('deja pasar al visitante sin sesión', () => {
    rolActual = null;
    expect(ejecutar()).toBe(true);
  });

  it('deja pasar al huésped', () => {
    rolActual = 'Huésped';
    expect(ejecutar()).toBe(true);
  });

  it('devuelve al inicio al anfitrión', () => {
    rolActual = 'Anfitrión';
    expect(aInicio(ejecutar())).toBe(true);
  });

  it('devuelve al inicio al administrador', () => {
    rolActual = 'Administrador';
    expect(aInicio(ejecutar())).toBe(true);
  });
});
