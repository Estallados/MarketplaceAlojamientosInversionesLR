import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { ResenasAdmin } from './resenas';
import { API_URL } from '../../core/services/api-url';
import { RolUsuario, SessionService } from '../../core/services/session.service';
import { roleGuard } from '../../core/guards/role.guard';
import { Resena } from '../../resenas/models/resena';
import { MAX_MOTIVO_RESENA } from '../services/admin-resenas.service';
import { routes } from '../../app.routes';

/**
 * HU-14 Ocultar reseñas que incumplan reglas de moderación (SCRUM-272): moderación del administrador.
 */
describe('ResenasAdmin (HU-14)', () => {
  const url = 'http://api/admin/resenas';
  let http: HttpTestingController;
  let rolActual: RolUsuario | null = 'Administrador';

  /** Sesión de prueba: solo decide el rol con el que entra el usuario. */
  const sesion = {
    autenticado: () => rolActual !== null,
    tieneRol: (...roles: RolUsuario[]) => rolActual !== null && roles.includes(rolActual),
    obtenerToken: () => null,
  };

  const proveedores = () => [
    provideRouter([]),
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: API_URL, useValue: 'http://api' },
    { provide: SessionService, useValue: sesion },
  ];

  /** roleGuard real con los datos de la ruta admin/resenas. */
  const entrar = () => {
    const ruta = routes.find((r) => r.path === 'admin/resenas')!;
    return TestBed.runInInjectionContext(() =>
      roleGuard({ data: ruta.data } as unknown as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
  };

  const resena = (id: number, cambios: Partial<Resena> = {}): Resena => ({
    idResena: id,
    calificacion: 4,
    comentario: `Comentario ${id}`,
    estado: 'PUBLICADA',
    fecha: '2026-11-01T10:00:00',
    idUsuario: 10,
    idReserva: 100 + id,
    idAlojamiento: 7,
    nombreUsuario: 'Ana',
    tituloAlojamiento: 'Casa en el lago',
    ...cambios,
  });

  const pagina = (contenido: Resena[], numero = 0, totalPaginas = 1) => ({
    contenido, pagina: numero, tamanio: 100, totalElementos: contenido.length, totalPaginas,
  });

  const lista = [
    resena(1, { comentario: 'Lenguaje ofensivo' }),
    resena(2, { estado: 'OCULTA', nombreUsuario: 'Pedro' }),
    resena(3, { tituloAlojamiento: 'Loft centro' }),
  ];

  function crear(contenido: Resena[] = lista) {
    TestBed.configureTestingModule({
      imports: [ResenasAdmin],
      providers: proveedores(),
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ResenasAdmin);
    fixture.detectChanges();
    http.expectOne((r) => r.url === url && r.params.get('pagina') === '0').flush(pagina(contenido));
    fixture.detectChanges();
    return fixture;
  }

  type Fixture = ReturnType<typeof crear>;
  const el = (f: Fixture) => f.nativeElement as HTMLElement;
  const fila = (f: Fixture, id: number) => el(f).querySelector(`[data-resena][data-id="${id}"]`) as HTMLElement;
  const estado = (f: Fixture, id: number) => fila(f, id).querySelector('[data-estado]')?.textContent?.trim();
  const confirmar = (f: Fixture) => el(f).querySelector('[data-confirmar-ocultar]') as HTMLButtonElement;
  const escribirMotivo = (f: Fixture, texto: string) => {
    const area = el(f).querySelector('textarea') as HTMLTextAreaElement;
    area.value = texto;
    area.dispatchEvent(new Event('input'));
    f.detectChanges();
  };
  const abrirOcultar = (f: Fixture, id: number) => {
    (fila(f, id).querySelector('[data-ocultar]') as HTMLButtonElement).click();
    f.detectChanges();
  };

  beforeEach(() => (rolActual = 'Administrador'));
  afterEach(() => http.verify());

  it('CA-066 lista todas las reseñas con su estado y la acción que corresponde', () => {
    const f = crear();

    expect(el(f).querySelectorAll('[data-resena]').length).toBe(3);
    expect(estado(f, 1)).toBe('Visible');
    expect(estado(f, 2)).toBe('Oculta');
    expect(fila(f, 1).textContent).toContain('#101');
    expect(fila(f, 1).querySelector('[data-ocultar]')).toBeTruthy();
    expect(fila(f, 2).querySelector('[data-mostrar]')).toBeTruthy();
    expect(fila(f, 2).querySelector('[data-ocultar]')).toBeNull();
  });

  it('CA-066 oculta la reseña con el motivo y la marca como oculta cuando el backend lo confirma', () => {
    const f = crear();
    abrirOcultar(f, 1);
    expect(el(f).querySelector('[role="dialog"]')).toBeTruthy();
    expect(confirmar(f).disabled).toBe(true);

    escribirMotivo(f, '  Lenguaje ofensivo hacia el anfitrión  ');
    confirmar(f).click();
    confirmar(f).click(); // doble clic: una sola petición
    f.detectChanges();
    expect(confirmar(f).textContent?.trim()).toBe('Procesando...');
    expect(estado(f, 1)).toBe('Visible'); // no cambia hasta que el backend confirma

    const req = http.expectOne(`${url}/1/ocultar`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ motivo: 'Lenguaje ofensivo hacia el anfitrión' });
    req.flush(resena(1, { estado: 'OCULTA', comentario: 'Lenguaje ofensivo' }));
    f.detectChanges();

    expect(el(f).querySelector('[role="dialog"]')).toBeNull();
    expect(estado(f, 1)).toBe('Oculta');
    expect(fila(f, 1).querySelector('[data-mostrar]')).toBeTruthy();
    expect(fila(f, 1).textContent).toContain('Lenguaje ofensivo'); // el contenido no se modifica
  });

  it('CA-069 si el usuario no es administrador no puede entrar ni ocultar la reseña', () => {
    const ruta = routes.find((r) => r.path === 'admin/resenas');
    expect(ruta?.data?.['roles']).toEqual(['Administrador']);
    expect(ruta?.canActivate).toEqual([roleGuard]);

    const f = crear();
    expect(entrar()).toBe(true);
    rolActual = 'Huésped';
    expect(entrar()).toBeInstanceOf(UrlTree);
    rolActual = 'Anfitrión';
    expect(entrar()).toBeInstanceOf(UrlTree);
    rolActual = null;
    expect((entrar() as UrlTree).toString()).toBe('/login');

    // Si la sesión pierde el rol, el backend responde 403 y la reseña no cambia.
    abrirOcultar(f, 1);
    escribirMotivo(f, 'Spam');
    confirmar(f).click();
    http.expectOne(`${url}/1/ocultar`).flush(
      { message: 'No tienes permisos para esta operación.' },
      { status: 403, statusText: 'Forbidden' },
    );
    f.detectChanges();

    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No tienes permisos');
    expect(estado(f, 1)).toBe('Visible');
    expect(confirmar(f).disabled).toBe(false); // puede cerrar o reintentar
  });

  it('exige el motivo y limita su largo al del backend', () => {
    const f = crear();
    abrirOcultar(f, 1);
    const area = el(f).querySelector('textarea') as HTMLTextAreaElement;

    expect(area.getAttribute('maxlength')).toBe(String(MAX_MOTIVO_RESENA));
    expect(el(f).textContent).toContain('El motivo es obligatorio.');

    escribirMotivo(f, '   ');
    expect(confirmar(f).disabled).toBe(true);

    escribirMotivo(f, 'Spam');
    expect(el(f).textContent).toContain(`4 / ${MAX_MOTIVO_RESENA}`);
    expect(el(f).textContent).not.toContain('El motivo es obligatorio.');
    expect(confirmar(f).disabled).toBe(false);
  });

  it('muestra el mensaje del backend si no se puede ocultar y permite cerrar con Cancelar o Escape', () => {
    const f = crear();
    abrirOcultar(f, 3);
    escribirMotivo(f, 'Spam');
    confirmar(f).click();
    http.expectOne(`${url}/3/ocultar`).flush({ message: 'La reseña no existe.' }, { status: 404, statusText: 'Not Found' });
    f.detectChanges();
    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('La reseña no existe.');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    f.detectChanges();
    expect(el(f).querySelector('[role="dialog"]')).toBeNull();

    abrirOcultar(f, 3);
    (Array.from(el(f).querySelectorAll('[role="dialog"] button')) as HTMLButtonElement[])
      .find((b) => b.textContent?.trim() === 'Cancelar')!.click();
    f.detectChanges();
    expect(el(f).querySelector('[role="dialog"]')).toBeNull();
  });

  it('"Mostrar reseña" la vuelve a publicar y muestra el error si falla', () => {
    const f = crear();
    (fila(f, 2).querySelector('[data-mostrar]') as HTMLButtonElement).click();
    f.detectChanges();
    expect((fila(f, 2).querySelector('[data-mostrar]') as HTMLButtonElement).textContent?.trim()).toBe('Mostrando...');

    const req = http.expectOne(`${url}/2/mostrar`);
    expect(req.request.method).toBe('PATCH');
    req.flush(resena(2, { estado: 'PUBLICADA', nombreUsuario: 'Pedro' }));
    f.detectChanges();
    expect(estado(f, 2)).toBe('Visible');

    abrirOcultar(f, 2);
    escribirMotivo(f, 'Spam');
    confirmar(f).click();
    http.expectOne(`${url}/2/ocultar`).flush(resena(2, { estado: 'OCULTA' }));
    f.detectChanges();

    (fila(f, 2).querySelector('[data-mostrar]') as HTMLButtonElement).click();
    http.expectOne(`${url}/2/mostrar`).flush(null, { status: 0, statusText: 'Error' });
    f.detectChanges();
    expect(el(f).querySelector('[role="alert"]')?.textContent).toContain('No hay conexión con el servidor.');
    expect(estado(f, 2)).toBe('Oculta');
  });

  it('busca y filtra por estado, y pagina de 10 en 10', () => {
    const muchas = Array.from({ length: 12 }, (_, i) => resena(i + 1, { estado: i < 2 ? 'OCULTA' : 'PUBLICADA' }));
    const f = crear([...muchas, resena(50, { nombreUsuario: 'Zoe', comentario: 'Excelente' })]);
    expect(el(f).querySelectorAll('[data-resena]').length).toBe(10);
    expect(el(f).textContent).toContain('Mostrando 10 de 13 reseñas');

    const select = el(f).querySelector('select') as HTMLSelectElement;
    select.value = 'Oculta';
    select.dispatchEvent(new Event('change'));
    f.detectChanges();
    expect(el(f).querySelectorAll('[data-resena]').length).toBe(2);

    select.value = 'Todas';
    select.dispatchEvent(new Event('change'));
    const buscador = el(f).querySelector('input') as HTMLInputElement;
    buscador.value = 'zoe';
    buscador.dispatchEvent(new Event('input'));
    f.detectChanges();
    expect(Array.from(el(f).querySelectorAll('[data-resena]')).map((r) => r.getAttribute('data-id'))).toEqual(['50']);
  });

  it('carga todas las páginas, indica cuando no hay reseñas y permite reintentar si falla', () => {
    TestBed.configureTestingModule({
      imports: [ResenasAdmin],
      providers: proveedores(),
    });
    http = TestBed.inject(HttpTestingController);
    const f = TestBed.createComponent(ResenasAdmin);
    f.detectChanges();
    expect(el(f).textContent).toContain('Cargando reseñas...');

    http.expectOne((r) => r.url === url && r.params.get('pagina') === '0').flush(null, { status: 500, statusText: 'Error' });
    f.detectChanges();
    expect(el(f).textContent).toContain('No se pudieron cargar las reseñas.');

    (Array.from(el(f).querySelectorAll('button')) as HTMLButtonElement[]).find((b) => b.textContent?.includes('Reintentar'))!.click();
    http.expectOne((r) => r.url === url && r.params.get('pagina') === '0').flush(pagina([resena(1)], 0, 2));
    http.expectOne((r) => r.url === url && r.params.get('pagina') === '1').flush(pagina([resena(2)], 1, 2));
    f.detectChanges();
    expect(el(f).querySelectorAll('[data-resena]').length).toBe(2);

    f.componentInstance.reviews.set([]);
    f.detectChanges();
    expect(el(f).textContent).toContain('No hay reseñas que coincidan.');
  });
});

