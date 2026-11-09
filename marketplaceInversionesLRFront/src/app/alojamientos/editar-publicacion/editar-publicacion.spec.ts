import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { EditarPublicacion, MENSAJE_SIN_PERMISO } from './editar-publicacion';
import { AlojamientoRespuesta } from '../models/alojamiento';
import { API_URL } from '../../services/api-url';
import { RolUsuario, SessionService } from '../../services/session.service';
import { routes } from '../../app.routes';

// ======================================
// HU-16 ACTUALIZAR PUBLICACIÓN
// ======================================

describe('EditarPublicacion (HU-16)', () => {

  const baseUrl = 'http://localhost:8081';

  const url = `${baseUrl}/alojamientos`;

  const urlPerfil = `${baseUrl}/usuarios/perfil`;

  let rolActual: RolUsuario | null = 'Anfitrión';

  /** Sesión de prueba: solo decide el rol con el que entra el usuario. */
  const sesion = {
    autenticado: () => rolActual !== null,
    tieneRol: (...roles: RolUsuario[]) =>
      rolActual !== null && roles.includes(rolActual),
    obtenerToken: () => null
  };

  let http: HttpTestingController;

  let harness: RouterTestingHarness;

  let componente: EditarPublicacion;

  /** Publicación #41 del anfitrión 7. */
  const publicacion: AlojamientoRespuesta = {
    idAlojamiento: 41,
    idAnfitrion: 7,
    nombreAnfitrion: 'Laura Gómez',
    estado: 'ACTIVA',
    titulo: 'Casa campestre',
    descripcion: 'Casa amplia con jardín.',
    ubicacion: 'Calle 123 #45-67',
    ciudad: 'Villa de Leyva',
    tipo: 'Casa',
    capacidad: 6,
    habitaciones: 3,
    camas: 4,
    banos: 2,
    precioNoche: 245000,
    horaEntrada: '15:00:00',
    horaSalida: '11:00:00',
    reglas: 'No se permiten fiestas.',
    idPolitica: 1,
    amenidades: ['Wifi'],
    serviciosAdicionales: [
      { idServicio: 5, idAlojamiento: 41, nombre: 'Desayuno', costo: 35000 }
    ]
  };

  beforeEach(async () => {

    rolActual = 'Anfitrión';

    await TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl },
        { provide: SessionService, useValue: sesion }
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);

    harness = await RouterTestingHarness.create();

  });

  afterEach(() => {
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

  /** Abre la edición y responde el detalle del alojamiento. */
  async function abrir(
    ruta = '/host/alojamientos/41/editar',
    alojamiento: AlojamientoRespuesta = publicacion
  ): Promise<void> {

    componente = await harness.navigateByUrl(ruta, EditarPublicacion);

    const detalle = http.expectOne(`${url}/41`);

    expect(detalle.request.method).toBe('GET');

    detalle.flush(alojamiento);

    await estable();
  }

  /** Responde el perfil del anfitrión autenticado. */
  async function responderPerfil(idUsuario: number): Promise<void> {

    http.expectOne(urlPerfil).flush({
      idUsuario,
      nombre: 'Anfitrión',
      correo: 'anfitrion@correo.com',
      rol: 'ANFITRION',
      estado: 'ACTIVO'
    });

    await estable();
  }

  async function responderPoliticas(): Promise<void> {

    http.expectOne(`${url}/politicas`).flush([
      { idPolitica: 1, nombre: 'Flexible', condiciones: 'Reembolso total' },
      { idPolitica: 2, nombre: 'Estricta', condiciones: 'Sin reembolso' }
    ]);

    await estable();
  }

  /** Propietario: detalle, perfil con su id y políticas. */
  async function abrirComoPropietario(): Promise<void> {
    await abrir();
    await responderPerfil(7);
    await responderPoliticas();
  }

  function campo<T extends HTMLElement = HTMLInputElement>(id: string): T {
    return elemento().querySelector<T>(`#${id}`)!;
  }

  async function escribir(id: string, valor: string): Promise<void> {

    const input = campo<HTMLInputElement | HTMLTextAreaElement>(id);

    input.value = valor;

    input.dispatchEvent(new Event('input'));

    input.dispatchEvent(new Event('blur'));

    await estable();
  }

  async function pulsar(selector: string): Promise<void> {

    elemento().querySelector<HTMLButtonElement>(selector)!.click();

    await estable();
  }

  function errorCampo(nombre: string): string | null {
    return elemento()
      .querySelector(`[data-error="${nombre}"]`)
      ?.textContent?.trim() ?? null;
  }

  function alerta(): string {
    return elemento().querySelector('[role="alert"]')?.textContent?.trim() ?? '';
  }

  function confirmacion(): string {
    return elemento().querySelector('[role="status"]')?.textContent?.trim() ?? '';
  }

  function formulario(): HTMLFormElement | null {
    return elemento().querySelector('[data-formulario-edicion]');
  }

  // ======================================
  // CA-100 ACTUALIZACIÓN DE PUBLICACIÓN
  // ======================================

  it('CA-100 carga los datos actuales, valida y guarda la publicación mostrando la información actualizada', async () => {

    await abrirComoPropietario();

    // 1. Formulario inicializado con los datos actuales

    expect(formulario()).not.toBeNull();

    expect(campo('titulo').value).toBe('Casa campestre');
    expect(campo('ciudad').value).toBe('Villa de Leyva');
    expect(campo<HTMLTextAreaElement>('descripcion').value).toBe('Casa amplia con jardín.');
    expect(campo<HTMLSelectElement>('tipo').value).toBe('Casa');
    expect(campo('capacidad').value).toBe('6');
    expect(campo('precioNoche').value).toBe('245000');
    expect(campo('horaEntrada').value).toBe('15:00');
    expect(campo('horaSalida').value).toBe('11:00');
    expect(campo<HTMLTextAreaElement>('reglas').value).toBe('No se permiten fiestas.');
    expect(campo<HTMLSelectElement>('idPolitica').selectedIndex).toBe(1);

    expect(
      elemento().querySelector('[data-estado]')?.textContent?.trim()
    ).toBe('Activa');

    // Los servicios adicionales actuales también se muestran

    expect(
      elemento().querySelector('[data-servicio] [data-nombre]')?.textContent?.trim()
    ).toBe('Desayuno');

    // 2. Validación junto a cada campo: no se envía nada inválido

    await escribir('titulo', '   ');
    await escribir('precioNoche', '0');
    await escribir('capacidad', '');

    expect(errorCampo('titulo')).toBe('Debes indicar el título del alojamiento.');
    expect(errorCampo('precioNoche')).toBe('El precio por noche debe ser mayor que cero.');
    expect(errorCampo('capacidad')).toBe('Este dato ya estaba registrado; indica un valor.');

    await pulsar('[data-guardar]');

    expect(alerta()).toBe('Revisa los datos marcados antes de guardar.');

    http.expectNone(`${url}/41`);

    // 3. Datos válidos: PUT /alojamientos/41

    await escribir('titulo', '  Casa campestre renovada  ');
    await escribir('precioNoche', '300000');
    await escribir('capacidad', '8');
    await escribir('reglas', 'No se permiten fiestas ni mascotas.');

    expect(errorCampo('titulo')).toBeNull();
    expect(errorCampo('precioNoche')).toBeNull();

    await pulsar('[data-guardar]');

    // Estado de carga y sin envíos duplicados

    expect(
      elemento().querySelector<HTMLButtonElement>('[data-guardar]')!.disabled
    ).toBe(true);

    expect(
      elemento().querySelector('[data-guardar]')!.textContent?.trim()
    ).toBe('Guardando...');

    componente.guardar();

    const actualizar = http.expectOne(`${url}/41`);

    expect(actualizar.request.method).toBe('PUT');

    expect(actualizar.request.body).toEqual({
      titulo: 'Casa campestre renovada',
      descripcion: 'Casa amplia con jardín.',
      ubicacion: 'Calle 123 #45-67',
      ciudad: 'Villa de Leyva',
      tipo: 'Casa',
      capacidad: 8,
      habitaciones: 3,
      camas: 4,
      banos: 2,
      precioNoche: 300000,
      idPolitica: 1,
      horaEntrada: '15:00',
      horaSalida: '11:00',
      reglas: 'No se permiten fiestas ni mascotas.',
      amenidades: ['Wifi']
    });

    // 4. El servidor guarda y devuelve la información actualizada

    actualizar.flush({
      ...publicacion,
      ...actualizar.request.body,
      horaEntrada: '15:00:00',
      horaSalida: '11:00:00',
      serviciosAdicionales: undefined,
      fechaActualizacion: '2026-11-09T10:00:00'
    });

    await estable();

    expect(confirmacion()).toBe(
      'Los cambios de la publicación se guardaron correctamente.'
    );

    expect(alerta()).toBe('');

    expect(campo('titulo').value).toBe('Casa campestre renovada');
    expect(campo('precioNoche').value).toBe('300000');
    expect(campo('capacidad').value).toBe('8');

    expect(
      elemento().querySelector('[data-resumen]')?.textContent
    ).toContain('Casa campestre renovada');

    // Los servicios adicionales no se pierden con el PUT

    expect(
      elemento().querySelectorAll('[data-servicio]').length
    ).toBe(1);

    // Sin cambios pendientes, "Descartar" queda deshabilitado

    expect(
      elemento().querySelector<HTMLButtonElement>('[data-descartar]')!.disabled
    ).toBe(true);

    // 5. Error del servidor: se muestra su mensaje

    await escribir('precioNoche', '310000');

    await pulsar('[data-guardar]');

    http.expectOne(`${url}/41`).flush(
      { message: 'La política de cancelación no existe.' },
      { status: 400, statusText: 'Bad Request' }
    );

    await estable();

    expect(alerta()).toBe('La política de cancelación no existe.');

    expect(confirmacion()).toBe('');

    // El formulario sigue disponible con lo que escribió

    expect(campo('precioNoche').value).toBe('310000');

    // 6. Descartar vuelve a los datos guardados

    await pulsar('[data-descartar]');

    expect(campo('precioNoche').value).toBe('300000');
  });

  // ======================================
  // CA-101 RESTRICCIÓN PARA NO AUTORIZADOS
  // ======================================

  it('CA-101 rechaza la modificación de la publicación y de sus servicios a quien no es propietario ni administrador', async () => {

    // 1. Anfitrión que no es el propietario (perfil 99, publicación del 7)

    await abrir();

    await responderPerfil(99);

    // No se ofrece el formulario ni los servicios adicionales

    expect(formulario()).toBeNull();

    expect(elemento().querySelector('[data-servicios-adicionales]')).toBeNull();

    expect(alerta()).toBe(MENSAJE_SIN_PERMISO);

    // Aunque se invoque el guardado, no sale ninguna petición

    componente.guardar();

    http.expectNone(req => req.method === 'PUT');

    http.expectNone(`${url}/politicas`);
  });

  it('CA-101 bloquea la edición cuando el servidor responde 403 al guardar la publicación', async () => {

    await abrirComoPropietario();

    await escribir('precioNoche', '260000');

    await pulsar('[data-guardar]');

    http.expectOne(`${url}/41`).flush(
      {
        message: 'Solo el anfitrión propietario o un administrador puede modificar este alojamiento.',
        status: 403
      },
      { status: 403, statusText: 'Forbidden' }
    );

    await estable();

    expect(componente.acceso()).toBe('denegado');

    expect(formulario()).toBeNull();

    expect(alerta()).toBe(MENSAJE_SIN_PERMISO);
  });

  it('CA-101 bloquea la edición cuando el servidor responde 403 al modificar un servicio adicional', async () => {

    await abrirComoPropietario();

    await pulsar('[data-eliminar-servicio]');

    await pulsar('[data-confirmar]');

    const eliminar = http.expectOne(`${url}/41/servicios/5`);

    expect(eliminar.request.method).toBe('DELETE');

    eliminar.flush(
      { message: 'Solo el anfitrión propietario o un administrador puede modificar este alojamiento.' },
      { status: 403, statusText: 'Forbidden' }
    );

    await estable();

    expect(formulario()).toBeNull();

    expect(elemento().querySelector('[data-servicios-adicionales]')).toBeNull();

    expect(alerta()).toBe(MENSAJE_SIN_PERMISO);
  });

  it('CA-101 trata el 401 como sesión expirada y ofrece volver a iniciar sesión', async () => {

    await abrirComoPropietario();

    await escribir('reglas', 'Nuevas reglas.');

    await pulsar('[data-guardar]');

    http.expectOne(`${url}/41`).flush(
      { message: 'No autenticado', status: 401 },
      { status: 401, statusText: 'Unauthorized' }
    );

    await estable();

    expect(componente.acceso()).toBe('sin-sesion');

    expect(formulario()).toBeNull();

    expect(alerta()).toContain('Tu sesión expiró. Inicia sesión nuevamente.');

    expect(
      elemento().querySelector('[role="alert"] a')?.getAttribute('href')
    ).toBe('/login');
  });
});
