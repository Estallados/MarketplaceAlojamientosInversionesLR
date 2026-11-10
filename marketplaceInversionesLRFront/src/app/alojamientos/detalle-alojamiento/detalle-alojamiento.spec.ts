import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { DetalleAlojamientoPage } from './detalle-alojamiento';
import { DetalleAlojamientoService } from '../services/detalle-alojamiento.service';
import { DetalleAlojamiento } from '../models/detalle-alojamiento';

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
    expect(c.fotosSecundarias().length).toBe(4);
    expect(c.amenities()).toEqual(['Wifi', 'Cocina']);
    expect(c.servicios()[0].nombre).toBe('Desayuno');
    expect(c.reglas().map((r) => r.text)).toContain('A partir de las 15:00');
    expect(c.reglas().map((r) => r.text)).toContain('Flexible');
    expect(c.reviews().length).toBe(1);
    expect(c.promedioResenas()).toBe(5);
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
