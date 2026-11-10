import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter
} from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Location } from '@angular/common';
import { firstValueFrom } from 'rxjs';

import { EditarPublicacion, MENSAJE_SIN_PERMISO } from './editar-publicacion';
import { AlojamientoRespuesta } from '../models/alojamiento';
import { PermisoPublicacionService } from '../services/permiso-publicacion.service';
import { roleGuard } from '../../core/guards/role.guard';
import { API_URL } from '../../core/services/api-url';
import { RolUsuario, SessionService } from '../../core/services/session.service';
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

  /** Ejecuta roleGuard con los datos de la ruta indicada. */
  function ejecutarGuard(path: string): boolean | UrlTree {

    const ruta = routes.find(r => r.path === path)!;

    return TestBed.runInInjectionContext(() =>
      roleGuard(
        { data: ruta.data } as unknown as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot
      )
    ) as boolean | UrlTree;
  }

  function destino(resultado: boolean | UrlTree): string {

    expect(resultado instanceof UrlTree).toBe(true);

    return TestBed.inject(Router).serializeUrl(resultado as UrlTree);
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
  // CA-100 TARIFAS PROPIAS DEL ALOJAMIENTO (RF-023)
  // ======================================

  /** Propietario con una publicación que ya tiene tarifa de limpieza propia. */
  async function abrirConTarifas(): Promise<void> {
    await abrir('/host/alojamientos/41/editar', {
      ...publicacion,
      // Sin porcentajeServicio: usa la tarifa general.
      porcentajeLimpieza: 5
    });
    await responderPerfil(7);
    await responderPoliticas();
  }

  function tarifaActual(campoTarifa: string): string {
    return elemento()
      .querySelector(`[data-tarifa-actual="${campoTarifa}"]`)
      ?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('CA-100 muestra las tarifas actuales del alojamiento y guarda las nuevas con el PUT de la publicación', async () => {

    await abrirConTarifas();

    expect(campo('porcentajeLimpieza').value).toBe('5');
    expect(campo('porcentajeServicio').value).toBe('');
    expect(tarifaActual('porcentajeLimpieza')).toBe('Actual: 5%');
    expect(tarifaActual('porcentajeServicio')).toBe('Actual: Sin definir');

    await escribir('porcentajeLimpieza', '8');
    await escribir('porcentajeServicio', '12.5');

    await pulsar('[data-guardar]');

    const actualizar = http.expectOne(`${url}/41`);
    expect(actualizar.request.method).toBe('PUT');
    expect(actualizar.request.body.porcentajeLimpieza).toBe(8);
    expect(actualizar.request.body.porcentajeServicio).toBe(12.5);

    actualizar.flush({ ...publicacion, porcentajeLimpieza: 8, porcentajeServicio: 12.5 });
    await estable();

    expect(confirmacion()).toBe('Los cambios de la publicación se guardaron correctamente.');
    expect(tarifaActual('porcentajeLimpieza')).toBe('Actual: 8%');
    expect(tarifaActual('porcentajeServicio')).toBe('Actual: 12.5%');
  });

  it('CA-100 valida que las tarifas estén entre 0 y 100 y no envía una tarifa vacía', async () => {

    await abrirConTarifas();

    await escribir('porcentajeLimpieza', '150');
    expect(errorCampo('porcentajeLimpieza')).toBe('La tarifa de limpieza debe ser un porcentaje entre 0 y 100.');

    await escribir('porcentajeServicio', '-1');
    expect(errorCampo('porcentajeServicio')).toBe('La tarifa de servicio debe ser un porcentaje entre 0 y 100.');

    await pulsar('[data-guardar]');
    expect(alerta()).toBe('Revisa los datos marcados antes de guardar.');
    http.expectNone(`${url}/41`);

    // Una tarifa ya registrada no se puede vaciar: el PUT conserva los valores en null.
    await escribir('porcentajeLimpieza', '');
    expect(errorCampo('porcentajeLimpieza')).toBe('Este dato ya estaba registrado; indica un valor.');

    // La de servicio no estaba definida: vacía sigue usando la tarifa general.
    await escribir('porcentajeLimpieza', '0');
    await escribir('porcentajeServicio', '');
    expect(errorCampo('porcentajeLimpieza')).toBeNull();
    expect(errorCampo('porcentajeServicio')).toBeNull();

    await pulsar('[data-guardar]');

    const actualizar = http.expectOne(`${url}/41`);
    expect(actualizar.request.body.porcentajeLimpieza).toBe(0);
    expect(actualizar.request.body).not.toHaveProperty('porcentajeServicio');
    actualizar.flush({ ...publicacion, porcentajeLimpieza: 0 });
    await estable();
  });

  it('CA-100 muestra las fotografías de la publicación y no las pierde al guardar el formulario', async () => {

    const imagenes = [
      { idImagen: 1, idAlojamiento: 41, url: 'https://res.cloudinary.com/demo/1.jpg', principal: true },
      { idImagen: 2, idAlojamiento: 41, url: 'https://res.cloudinary.com/demo/2.jpg', principal: false }
    ];

    await abrir('/host/alojamientos/41/editar', { ...publicacion, detalleImagenes: imagenes });
    await responderPerfil(7);
    await responderPoliticas();

    const fotos = () => elemento().querySelectorAll('[data-fotografias] [data-foto]').length;
    expect(fotos()).toBe(2);

    // Cambiar la principal actualiza la publicación con la lista del backend.
    elemento().querySelector<HTMLButtonElement>('[data-foto][data-id="2"] [data-principal]')!.click();
    await estable();
    http.expectOne(`${url}/41/imagenes/2/principal`).flush([
      { ...imagenes[1], principal: true },
      { ...imagenes[0], principal: false }
    ]);
    await estable();
    expect(componente.fotografias()[0].idImagen).toBe(2);

    // El PUT no gestiona imágenes: si su respuesta no las trae, se conservan.
    await escribir('precioNoche', '260000');
    await pulsar('[data-guardar]');
    const actualizar = http.expectOne(`${url}/41`);
    expect(actualizar.request.body).not.toHaveProperty('imagenes');
    actualizar.flush({ ...publicacion, precioNoche: 260000 });
    await estable();

    expect(fotos()).toBe(2);
    expect(componente.fotografias()[0].idImagen).toBe(2);
  });

  it('HU-16 el botón Volver regresa a la página anterior', async () => {

    // Página previa dentro de la aplicación (no hace peticiones al abrir).
    await harness.navigateByUrl('/alojamientos/41/consultar-disponibilidad');

    await abrirComoPropietario();

    const atras = vi.spyOn(TestBed.inject(Location), 'back').mockImplementation(() => undefined);

    const boton = elemento().querySelector<HTMLButtonElement>('[data-volver]')!;
    expect(boton.textContent?.trim()).toBe('← Volver');

    boton.click();
    await estable();

    expect(atras).toHaveBeenCalledTimes(1);
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

  // ======================================
  // CA-113 PROPIETARIO O ADMINISTRADOR AUTORIZADO
  // ======================================

  it('CA-113 permite al anfitrión propietario modificar su publicación', async () => {

    // La ruta de anfitrión admite el rol ANFITRION

    expect(ejecutarGuard('host/alojamientos/:id/editar')).toBe(true);

    // Es el propietario: idUsuario del perfil = idAnfitrion

    await abrirComoPropietario();

    expect(componente.acceso()).toBe('permitido');

    expect(formulario()).not.toBeNull();

    expect(elemento().querySelector('[data-servicios-adicionales]')).not.toBeNull();

    await escribir('ciudad', 'Tunja');

    await pulsar('[data-guardar]');

    const actualizar = http.expectOne(`${url}/41`);

    expect(actualizar.request.method).toBe('PUT');

    expect(actualizar.request.body.ciudad).toBe('Tunja');

    actualizar.flush({ ...publicacion, ciudad: 'Tunja' });

    await estable();

    expect(confirmacion()).toBe(
      'Los cambios de la publicación se guardaron correctamente.'
    );

    expect(campo('ciudad').value).toBe('Tunja');
  });

  it('CA-113 permite al ADMINISTRADOR modificar la publicación de otro anfitrión', async () => {

    rolActual = 'Administrador';

    // La ruta de administración admite el rol ADMINISTRADOR

    expect(ejecutarGuard('admin/alojamientos/:id/editar')).toBe(true);

    await abrir('/admin/alojamientos/41/editar');

    // No necesita ser el propietario: no se consulta su perfil

    http.expectNone(urlPerfil);

    await responderPoliticas();

    expect(componente.acceso()).toBe('permitido');

    expect(formulario()).not.toBeNull();

    expect(
      elemento().querySelector('[data-resumen]')?.textContent
    ).toContain('Anfitrión: Laura Gómez');

    await escribir('precioNoche', '199000');

    await pulsar('[data-guardar]');

    const actualizar = http.expectOne(`${url}/41`);

    expect(actualizar.request.method).toBe('PUT');

    expect(actualizar.request.body.precioNoche).toBe(199000);

    actualizar.flush({ ...publicacion, precioNoche: 199000 });

    await estable();

    expect(confirmacion()).toBe(
      'Los cambios de la publicación se guardaron correctamente.'
    );

    expect(campo('precioNoche').value).toBe('199000');
  });

  // ======================================
  // CA-114 DENEGACIÓN A USUARIOS SIN PERMISOS
  // ======================================

  it('CA-114 deniega el acceso a la edición a un usuario autenticado sin rol de anfitrión ni administrador', async () => {

    rolActual = 'Huésped';

    // Las rutas de edición lo devuelven al inicio

    expect(destino(ejecutarGuard('host/alojamientos/:id/editar'))).toBe('/home');

    expect(destino(ejecutarGuard('admin/alojamientos/:id/editar'))).toBe('/home');

    // El permiso se niega sin consultar el perfil

    const permitido = await firstValueFrom(
      TestBed.inject(PermisoPublicacionService).puedeModificar(publicacion)
    );

    expect(permitido).toBe(false);

    http.expectNone(urlPerfil);

    // Un anfitrión tampoco entra por la ruta de administración

    rolActual = 'Anfitrión';

    expect(destino(ejecutarGuard('admin/alojamientos/:id/editar'))).toBe('/home');

    // Sin sesión, al login

    rolActual = null;

    expect(destino(ejecutarGuard('host/alojamientos/:id/editar'))).toBe('/login');
  });

  it('CA-114 muestra un mensaje claro y no ofrece la edición cuando el servidor deniega el acceso', async () => {

    componente = await harness.navigateByUrl(
      '/host/alojamientos/41/editar',
      EditarPublicacion
    );

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

    expect(elemento().querySelector('[data-servicios-adicionales]')).toBeNull();

    expect(alerta()).toBe(MENSAJE_SIN_PERMISO);

    componente.guardar();

    http.expectNone(req => req.method === 'PUT');

    http.expectNone(urlPerfil);
  });
});
