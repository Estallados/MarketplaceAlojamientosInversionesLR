import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { ConsultarDisponibilidad } from './consultar-disponibilidad';
import { FechasEstadia } from '../models/consulta-disponibilidad';
import {
  diaDeFecha,
  diaSiguiente,
  erroresFechas,
  hoyLocal,
  noches
} from '../validaciones/fechas-estadia';
import { API_URL } from '../../core/services/api-url';
import { routes } from '../../app.routes';

// ======================================
// HU-21 CONSULTAR DISPONIBILIDAD PARA FECHAS SELECCIONADAS
// Se prueba la pantalla real con el servicio HTTP real; solo el backend
// se sustituye con HttpTestingController.
// ======================================

describe('ConsultarDisponibilidad (HU-21)', () => {

  const baseUrl = 'http://localhost:8081';

  const urlConsulta = `${baseUrl}/alojamientos/41/disponibilidad/consultar`;

  let http: HttpTestingController;

  let harness: RouterTestingHarness;

  let componente: ConsultarDisponibilidad;

  /** Fecha AAAA-MM-DD a n días de hoy (hora local). */
  function enDias(n: number): string {
    let fecha = hoyLocal();
    for (let i = 0; i < n; i++) {
      fecha = diaSiguiente(fecha);
    }
    return fecha;
  }

  const llegada = enDias(10);

  const salida = enDias(13);

  beforeEach(async () => {

    await TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl }
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);

    harness = await RouterTestingHarness.create();

    componente = await harness.navigateByUrl(
      '/alojamientos/41/consultar-disponibilidad',
      ConsultarDisponibilidad
    );

    await estable();
  });

  afterEach(() => {
    // Ninguna petición sin responder ni inesperada.
    http.verify();
  });

  // ======================================
  // UTILIDADES
  // ======================================

  function elemento(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  async function estable(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  function campo(id: 'llegada' | 'salida'): HTMLInputElement {
    return elemento().querySelector<HTMLInputElement>(`#${id}`)!;
  }

  async function elegir(id: 'llegada' | 'salida', valor: string): Promise<void> {
    const input = campo(id);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await estable();
  }

  async function elegirFechas(a: string, b: string): Promise<void> {
    await elegir('llegada', a);
    await elegir('salida', b);
  }

  async function consultar(): Promise<void> {
    elemento().querySelector<HTMLButtonElement>('[data-consultar]')!.click();
    await estable();
  }

  function existe(selector: string): boolean {
    return elemento().querySelector(selector) !== null;
  }

  function texto(selector: string): string {
    return elemento().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function peticion(): TestRequest {
    return http.expectOne(r => r.url === urlConsulta);
  }

  // ======================================
  // CA-082 VALIDACIÓN DE FECHAS
  // ======================================

  it('CA-082 exige la fecha de llegada y la de salida y no consulta sin ellas', async () => {

    await consultar();

    expect(texto('[data-error="llegada"]')).toBe('Selecciona la fecha de llegada.');
    expect(texto('[data-error="salida"]')).toBe('Selecciona la fecha de salida.');

    http.expectNone(r => r.url === urlConsulta);
    expect(existe('[data-disponible]')).toBe(false);
  });

  it('CA-082 rechaza una salida igual a la llegada indicando que las fechas son inválidas y sin hacer la petición', async () => {

    await elegirFechas(llegada, llegada);
    await consultar();

    expect(texto('[data-error="salida"]')).toBe(
      'Las fechas no son válidas: la salida debe ser posterior a la llegada.'
    );
    http.expectNone(r => r.url === urlConsulta);
  });

  it('CA-082 rechaza una salida anterior a la llegada sin hacer la petición ni permitir continuar', async () => {

    await elegirFechas(llegada, enDias(8));
    await consultar();

    expect(texto('[data-error="salida"]')).toContain('la salida debe ser posterior a la llegada');
    http.expectNone(r => r.url === urlConsulta);

    expect(existe('[data-continuar]')).toBe(false);
    expect(componente.puedeContinuar()).toBe(false);
  });

  it('CA-082 rechaza fechas inexistentes, con formato distinto de AAAA-MM-DD o una llegada en el pasado', () => {

    const hoy = '2026-11-09';

    expect(erroresFechas('2026-02-30', '2026-03-02', hoy).llegada).toBe('La fecha de llegada no es válida.');
    expect(erroresFechas('09/12/2026', '2026-12-12', hoy).llegada).toBe('La fecha de llegada no es válida.');
    expect(erroresFechas('2026-12-10', '2026-13-01', hoy).salida).toBe('La fecha de salida no es válida.');
    expect(erroresFechas('2026-11-08', '2026-11-12', hoy).llegada).toBe('La fecha de llegada no puede estar en el pasado.');

    // Hoy es una llegada válida; una noche es el mínimo.
    expect(erroresFechas('2026-11-09', '2026-11-10', hoy)).toEqual({ llegada: '', salida: '' });
  });

  it('CA-082 compara días del calendario sin desplazamientos por zona horaria', () => {

    // Hoy se toma de la hora local, no de UTC (23:30 sigue siendo el mismo día).
    expect(hoyLocal(new Date(2026, 10, 9, 23, 30))).toBe('2026-11-09');
    expect(hoyLocal(new Date(2026, 10, 9, 0, 15))).toBe('2026-11-09');

    // Cambios de mes, de año y de horario de verano no corren el día.
    expect(diaSiguiente('2026-12-31')).toBe('2027-01-01');
    expect(diaSiguiente('2028-02-28')).toBe('2028-02-29');
    expect(noches('2026-03-07', '2026-03-09')).toBe(2);
    expect(noches('2026-10-31', '2026-11-02')).toBe(2);

    // Se compara como día, no como texto.
    expect(diaDeFecha('2026-12-09')! < diaDeFecha('2026-12-10')!).toBe(true);
    expect(diaDeFecha('2026-1-9')).toBeNull();
  });

  it('CA-082 muestra el rechazo del servidor (400) como fechas inválidas, porque el backend es la autoridad', async () => {

    await elegirFechas(llegada, salida);
    await consultar();

    peticion().flush(
      { message: 'La fecha de llegada no puede estar en el pasado.' },
      { status: 400, statusText: 'Bad Request' }
    );
    await estable();

    expect(texto('[data-error-consulta]')).toBe('La fecha de llegada no puede estar en el pasado.');
    expect(existe('[data-disponible]')).toBe(false);
    expect(existe('[data-no-disponible]')).toBe(false);
  });

  // ======================================
  // CA-119 ALOJAMIENTO DISPONIBLE
  // ======================================

  it('CA-119 consulta el endpoint real con las fechas en formato AAAA-MM-DD y muestra el alojamiento disponible', async () => {

    await elegirFechas(llegada, salida);

    expect(texto('[data-resumen-fechas]')).toContain('3 noches');

    await consultar();

    const consulta = peticion();

    expect(consulta.request.method).toBe('GET');
    expect(consulta.request.params.get('llegada')).toBe(llegada);
    expect(consulta.request.params.get('salida')).toBe(salida);
    expect(consulta.request.params.get('llegada')).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    // Indicador de carga y sin doble envío.
    expect(existe('[data-consultando]')).toBe(true);
    expect(elemento().querySelector<HTMLButtonElement>('[data-consultar]')!.disabled).toBe(true);
    componente.consultar();
    http.expectNone(r => r.url === urlConsulta);

    consulta.flush({
      idAlojamiento: 41,
      fechaEntrada: llegada,
      fechaSalida: salida,
      disponible: true,
      motivo: null
    });
    await estable();

    expect(texto('[data-disponible]')).toContain('El alojamiento está disponible para estas fechas.');
    expect(existe('[data-no-disponible]')).toBe(false);

    // Las fechas se conservan en el formulario y en la URL.
    expect(campo('llegada').value).toBe(llegada);
    expect(campo('salida').value).toBe(salida);
    expect(TestBed.inject(Router).url).toContain(`llegada=${llegada}`);
    expect(TestBed.inject(Router).url).toContain(`salida=${salida}`);
  });

  it('CA-119 permite continuar con la reserva y transmite las fechas consultadas al siguiente paso', async () => {

    const emitidas: FechasEstadia[] = [];
    componente.continuar.subscribe(fechas => emitidas.push(fechas));

    await elegirFechas(llegada, salida);
    await consultar();

    peticion().flush({ idAlojamiento: 41, fechaEntrada: llegada, fechaSalida: salida, disponible: true });
    await estable();

    const continuar = elemento().querySelector<HTMLButtonElement>('[data-continuar]')!;

    expect(continuar.disabled).toBe(false);

    continuar.click();
    await estable();

    expect(emitidas).toEqual([{ idAlojamiento: 41, llegada, salida }]);
  });

  it('CA-119 invalida el resultado disponible si el huésped cambia las fechas después de consultar', async () => {

    await elegirFechas(llegada, salida);
    await consultar();

    peticion().flush({ idAlojamiento: 41, fechaEntrada: llegada, fechaSalida: salida, disponible: true });
    await estable();

    await elegir('salida', enDias(15));

    expect(existe('[data-disponible]')).toBe(false);
    expect(existe('[data-continuar]')).toBe(false);
    expect(componente.puedeContinuar()).toBe(false);
  });

  // ======================================
  // CA-120 ALOJAMIENTO NO DISPONIBLE
  // ======================================

  it('CA-120 indica que no está disponible con el motivo del servidor, conserva las fechas y no permite continuar', async () => {

    const emitidas: FechasEstadia[] = [];
    componente.continuar.subscribe(fechas => emitidas.push(fechas));

    await elegirFechas(llegada, salida);
    await consultar();

    peticion().flush({
      idAlojamiento: 41,
      fechaEntrada: llegada,
      fechaSalida: salida,
      disponible: false,
      motivo: 'Las fechas se cruzan con una reserva.'
    });
    await estable();

    expect(texto('[data-no-disponible]')).toContain('no está disponible para el período solicitado');
    expect(texto('[data-motivo]')).toBe('Las fechas se cruzan con una reserva.');

    // No es un error de conexión.
    expect(existe('[data-error-consulta]')).toBe(false);
    expect(existe('[data-disponible]')).toBe(false);

    // Las fechas se conservan para cambiarlas.
    expect(campo('llegada').value).toBe(llegada);
    expect(campo('salida').value).toBe(salida);

    // No se puede continuar con la reserva para ese rango.
    expect(existe('[data-continuar]')).toBe(false);
    componente.continuarConReserva();
    expect(emitidas).toEqual([]);
  });

  it('CA-120 permite elegir otras fechas y volver a consultar', async () => {

    await elegirFechas(llegada, salida);
    await consultar();

    peticion().flush({ idAlojamiento: 41, fechaEntrada: llegada, fechaSalida: salida, disponible: false, motivo: 'Las fechas están fuera de los períodos disponibles.' });
    await estable();

    const otraSalida = enDias(12);

    await elegir('salida', otraSalida);

    expect(existe('[data-no-disponible]')).toBe(false);

    await consultar();

    const nueva = peticion();
    expect(nueva.request.params.get('salida')).toBe(otraSalida);

    nueva.flush({ idAlojamiento: 41, fechaEntrada: llegada, fechaSalida: otraSalida, disponible: true });
    await estable();

    expect(existe('[data-disponible]')).toBe(true);
  });

  it('CA-120 distingue la indisponibilidad de un error de conexión, del servidor o de un alojamiento inexistente', async () => {

    const casos: [number, string][] = [
      [0, 'No se pudo conectar con el servidor'],
      [500, 'El servidor no pudo consultar la disponibilidad (HTTP 500)'],
      [404, 'El alojamiento no existe o no está disponible para consulta.']
    ];

    await elegirFechas(llegada, salida);

    for (const [status, mensaje] of casos) {

      await consultar();

      if (status === 0) {
        peticion().error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
      } else {
        peticion().flush({ message: 'Error' }, { status, statusText: 'Error' });
      }
      await estable();

      expect(texto('[data-error-consulta]')).toContain(mensaje);
      expect(existe('[data-no-disponible]')).toBe(false);
      expect(existe('[data-disponible]')).toBe(false);
    }
  });
});
