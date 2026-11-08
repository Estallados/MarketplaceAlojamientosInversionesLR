import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { Recover } from './recover';
import { API_URL } from '../../services/api-url';

// ======================================
// HU-08 RECUPERACIÓN DE CONTRASEÑA
// ======================================

describe('Recover (HU-08)', () => {

  const baseUrl = 'http://localhost:8081';

  let fixture: ComponentFixture<Recover>;

  let http: HttpTestingController;

  let elemento: HTMLElement;

  beforeEach(async () => {

    await TestBed.configureTestingModule({
      imports: [Recover],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Recover);

    http = TestBed.inject(HttpTestingController);

    elemento = fixture.nativeElement as HTMLElement;

    await fixture.whenStable();

  });

  afterEach(() => {
    http.verify();
  });

  // ======================================
  // UTILIDADES
  // ======================================

  async function escribir(
    selector: string,
    valor: string
  ): Promise<void> {

    const input =
      elemento.querySelector<HTMLInputElement>(selector)!;

    input.value = valor;

    input.dispatchEvent(new Event('input'));

    await fixture.whenStable();

  }

  function boton(): HTMLButtonElement {
    return elemento.querySelector<HTMLButtonElement>(
      'button[type="submit"]'
    )!;
  }

  async function enviar(): Promise<void> {

    elemento
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));

    await fixture.whenStable();

  }

  function texto(): string {
    return elemento.textContent ?? '';
  }

  // ======================================
  // CA-038
  // ======================================

  it('CA-038 muestra el formulario, valida, envía la solicitud y confirma el restablecimiento', async () => {

    // 1. Visualización del formulario

    expect(texto()).toContain('Recuperar contraseña');

    expect(
      elemento.querySelector('#correo-recuperacion')
    ).toBeTruthy();

    expect(boton().textContent).toContain(
      'Enviar código de recuperación'
    );

    // 2. Validaciones del correo

    expect(boton().disabled).toBe(true);

    await escribir('#correo-recuperacion', 'correo-invalido');

    expect(boton().disabled).toBe(true);

    await escribir('#correo-recuperacion', '  Usuario@Correo.com ');

    expect(boton().disabled).toBe(false);

    // 3. Envío de la solicitud y comunicación con el servicio

    await enviar();

    expect(boton().textContent).toContain(
      'Solicitando código...'
    );

    const solicitud = http.expectOne(
      `${baseUrl}/auth/recuperar`
    );

    expect(solicitud.request.method).toBe('POST');

    expect(solicitud.request.body).toEqual({
      correo: 'usuario@correo.com'
    });

    solicitud.flush({
      message: 'Si el correo está registrado, se generó el enlace de recuperación'
    });

    await fixture.whenStable();

    // 4. Confirmación de la solicitud

    expect(texto()).toContain('Crear nueva contraseña');

    expect(
      elemento.querySelector('[role="status"]')?.textContent
    ).toContain('Si el correo está registrado');

    // 5. Validaciones de la nueva contraseña

    await escribir('#codigo-recuperacion', 'token-de-recuperacion');

    await escribir('#nueva-clave', 'debil');

    expect(texto()).toContain(
      'La contraseña todavía no cumple todos'
    );

    expect(boton().disabled).toBe(true);

    await escribir('#nueva-clave', 'Segura#2026');

    await escribir('#confirmar-clave', 'Otra#2026');

    expect(texto()).toContain('Las contraseñas no coinciden.');

    expect(boton().disabled).toBe(true);

    await escribir('#confirmar-clave', 'Segura#2026');

    expect(boton().disabled).toBe(false);

    // 6. Restablecer con el token

    await enviar();

    const restablecer = http.expectOne(
      `${baseUrl}/auth/restablecer`
    );

    expect(restablecer.request.method).toBe('POST');

    expect(restablecer.request.body).toEqual({
      token: 'token-de-recuperacion',
      contrasenia: 'Segura#2026'
    });

    restablecer.flush({
      message: 'Contraseña actualizada. Ya puedes iniciar sesión.'
    });

    await fixture.whenStable();

    // 7. Confirmación final

    expect(texto()).toContain('Contraseña actualizada');

    expect(
      elemento.querySelector('[role="status"]')?.textContent
    ).toContain('La contraseña se actualizó correctamente.');

    expect(
      elemento.querySelector('a[href="/login"]')
    ).toBeTruthy();

  });

  it('CA-038 muestra el error del backend cuando el token es inválido o venció', async () => {

    // Ir directo al formulario con el código

    const yaTengo = Array.from(
      elemento.querySelectorAll('button')
    ).find(b => b.textContent?.includes('Ya tengo un código'))!;

    yaTengo.click();

    await fixture.whenStable();

    await escribir('#codigo-recuperacion', 'token-vencido');

    await escribir('#nueva-clave', 'Segura#2026');

    await escribir('#confirmar-clave', 'Segura#2026');

    await enviar();

    http
      .expectOne(`${baseUrl}/auth/restablecer`)
      .flush(
        { message: 'El token de recuperación no es válido o ya venció.' },
        { status: 400, statusText: 'Bad Request' }
      );

    await fixture.whenStable();

    expect(
      elemento.querySelector('[role="alert"]')?.textContent
    ).toContain('El token de recuperación no es válido o ya venció.');

    expect(texto()).toContain('Crear nueva contraseña');

  });

  it('CA-038 informa cuando no hay conexión con el servidor al solicitar la recuperación', async () => {

    await escribir('#correo-recuperacion', 'usuario@correo.com');

    await enviar();

    http
      .expectOne(`${baseUrl}/auth/recuperar`)
      .error(new ProgressEvent('error'), { status: 0 });

    await fixture.whenStable();

    expect(
      elemento.querySelector('[role="alert"]')?.textContent
    ).toContain('No se pudo conectar con Spring Boot.');

    expect(texto()).toContain('Recuperar contraseña');

  });

});
