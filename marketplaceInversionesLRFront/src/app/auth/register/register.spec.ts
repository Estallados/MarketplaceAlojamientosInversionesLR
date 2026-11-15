import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Register } from './register';
import { API_URL } from '../../core/services/api-url';

// ======================================
// HU-06 REGISTRO · CA-135 POLÍTICA DE CONTRASEÑA
// ======================================

describe('Register (CA-135)', () => {

  let fixture: ComponentFixture<Register>;

  let http: HttpTestingController;

  let elemento: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    http = TestBed.inject(HttpTestingController);
    elemento = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  async function escribir(id: string, valor: string): Promise<void> {
    const input = elemento.querySelector<HTMLInputElement>(`#${id}`)!;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function errorContrasena(): string {
    return elemento.querySelector('[data-error="contrasena"]')?.textContent?.trim() ?? '';
  }

  it('CA-135 muestra bajo el campo exactamente qué le falta a la contraseña', async () => {

    await escribir('contrasena', 'clave2026');
    expect(errorContrasena()).toBe('La contraseña debe tener una mayúscula y un símbolo.');
    expect(elemento.querySelector('#contrasena')!.getAttribute('aria-invalid')).toBe('true');

    await escribir('contrasena', 'Clave#2026');
    expect(errorContrasena()).toBe('');
  });

  it('CA-135 con una contraseña débil no envía el registro e informa la validación', async () => {

    await escribir('nombre', 'Ana Gómez');
    await escribir('correo', 'ana@correo.com');
    await escribir('contrasena', 'Clave2026x');

    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    http.expectNone('http://api/auth/register');
    expect(fixture.componentInstance.error).toBe('La contraseña debe tener un símbolo.');
  });
});
