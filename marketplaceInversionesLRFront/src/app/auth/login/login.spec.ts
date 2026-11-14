import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { NgForm } from '@angular/forms';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { Login } from './login';
import { API_URL } from '../../core/services/api-url';
import { SessionService } from '../../core/services/session.service';
import { roleGuard } from '../../core/guards/role.guard';

/**
 * HU-30 Autenticación multifactor (SCRUM-282): inicio de sesión con y sin segundo factor.
 * Contrato: POST /auth/login responde 200 {token, rol, correo} o, con el segundo factor habilitado,
 * 401 {requiereCodigoCorreo: true} y se repite con `codigo` (CA-174, CA-177).
 */
describe('Login con segundo factor (HU-30)', () => {
  let http: HttpTestingController;
  let sesion: SessionService;
  const login = 'http://api/auth/login';

  /** JWT sin firma válida para el front: solo se lee `exp`. */
  const jwt = (() => {
    const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
    return `${b64({ alg: 'HS256' })}.${b64({ sub: 'ana@correo.com', exp: Math.floor(Date.now() / 1000) + 3600 })}.firma`;
  })();
  const sesionOk = { token: jwt, rol: 'HUESPED', correo: 'ana@correo.com' };
  const pideCodigo = {
    message: 'Te enviamos un código de verificación a tu correo; vence en 15 minutos.',
    requiereSegundoFactor: true,
    requiereCodigoCorreo: true,
    metodoSegundoFactor: 'CORREO',
  };
  const no401 = { status: 401, statusText: 'Unauthorized' };

  function crear() {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SessionService);
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    // El botón de Google consulta su configuración al cargar.
    http.expectOne('http://api/auth/google/configuracion').flush({ habilitado: false, clientId: null });
    return fixture;
  }

  /** Envía el formulario tal como lo hace (ngSubmit) con un formulario válido. */
  async function enviar(f: ReturnType<typeof crear>) {
    f.componentInstance.iniciarSesion({ invalid: false } as NgForm);
    f.detectChanges();
    await f.whenStable();
  }

  async function credenciales(f: ReturnType<typeof crear>) {
    f.componentInstance.cambiarCorreo('ana@correo.com');
    f.componentInstance.cambiarContrasenia('Clave#2026');
    await enviar(f);
  }

  /** Lleva el login hasta el paso del código del correo. */
  async function hastaElCodigo(f: ReturnType<typeof crear>) {
    await credenciales(f);
    http.expectOne(login).flush(pideCodigo, no401);
    f.detectChanges();
    await f.whenStable();
  }

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('caso 1 (CA-177): sin segundo factor inicia sesión directamente, sin pedir código', async () => {
    const f = crear();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await credenciales(f);
    const req = http.expectOne(login);
    expect(req.request.body).toEqual({ correo: 'ana@correo.com', contrasenia: 'Clave#2026' });
    req.flush(sesionOk);

    expect(sesion.autenticado()).toBe(true);
    expect(f.componentInstance.requiereSegundoFactor).toBe(false);
    expect(navegar).toHaveBeenCalledWith('/home');
  });

  it('caso 2: con segundo factor pide el código del correo y no guarda sesión', async () => {
    const f = crear();
    await hastaElCodigo(f);

    expect(f.componentInstance.requiereSegundoFactor).toBe(true);
    expect(f.nativeElement.querySelector('#codigo')).not.toBeNull();
    expect(f.nativeElement.textContent).toContain('Te enviamos un código');
    expect(sesion.autenticado()).toBe(false);
    expect(sessionStorage.getItem('token')).toBeNull();
  });

  it('caso 3 (CA-174): con el código correcto entrega la sesión', async () => {
    const f = crear();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    await hastaElCodigo(f);
    f.componentInstance.cambiarCodigo('482913');
    await enviar(f);

    const req = http.expectOne(login);
    expect(req.request.body).toEqual({ correo: 'ana@correo.com', contrasenia: 'Clave#2026', codigo: '482913' });
    req.flush(sesionOk);

    expect(sesion.autenticado()).toBe(true);
  });

  it('caso 4: con un código incorrecto niega el acceso y muestra el motivo', async () => {
    const f = crear();
    await hastaElCodigo(f);
    f.componentInstance.cambiarCodigo('000000');
    await enviar(f);
    http.expectOne(login).flush(
      {
        message: 'El código del correo no es válido o ya venció. Revísalo o vuelve a iniciar sesión.',
        requiereSegundoFactor: true,
        requiereCodigoCorreo: true,
      },
      no401,
    );
    f.detectChanges();

    expect(sesion.autenticado()).toBe(false);
    expect(f.componentInstance.requiereSegundoFactor).toBe(true);
    expect(f.componentInstance.error).toContain('no es válido o ya venció');
  });

  it('caso 5: código vencido o intentos agotados → permite pedir un código nuevo', async () => {
    const f = crear();
    await hastaElCodigo(f);
    f.componentInstance.cambiarCodigo('111111');
    await enviar(f);
    http.expectOne(login).flush(
      {
        message: 'Superaste los intentos permitidos para este código del correo.',
        requiereSegundoFactor: true,
        requiereCodigoCorreo: true,
      },
      { status: 429, statusText: 'Too Many Requests' },
    );
    f.detectChanges();
    expect(f.componentInstance.error).toContain('Superaste los intentos');
    expect(sesion.autenticado()).toBe(false);

    (f.nativeElement.querySelector('[data-reenviar-codigo-inicio]') as HTMLButtonElement).click();
    const reenvio = http.expectOne(login);
    expect(reenvio.request.body).toEqual({ correo: 'ana@correo.com', contrasenia: 'Clave#2026' });
    reenvio.flush(pideCodigo, no401);
    f.detectChanges();

    // Un reenvío no es un error: se informa como mensaje.
    expect(f.componentInstance.error).toBe('');
    expect(f.componentInstance.mensaje).toContain('Te enviamos un código');
    expect(f.componentInstance.requiereSegundoFactor).toBe(true);
  });

  it('caso 6: sin código no envía la petición ni permite el acceso', async () => {
    const f = crear();
    await hastaElCodigo(f);
    await enviar(f);
    http.expectNone(login);
    expect(f.componentInstance.error).toContain('6 dígitos');
    expect(sesion.autenticado()).toBe(false);
  });

  it('caso 7: mientras el código está pendiente las rutas protegidas redirigen al login', async () => {
    const f = crear();
    await hastaElCodigo(f);
    const resultado = TestBed.runInInjectionContext(() =>
      roleGuard({ data: { roles: ['Huésped'] } } as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    expect(resultado instanceof UrlTree).toBe(true);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/login');
  });

  it('Google con segundo factor: pide el código de la app y lo envía con el mismo ID token', () => {
    const f = crear();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const c = f.componentInstance;
    const privado = c as unknown as { googleIdToken: string; ejecutarLoginGoogle(): void };
    privado.googleIdToken = 'id-token-google';
    privado.ejecutarLoginGoogle();
    http
      .expectOne('http://api/auth/google')
      .flush({ message: 'Ingresa el código de tu aplicación autenticadora.', requiereSegundoFactor: true }, no401);
    f.detectChanges();

    expect(c.googleRequiereCodigo).toBe(true);
    expect(f.nativeElement.querySelector('[data-codigo-google]')).not.toBeNull();
    expect(sesion.autenticado()).toBe(false);

    c.cambiarCodigoGoogle('123456');
    c.confirmarCodigoGoogle();
    const req = http.expectOne('http://api/auth/google');
    expect(req.request.body).toEqual({ idToken: 'id-token-google', rol: 'HUESPED', codigo: '123456' });
    req.flush({ ...sesionOk, cuentaNueva: false });

    expect(sesion.autenticado()).toBe(true);
    expect(c.googleRequiereCodigo).toBe(false);
    expect(privado.googleIdToken).toBe('');
  });
});
