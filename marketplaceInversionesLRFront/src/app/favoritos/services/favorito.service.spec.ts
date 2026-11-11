import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { vi } from 'vitest';

import { FavoritoService } from './favorito.service';
import { API_URL } from '../../core/services/api-url';
import { RolUsuario, SessionService } from '../../core/services/session.service';
import { Favorito, PaginaFavoritos } from '../models/favorito';

/**
 * HU-29 Gestionar alojamientos favoritos (SCRUM-280): servicio de favoritos.
 */
describe('FavoritoService (HU-29)', () => {
  let servicio: FavoritoService;
  let http: HttpTestingController;
  const rol = signal<RolUsuario | null>('Huésped');
  const correo = signal('ana@correo.com');

  const favorito = (idAlojamiento: number): Favorito => ({
    idFavorito: idAlojamiento * 10,
    fechaAgregado: '2026-11-10T10:00:00',
    idUsuario: 1,
    idAlojamiento,
  });

  const pagina = (contenido: Favorito[], numero = 0, totalPaginas = 1): PaginaFavoritos => ({
    contenido,
    pagina: numero,
    tamanio: 100,
    totalElementos: contenido.length,
    totalPaginas,
  });

  beforeEach(() => {
    rol.set('Huésped');
    correo.set('ana@correo.com');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api/' },
        {
          provide: SessionService,
          useValue: {
            correo,
            tieneRol: (...roles: RolUsuario[]) => rol() !== null && roles.includes(rol()!),
          },
        },
      ],
    });
    servicio = TestBed.inject(FavoritoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('CA-170 consulta GET /favoritos paginado para el usuario de la sesión', () => {
    let respuesta: PaginaFavoritos | undefined;
    servicio.listar(1, 5).subscribe((p) => (respuesta = p));

    const req = http.expectOne((r) => r.url === 'http://api/favoritos');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('pagina')).toBe('1');
    expect(req.request.params.get('tamanio')).toBe('5');
    req.flush(pagina([favorito(3)], 1, 2));

    expect(respuesta?.contenido[0].idAlojamiento).toBe(3);
  });

  it('CA-170 lee todas las páginas para saber qué alojamientos son favoritos y no mezcla usuarios', () => {
    servicio.cargarSiCorresponde();
    http.expectOne((r) => r.params.get('pagina') === '0').flush(pagina([favorito(1)], 0, 2));
    http.expectOne((r) => r.params.get('pagina') === '1').flush(pagina([favorito(2)], 1, 2));

    expect(servicio.esFavorito(1)).toBe(true);
    expect(servicio.esFavorito(2)).toBe(true);

    // Mismo usuario: no se vuelve a pedir.
    servicio.cargarSiCorresponde();
    http.expectNone('http://api/favoritos');

    // Otro usuario: se descartan los favoritos del anterior.
    correo.set('luis@correo.com');
    servicio.cargarSiCorresponde();
    expect(servicio.esFavorito(1)).toBe(false);
    http.expectOne((r) => r.url === 'http://api/favoritos').flush(pagina([]));
  });

  it('CA-170 sin sesión de huésped no consulta favoritos', () => {
    rol.set('Anfitrión');
    servicio.cargarSiCorresponde();
    http.expectNone('http://api/favoritos');
  });

  it('CA-173 agrega con POST /favoritos/{id} y lo marca como favorito al confirmarlo el backend', () => {
    const fin = vi.fn();
    servicio.toggle(7, fin);

    expect(servicio.enCurso(7)).toBe(true);
    expect(servicio.esFavorito(7)).toBe(false);

    const req = http.expectOne('http://api/favoritos/7');
    expect(req.request.method).toBe('POST');
    req.flush(favorito(7), { status: 201, statusText: 'Created' });

    expect(servicio.esFavorito(7)).toBe(true);
    expect(servicio.enCurso(7)).toBe(false);
    expect(fin).toHaveBeenCalledWith(true);
  });

  it('CA-173 si ya estaba (200) conserva un único favorito y no envía peticiones duplicadas', () => {
    servicio.toggle(7);
    servicio.toggle(7); // doble clic mientras la primera está en curso
    http.expectOne('http://api/favoritos/7').flush(favorito(7), { status: 200, statusText: 'OK' });

    servicio.registrarLista([favorito(7), favorito(7)]);
    expect(servicio.esFavorito(7)).toBe(true);
  });

  it('CA-173 informa el error y no lo marca si el alojamiento no existe (404) o el servidor falla', () => {
    servicio.toggle(9);
    http.expectOne('http://api/favoritos/9').flush({ message: 'El alojamiento no existe.' }, { status: 404, statusText: 'Not Found' });
    expect(servicio.esFavorito(9)).toBe(false);
    expect(servicio.mensaje()).toBe('Este alojamiento ya no está disponible.');

    servicio.toggle(9);
    expect(servicio.mensaje()).toBeNull();
    http.expectOne('http://api/favoritos/9').flush(null, { status: 500, statusText: 'Error' });
    expect(servicio.mensaje()).toBe('No se pudo guardar en favoritos. Inténtalo de nuevo.');
  });

  it('CA-173 sin sesión de huésped no llama al backend y pide iniciar sesión', () => {
    rol.set(null);
    servicio.toggle(7);
    http.expectNone('http://api/favoritos/7');
    expect(servicio.mensaje()).toContain('Inicia sesión');
  });

  it('CA-171 elimina con DELETE /favoritos/{id} y deja de ser favorito', () => {
    servicio.registrarLista([favorito(4)]);
    servicio.toggle(4);

    const req = http.expectOne('http://api/favoritos/4');
    expect(req.request.method).toBe('DELETE');
    expect(servicio.esFavorito(4)).toBe(true); // hasta que el backend confirme
    req.flush({ message: 'Alojamiento quitado de favoritos.' });

    expect(servicio.esFavorito(4)).toBe(false);
  });

  it('CA-171 si ya no estaba en favoritos (404) también lo quita; si falla otra cosa lo conserva', () => {
    servicio.registrarLista([favorito(4), favorito(5)]);

    servicio.toggle(4);
    http.expectOne('http://api/favoritos/4').flush({ message: 'El alojamiento no está en tus favoritos.' }, { status: 404, statusText: 'Not Found' });
    expect(servicio.esFavorito(4)).toBe(false);
    expect(servicio.mensaje()).toBeNull();

    servicio.toggle(5);
    http.expectOne('http://api/favoritos/5').flush(null, { status: 500, statusText: 'Error' });
    expect(servicio.esFavorito(5)).toBe(true);
    expect(servicio.mensaje()).toBe('No se pudo quitar de favoritos. Inténtalo de nuevo.');
  });
});
