import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Route,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter
} from '@angular/router';

import { routes } from '../../app.routes';
import { roleGuard, rolesExcluidosGuard } from './role.guard';
import { RolUsuario, SessionService } from '../services/session.service';

// ======================================
// RNF-003 (CA-131, CA-134) EN EL FRONT: TODAS LAS RUTAS
// Recorre app.routes: cada ruta que no es pública tiene guarda; sin sesión
// manda al login y con un rol sin permiso devuelve al inicio. Si se agrega
// una ruta sin guarda y no está en PUBLICAS, la prueba falla.
// ======================================

/** Rutas abiertas sin sesión. */
const PUBLICAS = new Set([
  '',
  '**',
  'home',
  'explorar',
  'login',
  'registro',
  'recuperar-contrasena',
  'restablecer-contrasena',
  'alojamientos/:id',
  'alojamientos/:id/consultar-disponibilidad'
]);

const ROLES: RolUsuario[] = ['Huésped', 'Anfitrión', 'Administrador'];

describe('Rutas protegidas del front (CA-131, CA-134)', () => {

  let rolActual: RolUsuario | null = null;

  const sesion = {
    autenticado: () => rolActual !== null,
    tieneRol: (...roles: RolUsuario[]) => rolActual !== null && roles.includes(rolActual)
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SessionService, useValue: sesion }]
    });
  });

  /** Resultado de las guardas de la ruta: true o la URL a la que redirige. */
  function entrar(ruta: Route, rol: RolUsuario | null): true | string {
    rolActual = rol;
    const snapshot = { data: ruta.data ?? {} } as unknown as ActivatedRouteSnapshot;
    for (const guarda of (ruta.canActivate ?? []) as CanActivateFn[]) {
      const resultado = TestBed.runInInjectionContext(() => guarda(snapshot, {} as RouterStateSnapshot));
      if (resultado instanceof UrlTree) {
        return TestBed.inject(Router).serializeUrl(resultado);
      }
      if (resultado !== true) {
        return String(resultado);
      }
    }
    return true;
  }

  const protegidas = routes.filter(r => !PUBLICAS.has(r.path ?? ''));

  it('CA-131 toda ruta que no es pública tiene la guarda de sesión y rol', () => {
    const sinGuarda = protegidas
      .filter(r => !(r.canActivate ?? []).includes(roleGuard))
      .map(r => r.path);

    expect(sinGuarda).toEqual([]);
    expect(protegidas.length).toBeGreaterThan(20);
  });

  it('CA-134 sin sesión, cada ruta protegida manda al inicio de sesión', () => {
    for (const ruta of protegidas) {
      expect(entrar(ruta, null), ruta.path).toBe('/login');
    }
  });

  it('CA-131 con un rol sin permiso, cada ruta devuelve al inicio; con un rol permitido, entra', () => {
    for (const ruta of protegidas) {
      const permitidos = (ruta.data?.['roles'] as RolUsuario[] | undefined) ?? ROLES;
      for (const rol of ROLES) {
        const esperado = permitidos.includes(rol) ? true : '/home';
        expect(entrar(ruta, rol), `${ruta.path} como ${rol}`).toBe(esperado);
      }
    }
  });

  it('CA-131 consultar disponibilidad es pública, pero el administrador y el anfitrión no entran', () => {
    const ruta = routes.find(r => r.path === 'alojamientos/:id/consultar-disponibilidad')!;

    expect(ruta.canActivate).toContain(rolesExcluidosGuard);
    expect(entrar(ruta, null)).toBe(true);
    expect(entrar(ruta, 'Huésped')).toBe(true);
    expect(entrar(ruta, 'Anfitrión')).toBe('/home');
    expect(entrar(ruta, 'Administrador')).toBe('/home');
  });

  it('CA-131 las rutas del administrador solo admiten al administrador', () => {
    const admin = routes.filter(r => r.path?.startsWith('admin/') && r.path !== 'admin/tarifas');

    expect(admin.length).toBeGreaterThan(5);
    for (const ruta of admin) {
      expect(ruta.data?.['roles'], ruta.path).toEqual(['Administrador']);
    }
  });
});
