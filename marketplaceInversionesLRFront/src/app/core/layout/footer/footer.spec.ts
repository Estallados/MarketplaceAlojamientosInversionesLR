import { TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { provideRouter } from '@angular/router';

import { Footer } from './footer';
import { RolUsuario, SessionService } from '../../services/session.service';

@Component({ template: '' })
class Vacia {}

describe('Footer', () => {
  const rol = signal<RolUsuario | null>(null);

  function crear() {
    TestBed.configureTestingModule({
      imports: [Footer],
      providers: [
        provideRouter([
          { path: 'home', component: Vacia },
          { path: 'explorar', component: Vacia },
          { path: 'login', component: Vacia },
          { path: 'registro', component: Vacia },
          { path: 'perfil', component: Vacia },
          { path: 'favoritos', component: Vacia },
          { path: 'host/alojamientos', component: Vacia },
          { path: '**', redirectTo: 'home' },
        ]),
        { provide: SessionService, useValue: { rol, autenticado: () => rol() !== null } },
      ],
    });
    const fixture = TestBed.createComponent(Footer);
    fixture.detectChanges();
    return fixture;
  }

  const enlaces = (f: { nativeElement: HTMLElement }) =>
    Array.from(f.nativeElement.querySelectorAll('footer a') as NodeListOf<HTMLAnchorElement>).map((a) =>
      a.textContent?.trim(),
    );

  beforeEach(() => rol.set(null));

  it('sin sesión muestra la navegación pública y el acceso a la cuenta', () => {
    expect(enlaces(crear())).toEqual(['Inicio', 'Explorar', 'Iniciar sesión', 'Crear cuenta']);
  });

  it('solo muestra los enlaces del rol que tienen pantalla en el router', () => {
    rol.set('Anfitrión');
    expect(enlaces(crear())).toEqual(['Mis alojamientos', 'Mi perfil']);
  });

  it('muestra el año actual', () => {
    expect(crear().nativeElement.textContent).toContain(`© ${new Date().getFullYear()} Inversiones LR`);
  });
});
