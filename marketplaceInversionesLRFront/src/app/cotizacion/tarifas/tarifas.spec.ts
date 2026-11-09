import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { Tarifas } from './tarifas';
import { API_URL } from '../../core/services/api-url';

// ======================================
// HU-24 CONFIGURAR PORCENTAJES DE TARIFAS
// ======================================

describe('Tarifas (HU-24)', () => {

  const baseUrl = 'http://localhost:8081';

  const url = `${baseUrl}/admin/tarifas`;

  let fixture: ComponentFixture<Tarifas>;

  let http: HttpTestingController;

  let elemento: HTMLElement;

  beforeEach(async () => {

    await TestBed.configureTestingModule({
      imports: [Tarifas],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(Tarifas);

    http = TestBed.inject(HttpTestingController);

    elemento = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();

  });

  afterEach(() => {
    http.verify();
  });

  // ======================================
  // UTILIDADES
  // ======================================

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function escribir(
    selector: string,
    valor: string
  ): Promise<void> {

    const input =
      elemento.querySelector<HTMLInputElement>(selector)!;

    input.value = valor;

    input.dispatchEvent(new Event('input'));

    input.dispatchEvent(new Event('blur'));

    await estable();

  }

  function valorInput(selector: string): string {
    return elemento.querySelector<HTMLInputElement>(selector)!.value;
  }

  function vigente(campo: 'limpieza' | 'servicio'): string {
    return elemento
      .querySelector(`[data-vigente="${campo}"]`)!
      .textContent!
      .trim();
  }

  function errorCampo(campo: string): string | null {
    return elemento
      .querySelector(`[data-error="${campo}"]`)
      ?.textContent?.trim() ?? null;
  }

  async function enviar(): Promise<void> {

    elemento
      .querySelector('form')!
      .dispatchEvent(new Event('submit'));

    await estable();

  }

  // ======================================
  // CA-084
  // ======================================

  it('CA-084 muestra los porcentajes predeterminados cuando no hay configuración y aplica el valor recién guardado', async () => {

    // 1. Consulta inicial de los porcentajes vigentes

    expect(elemento.textContent).toContain(
      'Cargando porcentajes de tarifas...'
    );

    const consulta = http.expectOne(url);

    expect(consulta.request.method).toBe('GET');

    consulta.flush({
      porcentajeLimpieza: 5,
      porcentajeServicio: 10,
      idConfiguracion: null,
      idUsuario: null,
      fecha: null,
      porDefecto: true
    });

    await estable();

    // 2. Visualización de los valores predeterminados

    expect(vigente('limpieza')).toBe('5%');

    expect(vigente('servicio')).toBe('10%');

    expect(
      elemento.querySelector('[data-por-defecto]')
    ).toBeTruthy();

    expect(valorInput('#porcentaje-limpieza')).toBe('5');

    expect(valorInput('#porcentaje-servicio')).toBe('10');

    // 3. Validaciones por campo

    await escribir('#porcentaje-limpieza', '');

    expect(errorCampo('porcentajeLimpieza')).toBe(
      'Este campo es obligatorio.'
    );

    await escribir('#porcentaje-limpieza', '-1');

    expect(errorCampo('porcentajeLimpieza')).toBe(
      'El porcentaje no puede ser negativo.'
    );

    await escribir('#porcentaje-servicio', '101');

    expect(errorCampo('porcentajeServicio')).toBe(
      'El porcentaje no puede ser mayor que 100.'
    );

    await escribir('#porcentaje-servicio', '12.345');

    expect(errorCampo('porcentajeServicio')).toBe(
      'Usa máximo dos decimales.'
    );

    // Con errores no se envía nada al backend

    await enviar();

    http.expectNone(url);

    expect(
      elemento.querySelector('[role="alert"]')?.textContent
    ).toContain('Revisa los porcentajes');

    // 4. Modificar ambos porcentajes

    await escribir('#porcentaje-limpieza', '7.5');

    await escribir('#porcentaje-servicio', '12');

    expect(errorCampo('porcentajeLimpieza')).toBeNull();

    expect(errorCampo('porcentajeServicio')).toBeNull();

    // 5. Envío al servicio (operación conjunta PUT)

    await enviar();

    expect(
      elemento.querySelector('button[type="submit"]')!.textContent
    ).toContain('Guardando...');

    const guardado = http.expectOne(url);

    expect(guardado.request.method).toBe('PUT');

    expect(guardado.request.body).toEqual({
      porcentajeLimpieza: 7.5,
      porcentajeServicio: 12
    });

    guardado.flush({
      porcentajeLimpieza: 7.5,
      porcentajeServicio: 12,
      idConfiguracion: 3,
      idUsuario: 1,
      fecha: '2026-11-07T10:30:00',
      porDefecto: false
    });

    await estable();

    // 6. Se refleja el valor vigente recién guardado

    expect(vigente('limpieza')).toBe('7.5%');

    expect(vigente('servicio')).toBe('12%');

    expect(
      elemento.querySelector('[data-por-defecto]')
    ).toBeNull();

    expect(
      elemento.querySelector('[role="status"]')?.textContent
    ).toContain('Los porcentajes se actualizaron correctamente.');

    // 7. Volver a consultar trae el valor del backend (sin caché)

    fixture.componentInstance.cargar();

    await estable();

    http.expectOne(url).flush({
      porcentajeLimpieza: 7.5,
      porcentajeServicio: 12,
      idConfiguracion: 3,
      idUsuario: 1,
      fecha: '2026-11-07T10:30:00',
      porDefecto: false
    });

    await estable();

    expect(vigente('limpieza')).toBe('7.5%');

    expect(valorInput('#porcentaje-servicio')).toBe('12');

  });

  it('CA-084 muestra los porcentajes personalizados vigentes y el error del backend al guardar', async () => {

    http.expectOne(url).flush({
      porcentajeLimpieza: 8,
      porcentajeServicio: 15,
      idConfiguracion: 2,
      idUsuario: 4,
      fecha: '2026-11-01T09:00:00',
      porDefecto: false
    });

    await estable();

    expect(vigente('limpieza')).toBe('8%');

    expect(vigente('servicio')).toBe('15%');

    expect(
      elemento.querySelector('[data-por-defecto]')
    ).toBeNull();

    await escribir('#porcentaje-limpieza', '9');

    await enviar();

    http.expectOne(url).flush(
      { message: 'Los porcentajes de limpieza y servicio son obligatorios y deben estar entre 0 y 100.' },
      { status: 400, statusText: 'Bad Request' }
    );

    await estable();

    expect(
      elemento.querySelector('[role="alert"]')?.textContent
    ).toContain('deben estar entre 0 y 100');

    // El valor vigente no cambia si el backend rechaza el cambio

    expect(vigente('limpieza')).toBe('8%');

  });

  it('CA-084 informa el error al consultar y permite reintentar', async () => {

    http.expectOne(url).flush(
      { message: 'No tienes permisos', status: 403 },
      { status: 403, statusText: 'Forbidden' }
    );

    await estable();

    expect(
      elemento.querySelector('[role="alert"]')?.textContent
    ).toContain('No tienes permisos para configurar las tarifas.');

    const reintentar = Array.from(
      elemento.querySelectorAll('button')
    ).find(b => b.textContent?.includes('Reintentar'))!;

    reintentar.click();

    await estable();

    http.expectOne(url).flush({
      porcentajeLimpieza: 5,
      porcentajeServicio: 10,
      porDefecto: true
    });

    await estable();

    expect(vigente('limpieza')).toBe('5%');

  });

});
