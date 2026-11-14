import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { SegundoFactor } from './segundo-factor';
import { API_URL } from '../../core/services/api-url';

/**
 * HU-30 Autenticación multifactor (SCRUM-282): habilitar y deshabilitar el segundo factor desde el perfil.
 * Contrato: POST /usuarios/segundofactor/activar | confirmar | desactivar (código de app autenticadora).
 */
describe('SegundoFactor (HU-30)', () => {
  let http: HttpTestingController;
  const base = 'http://api/usuarios/segundofactor';

  function crear(habilitado: boolean | null) {
    TestBed.configureTestingModule({
      imports: [SegundoFactor],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SegundoFactor);
    fixture.componentRef.setInput('habilitado', habilitado);
    let cambios = 0;
    fixture.componentInstance.cambiado.subscribe(() => cambios++);
    fixture.detectChanges();
    return { fixture, c: fixture.componentInstance, cambios: () => cambios };
  }

  const el = (f: { nativeElement: HTMLElement }, sel: string) => f.nativeElement.querySelector(sel) as HTMLElement | null;
  const texto = (f: { nativeElement: HTMLElement }) => f.nativeElement.textContent ?? '';

  afterEach(() => http.verify());

  it('caso 8: deshabilitado → muestra el estado y solo la opción de habilitar', () => {
    const { fixture } = crear(false);
    expect(el(fixture, '[data-estado-2fa]')?.textContent).toContain('Deshabilitada');
    expect(el(fixture, '[data-activar-2fa]')).not.toBeNull();
    expect(el(fixture, '[data-desactivar-2fa]')).toBeNull();
  });

  it('caso 9: habilitado → muestra el estado y solo la opción de deshabilitar', () => {
    const { fixture } = crear(true);
    expect(el(fixture, '[data-estado-2fa]')?.textContent).toContain('Habilitada');
    expect(el(fixture, '[data-desactivar-2fa]')).not.toBeNull();
    expect(el(fixture, '[data-activar-2fa]')).toBeNull();
  });

  it('CA-178: activa solo cuando el servidor confirma el código y pide releer el perfil', async () => {
    const { fixture, c, cambios } = crear(false);
    el(fixture, '[data-activar-2fa]')!.click();
    const activar = http.expectOne(`${base}/activar`);
    expect(activar.request.method).toBe('POST');
    activar.flush({ secreto: 'JBSWY3DPEHPK3PXP', uri: 'otpauth://totp/Marketplace:ana@correo.com?secret=JBSWY3DPEHPK3PXP' });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el(fixture, '[data-secreto-2fa]')?.textContent).toContain('JBSW Y3DP EHPK 3PXP');
    expect(cambios()).toBe(0);

    c.cambiarCodigo('12a3456');
    fixture.detectChanges();
    el(fixture, '[data-confirmar-2fa]')!.click();
    const confirmar = http.expectOne(`${base}/confirmar`);
    expect(confirmar.request.body).toEqual({ codigo: '123456' });
    confirmar.flush({ message: 'Segundo factor habilitado.' });
    fixture.detectChanges();

    expect(cambios()).toBe(1);
    expect(c.paso()).toBe('estado');
    expect(c.secreto()).toBe('');
    expect(c.codigo()).toBe('');
    expect(texto(fixture)).toContain('Segundo factor habilitado');
  });

  it('código de la app inválido → informa el error y no da por habilitado', () => {
    const { fixture, c, cambios } = crear(false);
    c.iniciarActivacion();
    http.expectOne(`${base}/activar`).flush({ secreto: 'ABCD', uri: 'otpauth://totp/x?secret=ABCD' });
    c.cambiarCodigo('000000');
    c.confirmarActivacion();
    http
      .expectOne(`${base}/confirmar`)
      .flush({ message: 'El código no es válido.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(cambios()).toBe(0);
    expect(c.paso()).toBe('configurar');
    expect(c.error()).toBe('El código no es válido.');
    expect(c.codigo()).toBe('');
  });

  it('no envía la confirmación sin un código de 6 dígitos', () => {
    const { c } = crear(false);
    c.iniciarActivacion();
    http.expectOne(`${base}/activar`).flush({ secreto: 'ABCD', uri: 'otpauth://totp/x?secret=ABCD' });
    c.cambiarCodigo('123');
    c.confirmarActivacion();
    http.expectNone(`${base}/confirmar`);
    expect(c.error()).toContain('6 dígitos');
  });

  it('caso 10: con el segundo factor deshabilitado no intenta deshabilitarlo', () => {
    const { c } = crear(false);
    c.iniciarDesactivacion();
    c.cambiarCodigo('123456');
    c.confirmarDesactivacion();
    expect(c.paso()).toBe('estado');
    http.expectNone(`${base}/desactivar`);
  });

  it('caso 11: deshabilita tras la confirmación del servidor y pide releer el perfil', () => {
    const { fixture, c, cambios } = crear(true);
    el(fixture, '[data-desactivar-2fa]')!.click();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('¿Deshabilitar la verificación en dos pasos?');

    c.cambiarCodigo('654321');
    fixture.detectChanges();
    el(fixture, '[data-confirmar-desactivar-2fa]')!.click();
    const req = http.expectOne(`${base}/desactivar`);
    expect(req.request.body).toEqual({ codigo: '654321' });
    req.flush({ message: 'Segundo factor deshabilitado.' });

    expect(cambios()).toBe(1);
    expect(c.paso()).toBe('estado');
  });

  it('caso 12: error del servidor → muestra el error y conserva el estado', () => {
    const { fixture, c, cambios } = crear(false);
    c.iniciarActivacion();
    http
      .expectOne(`${base}/activar`)
      .flush({ message: 'Error interno' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(c.error()).toBe('Error interno');
    expect(c.paso()).toBe('estado');
    expect(cambios()).toBe(0);
    expect(el(fixture, '[data-estado-2fa]')?.textContent).toContain('Deshabilitada');
  });

  it('409 (ya habilitado en el servidor) → pide releer el estado real', () => {
    const { c, cambios } = crear(false);
    c.iniciarActivacion();
    http
      .expectOne(`${base}/activar`)
      .flush({ message: 'El segundo factor ya está habilitado.' }, { status: 409, statusText: 'Conflict' });
    expect(cambios()).toBe(1);
    expect(c.error()).toContain('ya está habilitado');
  });

  it('estado sin consultar → no ofrece habilitar ni deshabilitar', () => {
    const { fixture } = crear(null);
    expect(el(fixture, '[data-estado-2fa]')?.textContent).toContain('Sin consultar');
    expect(el(fixture, '[data-activar-2fa]')).toBeNull();
    expect(el(fixture, '[data-desactivar-2fa]')).toBeNull();
  });

  it('cancelar borra el secreto y el código de la memoria', () => {
    const { c } = crear(false);
    c.iniciarActivacion();
    http.expectOne(`${base}/activar`).flush({ secreto: 'ABCD', uri: 'otpauth://totp/x?secret=ABCD' });
    c.cambiarCodigo('123456');
    c.cancelar();
    expect(c.secreto()).toBe('');
    expect(c.codigo()).toBe('');
    expect(c.paso()).toBe('estado');
  });
});
