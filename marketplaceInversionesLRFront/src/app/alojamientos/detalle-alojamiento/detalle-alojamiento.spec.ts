import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { DetalleAlojamientoPage } from './detalle-alojamiento';
import { DetalleAlojamientoService } from '../services/detalle-alojamiento.service';
import { DetalleAlojamiento } from '../models/detalle-alojamiento';
import { API_URL } from '../../core/services/api-url';
import { RolUsuario, SessionService } from '../../core/services/session.service';

/**
 * HU-18 Consultar información detallada de un alojamiento, con mapa (SCRUM-234).
 */
describe('DetalleAlojamientoPage (HU-18)', () => {
  let servicio: { obtener: ReturnType<typeof vi.fn>; resenas: ReturnType<typeof vi.fn> };

  const casa: DetalleAlojamiento = {
    idAlojamiento: 8,
    titulo: 'Casa en el campo',
    ciudad: 'Bogotá',
    latitud: 4.711,
    longitud: -74.0721,
    tipo: 'Cabaña',
    capacidad: 3,
    precioNoche: 123000,
    horaEntrada: '15:00:00',
    horaSalida: '11:00:00',
    nombrePolitica: 'Flexible',
    reglas: 'No se permite fumar',
    amenidades: ['Wifi', 'Cocina'],
    serviciosAdicionales: [{ idServicio: 1, nombre: 'Desayuno', costo: 20000 }],
    imagenes: ['https://ejemplo.com/1.jpg'],
    promedioCalificacion: 4.5,
    cantidadResenas: 2,
  };

  function crear(id = '8') {
    TestBed.configureTestingModule({
      imports: [DetalleAlojamientoPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DetalleAlojamientoService, useValue: servicio },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id }) } } },
      ],
    });
    const componente = TestBed.createComponent(DetalleAlojamientoPage).componentInstance;
    componente.ngOnInit();
    return componente;
  }

  beforeEach(() => {
    servicio = {
      obtener: vi.fn().mockReturnValue(of(casa)),
      resenas: vi.fn().mockReturnValue(
        of({
          resenas: { contenido: [{ idResena: 1, calificacion: 5, comentario: 'Excelente', fecha: '2026-10-01', nombreUsuario: 'Ana Pérez' }] },
          promedio: 5,
          cantidad: 1,
        }),
      ),
    };
  });

  it('CA-093: muestra galería, servicios, reglas y reseñas del alojamiento', () => {
    const c = crear();

    expect(servicio.obtener).toHaveBeenCalledWith(8);
    expect(c.stay()?.titulo).toBe('Casa en el campo');
    expect(c.fotoPrincipal()).toBe('https://ejemplo.com/1.jpg');
    expect(c.fotosSecundarias()).toEqual([]); // solo tiene una foto: no se rellena con otras
    expect(c.amenities()).toEqual(['Wifi', 'Cocina']);
    expect(c.servicios()[0].nombre).toBe('Desayuno');
    expect(c.reglas().map((r) => r.text)).toContain('A partir de las 15:00');
    expect(c.reglas().map((r) => r.text)).toContain('Flexible');
    expect(c.reviews().length).toBe(1);
    expect(c.promedioResenas()).toBe(5);
  });

  it('CA-093: la galería muestra solo las fotos del alojamiento, sin fotos de relleno', () => {
    servicio.obtener.mockReturnValue(
      of({ ...casa, imagenes: ['https://ejemplo.com/1.jpg', 'https://ejemplo.com/2.jpg', '', 'https://ejemplo.com/3.jpg'] }),
    );
    TestBed.configureTestingModule({
      imports: [DetalleAlojamientoPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DetalleAlojamientoService, useValue: servicio },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '8' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(DetalleAlojamientoPage);
    fixture.detectChanges();

    const fuentes = Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid="galeria"] img') as NodeListOf<HTMLImageElement>,
    ).map((img) => img.getAttribute('src'));
    expect(fuentes).toEqual(['https://ejemplo.com/1.jpg', 'https://ejemplo.com/2.jpg', 'https://ejemplo.com/3.jpg']);
    expect(fixture.nativeElement.innerHTML).not.toContain('unsplash');
  });

  it('CA-093: sin fotos no inventa imágenes y lo indica', () => {
    servicio.obtener.mockReturnValue(of({ ...casa, imagenes: [] }));
    TestBed.configureTestingModule({
      imports: [DetalleAlojamientoPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DetalleAlojamientoService, useValue: servicio },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '8' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(DetalleAlojamientoPage);
    fixture.detectChanges();

    expect(fixture.componentInstance.fotoPrincipal()).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="galeria"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="galeria-vacia"]')?.textContent).toContain(
      'todavía no tiene fotografías',
    );
  });

  it('CA-164: arma el mapa con la ubicación aproximada (no la exacta)', () => {
    const c = crear();
    const punto = c.puntoAproximado();

    expect(punto).not.toBeNull();
    expect(punto![0]).not.toBe(4.711);
    expect(Math.abs(punto![0] - 4.711)).toBeLessThan(0.01);
    expect(c.mapaUrl()).not.toBeNull();
    expect(c.mapaEnlace()).toContain('openstreetmap.org');
  });

  it('CA-164: sin coordenadas usa el mapa ilustrativo y busca por ciudad', () => {
    servicio.obtener.mockReturnValue(of({ ...casa, latitud: null, longitud: null }));
    const c = crear();

    expect(c.mapaUrl()).toBeNull();
    expect(c.mapaEnlace()).toContain('search?query=Bogot');
  });

  it('muestra error si el alojamiento no existe y no rompe si fallan las reseñas', () => {
    servicio.resenas.mockReturnValue(throwError(() => new Error('404')));
    const ok = crear();
    expect(ok.reviews()).toEqual([]);
    expect(ok.cantidadResenas()).toBe(2);

    TestBed.resetTestingModule();
    servicio.obtener.mockReturnValue(throwError(() => new Error('404')));
    const malo = crear('8');
    expect(malo.errorCarga()).toBe(true);
    expect(malo.loading()).toBe(false);
  });
});

