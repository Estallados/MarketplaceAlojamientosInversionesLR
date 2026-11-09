import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { ServiciosAdicionales } from './servicios-adicionales';
import { ServicioAdicional } from '../models/alojamiento';
import { API_URL } from '../../services/api-url';

// ======================================
// HU-16 SERVICIOS ADICIONALES CON COSTO (CA-185)
// ======================================

describe('ServiciosAdicionales (HU-16)', () => {

  const baseUrl = 'http://localhost:8081';

  const url = `${baseUrl}/alojamientos/41/servicios`;

  let fixture: ComponentFixture<ServiciosAdicionales>;

  let componente: ServiciosAdicionales;

  let http: HttpTestingController;

  let elemento: HTMLElement;

  /** Lo que el componente avisa a la página de edición. */
  let cambios: ServicioAdicional[][];

  let revocado: string[];

  beforeEach(async () => {

    await TestBed.configureTestingModule({
      imports: [ServiciosAdicionales],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ServiciosAdicionales);

    componente = fixture.componentInstance;

    http = TestBed.inject(HttpTestingController);

    elemento = fixture.nativeElement as HTMLElement;

    cambios = [];

    revocado = [];

    componente.serviciosCambiados.subscribe(lista => cambios.push(lista));

    componente.accesoRevocado.subscribe(estado => revocado.push(estado));

    fixture.componentRef.setInput('idAlojamiento', 41);

    fixture.componentRef.setInput('servicios', [
      { idServicio: 5, idAlojamiento: 41, nombre: 'Desayuno', costo: 35000 },
      { idServicio: 6, idAlojamiento: 41, nombre: 'Traslado', costo: 80000 }
    ]);

    await estable();
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

  async function escribir(selector: string, valor: string): Promise<void> {

    const input = elemento.querySelector<HTMLInputElement>(selector)!;

    input.value = valor;

    input.dispatchEvent(new Event('input'));

    await estable();
  }

  async function pulsar(selector: string): Promise<void> {

    elemento.querySelector<HTMLButtonElement>(selector)!.click();

    await estable();
  }

  function filas(): HTMLElement[] {
    return Array.from(elemento.querySelectorAll<HTMLElement>('[data-servicio]'));
  }

  function fila(idServicio: number): HTMLElement {
    return elemento.querySelector<HTMLElement>(`[data-servicio][data-id="${idServicio}"]`)!;
  }

  function texto(selector: string, raiz: ParentNode = elemento): string {
    return raiz.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function errorCampo(nombre: string): string | null {
    return elemento
      .querySelector(`[data-error="${nombre}"]`)
      ?.textContent?.trim() ?? null;
  }

  // ======================================
  // CA-185 GESTIÓN DE SERVICIOS ADICIONALES
  // ======================================

  it('CA-185 muestra los servicios adicionales de la publicación con su nombre y costo', () => {

    expect(filas().length).toBe(2);

    expect(texto('[data-nombre]', fila(5))).toBe('Desayuno');

    expect(texto('[data-costo]', fila(5))).toContain('35.000');

    expect(texto('[data-nombre]', fila(6))).toBe('Traslado');

    expect(texto('[data-costo]', fila(6))).toContain('80.000');

    // Se informa que los cambios no alteran reservas confirmadas

    expect(texto('[data-servicios-adicionales] p')).toContain(
      'las reservas ya confirmadas conservan el valor'
    );
  });

  it('CA-185 valida nombre no vacío y costo positivo antes de enviar', async () => {

    // Vacío

    await pulsar('[data-agregar-servicio]');

    expect(errorCampo('nuevo-nombre')).toBe('Debes indicar el nombre del servicio.');

    expect(errorCampo('nuevo-costo')).toBe('El costo debe ser un valor positivo.');

    // Nombre en blanco y costo cero

    await escribir('#nuevoNombre', '   ');

    await escribir('#nuevoCosto', '0');

    await pulsar('[data-agregar-servicio]');

    expect(errorCampo('nuevo-nombre')).toBe('Debes indicar el nombre del servicio.');

    expect(errorCampo('nuevo-costo')).toBe('El costo debe ser un valor positivo.');

    // Costo negativo

    await escribir('#nuevoNombre', 'Desayuno');

    await escribir('#nuevoCosto', '-5000');

    await pulsar('[data-agregar-servicio]');

    expect(errorCampo('nuevo-nombre')).toBeNull();

    expect(errorCampo('nuevo-costo')).toBe('El costo debe ser un valor positivo.');

    // Editar con costo no positivo tampoco se envía

    await pulsar('[data-editar-servicio]');

    await escribir('[data-edicion-costo]', '0');

    await pulsar('[data-guardar-servicio]');

    expect(errorCampo('edicion-costo')).toBe('El costo debe ser un valor positivo.');

    http.expectNone(() => true);

    expect(cambios.length).toBe(0);
  });

  it('CA-185 registra un servicio adicional asociado a la publicación', async () => {

    await escribir('#nuevoNombre', '  Limpieza extra  ');

    await escribir('#nuevoCosto', '45000');

    await pulsar('[data-agregar-servicio]');

    // Estado de carga y sin envíos duplicados

    expect(texto('[data-agregar-servicio]')).toBe('Procesando...');

    componente.agregar();

    const crear = http.expectOne(url);

    expect(crear.request.method).toBe('POST');

    expect(crear.request.body).toEqual({ nombre: 'Limpieza extra', costo: 45000 });

    crear.flush(
      { idServicio: 9, idAlojamiento: 41, nombre: 'Limpieza extra', costo: 45000 },
      { status: 201, statusText: 'Created' }
    );

    await estable();

    expect(filas().length).toBe(3);

    expect(texto('[data-nombre]', fila(9))).toBe('Limpieza extra');

    expect(texto('[data-costo]', fila(9))).toContain('45.000');

    expect(texto('[data-mensaje-servicios]')).toBe(
      'Servicio "Limpieza extra" agregado. Se aplicará a las nuevas cotizaciones.'
    );

    // El formulario queda limpio y la página de edición recibe la lista

    expect(elemento.querySelector<HTMLInputElement>('#nuevoNombre')!.value).toBe('');

    expect(cambios.at(-1)!.map(s => s.idServicio)).toEqual([5, 6, 9]);
  });

  it('CA-185 modifica un servicio adicional existente', async () => {

    await pulsar(`[data-servicio][data-id="5"] [data-editar-servicio]`);

    // La edición parte de los datos actuales

    expect(
      elemento.querySelector<HTMLInputElement>('[data-edicion-nombre]')!.value
    ).toBe('Desayuno');

    await escribir('[data-edicion-nombre]', 'Desayuno típico');

    await escribir('[data-edicion-costo]', '40000');

    await pulsar('[data-guardar-servicio]');

    const actualizar = http.expectOne(`${url}/5`);

    expect(actualizar.request.method).toBe('PUT');

    expect(actualizar.request.body).toEqual({ nombre: 'Desayuno típico', costo: 40000 });

    actualizar.flush({ idServicio: 5, idAlojamiento: 41, nombre: 'Desayuno típico', costo: 40000 });

    await estable();

    expect(elemento.querySelector('[data-edicion-nombre]')).toBeNull();

    expect(texto('[data-nombre]', fila(5))).toBe('Desayuno típico');

    expect(texto('[data-costo]', fila(5))).toContain('40.000');

    expect(texto('[data-mensaje-servicios]')).toBe(
      'Servicio "Desayuno típico" actualizado. Las reservas ya confirmadas conservan su valor.'
    );

    expect(cambios.at(-1)!.find(s => s.idServicio === 5)!.costo).toBe(40000);
  });

  it('CA-185 elimina un servicio adicional después de confirmar', async () => {

    // Cancelar la confirmación no elimina nada

    await pulsar(`[data-servicio][data-id="6"] [data-eliminar-servicio]`);

    expect(texto('[data-confirmar-eliminar]')).toContain('¿Eliminar el servicio "Traslado"?');

    await pulsar('[data-cancelar-eliminar]');

    expect(elemento.querySelector('[data-confirmar-eliminar]')).toBeNull();

    http.expectNone(() => true);

    // Confirmar: DELETE /alojamientos/41/servicios/6

    await pulsar(`[data-servicio][data-id="6"] [data-eliminar-servicio]`);

    await pulsar('[data-confirmar]');

    const eliminar = http.expectOne(`${url}/6`);

    expect(eliminar.request.method).toBe('DELETE');

    eliminar.flush({ message: 'Servicio adicional eliminado.' });

    await estable();

    expect(filas().length).toBe(1);

    expect(fila(6)).toBeNull();

    expect(texto('[data-mensaje-servicios]')).toBe(
      'Servicio "Traslado" eliminado. Las reservas ya confirmadas conservan su valor.'
    );

    expect(cambios.at(-1)!.map(s => s.idServicio)).toEqual([5]);
  });

  it('CA-185 muestra el mensaje del servidor cuando rechaza el servicio y avisa del 403', async () => {

    // 400 del backend: nombre y costo positivo

    await escribir('#nuevoNombre', 'Spa');

    await escribir('#nuevoCosto', '500');

    await pulsar('[data-agregar-servicio]');

    http.expectOne(url).flush(
      { message: 'El servicio adicional necesita un nombre y un costo positivo.' },
      { status: 400, statusText: 'Bad Request' }
    );

    await estable();

    expect(texto('[data-error-servicios]')).toBe(
      'El servicio adicional necesita un nombre y un costo positivo.'
    );

    expect(filas().length).toBe(2);

    // 404 del servicio

    await pulsar(`[data-servicio][data-id="5"] [data-eliminar-servicio]`);

    await pulsar('[data-confirmar]');

    http.expectOne(`${url}/5`).flush(
      { message: 'El servicio adicional no existe.' },
      { status: 404, statusText: 'Not Found' }
    );

    await estable();

    expect(texto('[data-error-servicios]')).toBe('El servicio adicional no existe.');

    // 403: la página de edición bloquea la publicación (CA-101)

    await pulsar(`[data-servicio][data-id="6"] [data-editar-servicio]`);

    await escribir('[data-edicion-costo]', '90000');

    await pulsar('[data-guardar-servicio]');

    http.expectOne(`${url}/6`).flush(
      { message: 'Solo el anfitrión propietario o un administrador puede modificar este alojamiento.' },
      { status: 403, statusText: 'Forbidden' }
    );

    await estable();

    expect(revocado).toEqual(['denegado']);

    expect(cambios.length).toBe(0);
  });
});
