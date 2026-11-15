import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { ProfileView } from './profile-view';
import { API_URL } from '../../core/services/api-url';

/**
 * HU-30 (SCRUM-282): el perfil lee el estado del segundo factor de GET /usuarios/perfil y lo vuelve a
 * consultar después de cada cambio confirmado por el servidor.
 */
describe('ProfileView: estado del segundo factor (HU-30)', () => {
  let http: HttpTestingController;
  const perfil = 'http://api/usuarios/perfil';
  const usuario = { nombre: 'Ana', correo: 'ana@correo.com', telefono: '3001234567', estado: 'ACTIVO', rol: 'HUESPED' };

  function crear() {
    sessionStorage.setItem('token', 'token');
    sessionStorage.setItem('rol', 'HUESPED');
    TestBed.configureTestingModule({
      imports: [ProfileView],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProfileView);
    fixture.detectChanges();
    return fixture;
  }

  const estado = (f: { nativeElement: HTMLElement }) =>
    (f.nativeElement.querySelector('[data-estado-2fa]') as HTMLElement).textContent?.trim();

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('muestra «Habilitada» cuando el backend devuelve totpHabilitado = true', () => {
    const f = crear();
    http.expectOne(perfil).flush({ ...usuario, totpHabilitado: true });
    f.detectChanges();
    expect(estado(f)).toBe('Habilitada');
  });

  it('CA-177: null o ausente en el backend equivale a deshabilitado', () => {
    const f = crear();
    http.expectOne(perfil).flush({ ...usuario, totpHabilitado: null });
    f.detectChanges();
    expect(estado(f)).toBe('Deshabilitada');
  });

  it('caso 12: si el perfil no se puede consultar, no supone ningún estado', () => {
    const f = crear();
    http.expectOne(perfil).flush({ message: 'Error' }, { status: 500, statusText: 'Server Error' });
    f.detectChanges();
    expect(estado(f)).toBe('Sin consultar');
    expect(f.nativeElement.querySelector('[data-activar-2fa]')).toBeNull();
  });

  it('después de habilitarlo vuelve a consultar el perfil y muestra el estado del servidor', () => {
    const f = crear();
    http.expectOne(perfil).flush({ ...usuario, totpHabilitado: false });
    f.detectChanges();

    (f.nativeElement.querySelector('[data-activar-2fa]') as HTMLButtonElement).click();
    http.expectOne('http://api/usuarios/segundofactor/activar').flush({ secreto: 'ABCD', uri: 'otpauth://totp/x?secret=ABCD' });
    f.detectChanges();
    const tarjeta = f.debugElement.query((d) => d.name === 'app-segundo-factor').componentInstance;
    tarjeta.cambiarCodigo('123456');
    tarjeta.confirmarActivacion();
    http.expectOne('http://api/usuarios/segundofactor/confirmar').flush({ message: 'Segundo factor habilitado.' });

    http.expectOne(perfil).flush({ ...usuario, totpHabilitado: true });
    f.detectChanges();
    expect(estado(f)).toBe('Habilitada');
  });
});