/**
 * HU-29 Gestionar alojamientos favoritos (SCRUM-280): botón Guardar del detalle.
 */
describe('DetalleAlojamientoPage: favoritos (HU-29)', () => {
  let http: HttpTestingController;
  const rol = signal<RolUsuario | null>('Huésped');

  const casa: DetalleAlojamiento = { idAlojamiento: 8, titulo: 'Casa en el campo', ciudad: 'Bogotá', imagenes: [] };

  function crear(favoritos: number[] = []) {
    TestBed.configureTestingModule({
      imports: [DetalleAlojamientoPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        {
          provide: DetalleAlojamientoService,
          useValue: { obtener: () => of(casa), resenas: () => of({ resenas: { contenido: [] } }) },
        },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '8' }) } } },
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
    const fixture = TestBed.createComponent(DetalleAlojamientoPage);
    fixture.detectChanges();
    if (rol() === 'Huésped') {
      http
        .expectOne((r) => r.url === 'http://api/favoritos')
        .flush({
          contenido: favoritos.map((id) => ({ idFavorito: id, fechaAgregado: '', idUsuario: 1, idAlojamiento: id })),
          pagina: 0,
          tamanio: 100,
          totalElementos: favoritos.length,
          totalPaginas: 1,
        });
    }
    fixture.detectChanges();
    return fixture;
  }

  const boton = (fixture: { nativeElement: HTMLElement }) =>
    fixture.nativeElement.querySelector('button[aria-pressed]') as HTMLButtonElement | null;

  beforeEach(() => rol.set('Huésped'));

  afterEach(() => http.verify());

  it('CA-173 el huésped guarda el alojamiento con POST /favoritos/{id} y el botón pasa a "Guardado"', () => {
    const fixture = crear();
    expect(boton(fixture)?.textContent).toContain('Guardar');
    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('false');

    boton(fixture)!.click();
    fixture.detectChanges();
    expect(boton(fixture)!.disabled).toBe(true);

    const req = http.expectOne('http://api/favoritos/8');
    expect(req.request.method).toBe('POST');
    req.flush({ idFavorito: 1, fechaAgregado: '', idUsuario: 1, idAlojamiento: 8 }, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(boton(fixture)?.textContent).toContain('Guardado');
    expect(boton(fixture)?.getAttribute('aria-pressed')).toBe('true');
  });

  it('CA-173 si ya estaba guardado lo muestra como "Guardado" sin volver a agregarlo', () => {
    const fixture = crear([8]);
    expect(boton(fixture)?.textContent).toContain('Guardado');
    http.expectNone('http://api/favoritos/8');
  });

  it('CA-173 muestra el error del backend y no lo marca como guardado', () => {
    const fixture = crear();
    boton(fixture)!.click();
    http.expectOne('http://api/favoritos/8').flush({ message: 'El alojamiento no existe.' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(boton(fixture)?.textContent).toContain('Guardar');
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Este alojamiento ya no está disponible.');
  });

  it('CA-171 desde el detalle el huésped quita el favorito con DELETE /favoritos/{id}', () => {
    const fixture = crear([8]);
    boton(fixture)!.click();
    const req = http.expectOne('http://api/favoritos/8');
    expect(req.request.method).toBe('DELETE');
    req.flush({ message: 'Alojamiento quitado de favoritos.' });
    fixture.detectChanges();

    expect(boton(fixture)?.textContent).toContain('Guardar');
  });

  it('CA-173 solo el huésped ve el botón Guardar', () => {
    rol.set('Anfitrión');
    const fixture = crear();
    expect(boton(fixture)).toBeNull();
  });
});
