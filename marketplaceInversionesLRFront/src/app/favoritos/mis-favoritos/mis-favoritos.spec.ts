import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';

import { MisFavoritos } from './mis-favoritos';
import { API_URL } from '../../core/services/api-url';
import { RolUsuario, SessionService } from '../../core/services/session.service';
import { AlojamientoRespuesta } from '../../alojamientos/models/alojamiento';
import { Favorito, PaginaFavoritos } from '../models/favorito';

/**
 * HU-29 Gestionar alojamientos favoritos (SCRUM-280): pantalla Mis favoritos.
 */
describe('MisFavoritos (HU-29)', () => {
  let http: HttpTestingController;
  const rol = signal<RolUsuario | null>('Huésped');

  const alojamiento = (id: number, titulo: string): AlojamientoRespuesta => ({
    idAlojamiento: id,
    titulo,
    ciudad: 'Bogotá',
    tipo: 'Casa',
    capacidad: 4,
    precioNoche: 250000,
    amenidades: ['Wifi', 'Cocina', 'Piscina'],
    imagenes: [`https://ejemplo.com/${id}.jpg`],
    promedioCalificacion: 4.5,
    estado: 'ACTIVA',
    idAnfitrion: 2,
  });

  const favorito = (a: AlojamientoRespuesta): Favorito => ({
    idFavorito: a.idAlojamiento * 10,
    fechaAgregado: '2026-11-10T10:00:00',
    idUsuario: 1,
    idAlojamiento: a.idAlojamiento,
    alojamiento: a,
  });

  const pagina = (contenido: Favorito[], numero = 0, totalPaginas = 1): PaginaFavoritos => ({
    contenido,
    pagina: numero,
    tamanio: 20,
    totalElementos: contenido.length,
    totalPaginas,
  });

  /** Pedido de la pantalla (tamaño 20); el de los corazones usa tamaño 100. */
  const pedidoPantalla = (numero = 0) =>
    http.expectOne(
      (r) => r.url === 'http://api/favoritos' && r.params.get('tamanio') === '20' && r.params.get('pagina') === String(numero),
    );

  function crear() {
    TestBed.configureTestingModule({
      imports: [MisFavoritos],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        {
          provide: SessionService,
          useValue: {
            correo: signal('ana@correo.com'),
            tieneRol: (...roles: RolUsuario[]) => rol() !== null && roles.includes(rol()!),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(MisFavoritos);
    fixture.detectChanges();
    // Sincronización de los corazones del servicio: aquí no aporta nada.
    http.match((r) => r.params.get('tamanio') === '100').forEach((r) => r.flush(pagina([])));
    return fixture;
  }

  const texto = (fixture: { nativeElement: HTMLElement }) => fixture.nativeElement.textContent ?? '';
  const tarjetas = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement.querySelectorAll('[data-testid="favorito"]');

  beforeEach(() => rol.set('Huésped'));

  afterEach(() => http.verify());

  it('CA-170 muestra los alojamientos favoritos del usuario con su tarjeta y enlace al detalle', () => {
    const fixture = crear();
    expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).toBeTruthy(); // cargando

    pedidoPantalla().flush(pagina([favorito(alojamiento(1, 'Casa en el lago')), favorito(alojamiento(2, 'Cabaña del bosque'))]));
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(2);
    expect(texto(fixture)).toContain('Casa en el lago');
    expect(texto(fixture)).toContain('Hasta 4 huéspedes · Wifi · Cocina');
    const enlace = fixture.nativeElement.querySelector('[data-testid="favorito"] a') as HTMLAnchorElement;
    expect(enlace.getAttribute('href')).toBe('/alojamientos/1');
  });

  it('CA-170 indica que la lista está vacía si el usuario no tiene favoritos', () => {
    const fixture = crear();
    pedidoPantalla().flush(pagina([]));
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(0);
    expect(texto(fixture)).toContain('Todavía no tienes alojamientos favoritos');
  });

  it('CA-170 muestra un error con reintento si no se pueden cargar', () => {
    const fixture = crear();
    pedidoPantalla().flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(texto(fixture)).toContain('No pudimos cargar tus favoritos');

    (fixture.nativeElement.querySelector('[role="alert"] button') as HTMLButtonElement).click();
    pedidoPantalla().flush(pagina([favorito(alojamiento(1, 'Casa en el lago'))]));
    fixture.detectChanges();
    expect(tarjetas(fixture).length).toBe(1);
  });

  it('CA-170 trae las páginas siguientes con "Ver más favoritos"', () => {
    const fixture = crear();
    pedidoPantalla().flush(pagina([favorito(alojamiento(1, 'Casa en el lago'))], 0, 2));
    fixture.detectChanges();

    const verMas = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((b) => b.textContent?.includes('Ver más favoritos'))!;
    verMas.click();
    pedidoPantalla(1).flush(pagina([favorito(alojamiento(2, 'Cabaña del bosque'))], 1, 2));
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(2);
    expect(texto(fixture)).not.toContain('Ver más favoritos');
  });

  it('CA-170 sin sesión de huésped no consulta y pide iniciar sesión', () => {
    rol.set(null);
    const fixture = crear();
    fixture.detectChanges();
    http.expectNone('http://api/favoritos');
    expect(texto(fixture)).toContain('Inicia sesión para ver tus favoritos');
  });

  it('CA-171 al quitar un favorito deja de aparecer en la lista cuando el backend lo confirma', () => {
    const fixture = crear();
    pedidoPantalla().flush(pagina([favorito(alojamiento(1, 'Casa en el lago')), favorito(alojamiento(2, 'Cabaña del bosque'))]));
    fixture.detectChanges();

    const quitar = fixture.nativeElement.querySelector('[aria-label="Quitar Casa en el lago de favoritos"]') as HTMLButtonElement;
    quitar.click();
    fixture.detectChanges();
    expect(quitar.disabled).toBe(true); // en curso
    expect(tarjetas(fixture).length).toBe(2);

    const req = http.expectOne('http://api/favoritos/1');
    expect(req.request.method).toBe('DELETE');
    req.flush({ message: 'Alojamiento quitado de favoritos.' });
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(1);
    expect(texto(fixture)).not.toContain('Casa en el lago');
  });

  it('CA-171 si el backend no puede quitarlo, lo conserva y muestra el error', () => {
    const fixture = crear();
    pedidoPantalla().flush(pagina([favorito(alojamiento(1, 'Casa en el lago'))]));
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[aria-label="Quitar Casa en el lago de favoritos"]') as HTMLButtonElement).click();
    http.expectOne('http://api/favoritos/1').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(tarjetas(fixture).length).toBe(1);
    expect(texto(fixture)).toContain('No se pudo quitar de favoritos');
  });
});
