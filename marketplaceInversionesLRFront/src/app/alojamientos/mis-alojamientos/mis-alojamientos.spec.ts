import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { MisAlojamientos } from './mis-alojamientos';
import { API_URL } from '../../core/services/api-url';
import { AlojamientoRespuesta, EstadoAlojamiento, PaginaAlojamientos } from '../models/alojamiento';
import { routes } from '../../app.routes';

/**
 * HU-19 Consultar mis publicaciones y sus estados (SCRUM-274): pantalla Mis alojamientos.
 */
describe('MisAlojamientos (HU-19)', () => {
  const url = 'http://api/alojamientos/mios';
  let http: HttpTestingController;

  const alojamiento = (id: number, estado: EstadoAlojamiento, titulo = `Casa ${id}`): AlojamientoRespuesta => ({
    idAlojamiento: id,
    titulo,
    estado,
    idAnfitrion: 5,
    ciudad: 'Bogotá',
    tipo: 'Casa',
    precioNoche: 250000,
    capacidad: 4,
    habitaciones: 2,
    banos: 1,
    imagenes: [`https://ejemplo.com/${id}.jpg`],
    promedioCalificacion: 4.5,
    cantidadResenas: 3,
    cantidadReservas: 2,
    fechaCreacion: '2026-11-01T10:00:00',
  });

  const pagina = (contenido: AlojamientoRespuesta[], numero = 0, totalPaginas = 1): PaginaAlojamientos => ({
    contenido,
    pagina: numero,
    tamanio: 100,
    totalElementos: contenido.length,
    totalPaginas,
  });

  const todos: AlojamientoRespuesta[] = [
    alojamiento(1, 'ACTIVA', 'Casa en el lago'),
    alojamiento(2, 'PENDIENTE_REVISION'),
    alojamiento(3, 'BORRADOR'),
    alojamiento(4, 'PAUSADA'),
    alojamiento(5, 'BLOQUEADA', 'Cabaña bloqueada'),
  ];

  function crear() {
    TestBed.configureTestingModule({
      imports: [MisAlojamientos],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api/' }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(MisAlojamientos);
    fixture.detectChanges();
    return fixture;
  }

  const pedido = (numero = 0) =>
    http.expectOne((r) => r.method === 'GET' && r.url === url && r.params.get('pagina') === String(numero));

  const texto = (fixture: { nativeElement: HTMLElement }) => (fixture.nativeElement.textContent ?? '').replace(/\s+/g, ' ');
  const tarjetas = (fixture: { nativeElement: HTMLElement }) =>
    Array.from(fixture.nativeElement.querySelectorAll('[data-alojamiento]') as NodeListOf<HTMLElement>);

  afterEach(() => http.verify());

  it('CA-103 muestra las publicaciones del anfitrión con el estado de cada una', () => {
    const fixture = crear();
    expect(texto(fixture)).toContain('Cargando tus alojamientos');

    const req = pedido();
    expect(req.request.params.get('tamanio')).toBe('100');
    req.flush(pagina(todos));
    fixture.detectChanges();

    const lista = tarjetas(fixture);
    expect(lista.length).toBe(5);
    expect(lista.map((t) => t.querySelector('[data-estado]')?.getAttribute('data-valor'))).toEqual([
      'ACTIVA',
      'PENDIENTE_REVISION',
      'BORRADOR',
      'PAUSADA',
      'BLOQUEADA',
    ]);
    expect(lista[0].textContent).toContain('Casa en el lago');
    expect(lista[0].querySelector('[data-estado]')?.textContent).toContain('Activa');
    expect(lista[0].textContent).toContain('Publicado y disponible para reservas.');
    expect(lista[1].textContent).toContain('Pendiente de aprobación por administración.');
    expect(lista[0].querySelector('img')?.getAttribute('src')).toBe('https://ejemplo.com/1.jpg');
  });

  it('CA-103 recorre todas las páginas de GET /alojamientos/mios', () => {
    const fixture = crear();
    pedido(0).flush(pagina(todos.slice(0, 3), 0, 2));
    pedido(1).flush(pagina(todos.slice(3), 1, 2));
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(5);
    expect(texto(fixture)).toContain('5 alojamiento(s) encontrados');
  });

  it('CA-103 cuenta las publicaciones por estado y permite filtrarlas', () => {
    const fixture = crear();
    pedido().flush(pagina([...todos, alojamiento(6, 'ACTIVA')]));
    fixture.detectChanges();

    const c = fixture.componentInstance;
    expect([c.total(), c.active(), c.pending(), c.drafts(), c.paused(), c.blocked()]).toEqual([6, 2, 1, 1, 1, 1]);

    const select = fixture.nativeElement.querySelector('select[aria-label="Filtrar por estado"]') as HTMLSelectElement;
    select.value = 'ACTIVA';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(2);
    expect(tarjetas(fixture).every((t) => t.querySelector('[data-estado]')?.getAttribute('data-valor') === 'ACTIVA')).toBe(true);

    select.value = 'BLOQUEADA';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(tarjetas(fixture).map((t) => t.textContent)).toEqual([expect.stringContaining('Cabaña bloqueada')]);
  });

  it('CA-103 sin publicaciones indica que aún no tiene alojamientos y ofrece crear uno', () => {
    const fixture = crear();
    pedido().flush(pagina([]));
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(0);
    expect(texto(fixture)).toContain('Aún no tienes alojamientos registrados');
    expect(texto(fixture)).toContain('Crear mi primer alojamiento');
  });

  it('CA-103 si la consulta falla muestra el motivo, no la lista vacía, y permite actualizar', () => {
    const fixture = crear();
    pedido().flush({ message: 'Acceso denegado' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Solo los anfitriones pueden consultar sus alojamientos.',
    );
    expect(texto(fixture)).not.toContain('Aún no tienes alojamientos');

    const actualizar = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>).find(
      (b) => b.textContent?.includes('Actualizar'),
    )!;
    actualizar.click();
    pedido().flush(pagina(todos.slice(0, 1)));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(tarjetas(fixture).length).toBe(1);
  });

  it('"Editar detalles" y "Disponibilidad" llevan a la edición y al calendario; no se edita una bloqueada', () => {
    const fixture = crear();
    pedido().flush(pagina([todos[0], todos[4]]));
    fixture.detectChanges();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const [activa, bloqueada] = tarjetas(fixture);
    expect(activa.textContent).not.toContain('Ver detalles');
    (activa.querySelector('[data-editar]') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/host/alojamientos', 1, 'editar']);

    (activa.querySelector('[data-disponibilidad]') as HTMLButtonElement).click();
    expect(navegar).toHaveBeenCalledWith(['/host/alojamientos', 1, 'disponibilidad']);

    expect((bloqueada.querySelector('[data-editar]') as HTMLButtonElement).disabled).toBe(true);
  });

  it('la ruta /host/alojamientos es solo para el anfitrión', () => {
    const ruta = routes.find((r) => r.path === 'host/alojamientos');
    expect(ruta?.data?.['roles']).toEqual(['Anfitrión']);
    expect(ruta?.canActivate?.length).toBe(1);
  });
});
