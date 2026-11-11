import { TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { Navbar } from './navbar';
import { RolUsuario, SessionService } from '../../services/session.service';
import { NotificacionesService } from '../../../notificaciones/services/notificaciones.service';

@Component({ template: '' })
class Vacia {}

/**
 * Barra de navegación copiada del proyecto base (agregada con HU-29, SCRUM-280, para llegar a
 * Mis favoritos).
 */
describe('Navbar', () => {
  const rol = signal<RolUsuario | null>(null);
  const correo = signal('');
  const cerrarSesion = vi.fn();

  function crear() {
    TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [
        provideRouter([
          { path: '', redirectTo: 'login', pathMatch: 'full' },
          { path: 'login', component: Vacia },
          { path: 'registro', component: Vacia },
          { path: 'perfil', component: Vacia },
          { path: 'favoritos', component: Vacia },
          { path: 'admin/alojamientos', component: Vacia },
          { path: 'admin/alojamientos/:id', component: Vacia },
          { path: '**', redirectTo: 'login' },
        ]),
        {
          provide: SessionService,
          useValue: { rol, correo, autenticado: () => rol() !== null, cerrarSesion },
        },
        { provide: NotificacionesService, useValue: { sinLeer: signal(0), actualizarContador: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(Navbar);
    fixture.detectChanges();
    return fixture;
  }

  const enlaces = (fixture: { nativeElement: HTMLElement }) =>
    Array.from(fixture.nativeElement.querySelectorAll('nav a') as NodeListOf<HTMLAnchorElement>).map((a) =>
      a.textContent?.trim(),
    );

  beforeEach(() => {
    rol.set(null);
    correo.set('');
    cerrarSesion.mockClear();
  });

  it('el huésped ve "Mis favoritos" y no los enlaces de pantallas que aún no existen', () => {
    rol.set('Huésped');
    correo.set('ana.perez@correo.com');
    const fixture = crear();

    expect(enlaces(fixture)).toEqual(['Mis favoritos']);
    expect(fixture.nativeElement.textContent).toContain('ana.perez');
    expect(fixture.nativeElement.textContent).toContain('AP');
  });

  it('el administrador ve su menú y no el de favoritos', () => {
    rol.set('Administrador');
    correo.set('admin@correo.com');
    const fixture = crear();

    expect(enlaces(fixture)).toEqual(['Alojamientos']);
  });

  it('sin sesión ofrece iniciar sesión y crear cuenta', () => {
    const fixture = crear();
    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Iniciar sesión');
    expect(texto).toContain('Crear cuenta');
    expect(enlaces(fixture)).toEqual([]);
  });

  it('el logo lleva al inicio del rol o, si aún no existe, al perfil', () => {
    rol.set('Huésped');
    correo.set('ana@correo.com');
    const c = crear().componentInstance;
    expect(c.homePath()).toBe('/perfil');
    expect(c.rutaDisponible('/admin/alojamientos/5')).toBe(true);
    expect(c.rutaDisponible('/home')).toBe(false);
  });

  it('cierra la sesión desde el menú de usuario', async () => {
    rol.set('Huésped');
    correo.set('ana@correo.com');
    const fixture = crear();
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigateByUrl');

    (fixture.nativeElement.querySelector('[aria-label="Abrir menú de usuario"]') as HTMLButtonElement).click();
    fixture.detectChanges();
    const salir = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (b) => b.textContent?.includes('Cerrar sesión'),
    )!;
    salir.click();

    expect(cerrarSesion).toHaveBeenCalled();
    expect(navegar).toHaveBeenCalledWith('/login');
  });
});
