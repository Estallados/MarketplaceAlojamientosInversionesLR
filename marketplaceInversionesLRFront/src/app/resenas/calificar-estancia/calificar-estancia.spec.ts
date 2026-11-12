import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { CalificarEstancia } from './calificar-estancia';
import { DetalleReserva } from '../../reservas/detalle-reserva/detalle-reserva';
import { API_URL } from '../../core/services/api-url';
import { Resena } from '../models/resena';
import { enDias, reservaDePrueba } from '../../reservas/testing/reservas-de-prueba';

/**
 * HU-03 Calificar, reseñar y consultar reseñas (SCRUM-256): calificar una estancia.
 */
describe('CalificarEstancia (HU-03)', () => {
  let http: HttpTestingController;

  function crearModal() {
    TestBed.configureTestingModule({
      imports: [CalificarEstancia],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CalificarEstancia);
    fixture.componentRef.setInput('idReserva', 15);
    fixture.componentRef.setInput('tituloAlojamiento', 'Casa 15');
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function crearDetalle() {
    TestBed.configureTestingModule({
      imports: [DetalleReserva],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: 'http://api' },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '15' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(DetalleReserva);
    fixture.detectChanges();
    return fixture;
  }

  const boton = (f: { nativeElement: HTMLElement }) =>
    f.nativeElement.querySelector('[data-calificar]') as HTMLButtonElement | null;

  afterEach(() => http.verify());

  it('CA-016: solo una reserva completada ofrece "Calificar estancia"', () => {
    const futura = crearDetalle();
    http.expectOne('http://api/reservas/15').flush(reservaDePrueba(15));
    futura.detectChanges();
    expect(boton(futura)).toBeNull();

    TestBed.resetTestingModule();
    const completada = crearDetalle();
    http.expectOne('http://api/reservas/15').flush(
      reservaDePrueba(15, { fechaEntrada: enDias(-5), fechaSalida: enDias(-2), completada: true }),
    );
    completada.detectChanges();
    expect(boton(completada)?.textContent?.trim()).toBe('Calificar estancia');

    boton(completada)!.click();
    completada.detectChanges();
    expect(completada.nativeElement.querySelector('app-calificar-estancia')).not.toBeNull();
  });

  it('CA-016: una reserva ya reseñada ofrece "Ver reseña" y no abre el formulario', () => {
    const fixture = crearDetalle();
    http.expectOne('http://api/reservas/15').flush(reservaDePrueba(15, { completada: true, tieneResena: true }));
    fixture.detectChanges();

    expect(boton(fixture)?.textContent?.trim()).toBe('Ver reseña');
    fixture.componentInstance.abrirResena();
    expect(fixture.componentInstance.mostrarResena()).toBe(false);
  });

  it('CA-016: exige una calificación de 1 a 5 y muestra el rechazo del backend', () => {
    const c = crearModal();

    c.enviar();
    expect(c.error()).toBe('Elige una calificación de 1 a 5 estrellas.');
    http.expectNone('http://api/resenas');

    c.elegirCalificacion(4);
    c.enviar();
    http
      .expectOne('http://api/resenas')
      .flush({ mensaje: 'Solo puedes reseñar una estancia completada.' }, { status: 409, statusText: 'Conflict' });
    expect(c.error()).toBe('Solo puedes reseñar una estancia completada.');
  });

  it('CA-018: publica la reseña asociada a la reserva con calificación y comentario', () => {
    const c = crearModal();
    let publicada: Resena | undefined;
    c.publicada.subscribe((r) => (publicada = r));

    c.elegirCalificacion(5);
    c.comentario.set('  Muy limpio y bien ubicado  ');
    c.enviar();

    const req = http.expectOne('http://api/resenas');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ idReserva: 15, calificacion: 5, comentario: 'Muy limpio y bien ubicado' });
    req.flush({ idResena: 9, idReserva: 15, idAlojamiento: 7, calificacion: 5, fecha: '2026-11-11T10:00:00' });

    expect(publicada?.idResena).toBe(9);
    expect(c.enviando()).toBe(false);
  });
});
