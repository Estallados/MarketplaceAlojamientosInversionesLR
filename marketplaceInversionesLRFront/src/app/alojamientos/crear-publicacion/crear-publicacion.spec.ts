import { TestBed, ComponentFixture } from '@angular/core/testing';
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

import { CrearPublicacion } from './crear-publicacion';
import { API_URL } from '../../services/api-url';
import { RolUsuario, SessionService } from '../../services/session.service';
import { roleGuard } from '../../guards/role.guard';
import { routes } from '../../app.routes';

// ======================================
// HU-15 CREAR PUBLICACIÓN DE ALOJAMIENTO
// ======================================

describe('CrearPublicacion (HU-15)', () => {

  const baseUrl = 'http://localhost:8081';

  const url = `${baseUrl}/alojamientos`;

  let rolActual: RolUsuario | null = 'Anfitrión';

  /** Sesión de prueba: solo decide el rol con el que entra el usuario. */
  const sesion = {
    autenticado: () => rolActual !== null,
    tieneRol: (...roles: RolUsuario[]) =>
      rolActual !== null && roles.includes(rolActual),
    obtenerToken: () => null
  };

  let fixture: ComponentFixture<CrearPublicacion>;

  let http: HttpTestingController;

  let elemento: HTMLElement;

  beforeEach(async () => {

    rolActual = 'Anfitrión';

    await TestBed.configureTestingModule({
      imports: [CrearPublicacion],
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl },
        { provide: SessionService, useValue: sesion }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(CrearPublicacion);

    http = TestBed.inject(HttpTestingController);

    elemento = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();

    // Políticas de cancelación al iniciar

    http.expectOne(`${url}/politicas`).flush([
      { idPolitica: 1, nombre: 'Flexible', condiciones: 'Reembolso total hasta 1 día antes' }
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

  async function escribir(
    id: string,
    valor: string
  ): Promise<void> {

    const campo =
      elemento.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;

    campo.value = valor;

    campo.dispatchEvent(new Event('input'));

    campo.dispatchEvent(new Event('blur'));

    await estable();

  }

  async function seleccionar(
    id: string,
    indice: number
  ): Promise<void> {

    const select =
      elemento.querySelector<HTMLSelectElement>(`#${id}`)!;

    select.selectedIndex = indice;

    select.dispatchEvent(new Event('change'));

    await estable();

  }

  async function pulsar(selector: string): Promise<void> {

    elemento
      .querySelector<HTMLButtonElement>(selector)!
      .click();

    await estable();

  }

  function errorCampo(campo: string): string | null {
    return elemento
      .querySelector(`[data-error="${campo}"]`)
      ?.textContent?.trim() ?? null;
  }

  function alerta(): string {
    return elemento.querySelector('[role="alert"]')?.textContent ?? '';
  }

  function pasoActual(): string {
    return elemento.querySelector('h2')?.textContent?.trim() ?? '';
  }

  function imagen(nombre = 'casa.jpg'): File {
    return new File(['imagen'], nombre, { type: 'image/jpeg' });
  }

  /** Selecciona archivos en el input de fotografías (paso 4). */
  async function subirImagenes(...archivos: File[]): Promise<void> {

    const input =
      elemento.querySelector<HTMLInputElement>('#imagenes')!;

    Object.defineProperty(input, 'files', {
      value: archivos,
      configurable: true
    });

    input.dispatchEvent(new Event('change'));

    await estable();

  }

  function rutaCrear(): ActivatedRouteSnapshot {

    const ruta = routes.find(r => r.path === 'host/alojamientos/crear')!;

    return { data: ruta.data } as unknown as ActivatedRouteSnapshot;
  }

  function ejecutarGuard(): boolean | UrlTree {

    return TestBed.runInInjectionContext(() =>
      roleGuard(rutaCrear(), {} as RouterStateSnapshot)
    ) as boolean | UrlTree;
  }

  // ======================================
  // CA-094 CREACIÓN DE PUBLICACIÓN
  // ======================================

  it('CA-094 permite al ANFITRION crear la publicación con todos sus datos y queda en BORRADOR', async () => {

    // 1. Acceso: solo el rol ANFITRION entra al formulario

    const ruta = routes.find(r => r.path === 'host/alojamientos/crear')!;

    expect(ruta.component).toBe(CrearPublicacion);

    expect(ejecutarGuard()).toBe(true);

    rolActual = 'Huésped';

    const redireccion = ejecutarGuard();

    expect(redireccion instanceof UrlTree).toBe(true);

    expect(
      TestBed.inject(Router).serializeUrl(redireccion as UrlTree)
    ).toBe('/home');

    rolActual = 'Anfitrión';

    // 2. Paso 1: información del alojamiento

    expect(pasoActual()).toBe('Información del alojamiento');

    await escribir('titulo', 'Casa campestre en Villa de Leyva');
    await escribir('ciudad', 'Villa de Leyva');
    await escribir('descripcion', 'Casa amplia con jardín.');
    await escribir('ubicacion', 'Calle 123 #45-67');
    await seleccionar('tipo', 1);
    await escribir('capacidad', '6');
    await escribir('habitaciones', '3');
    await escribir('camas', '4');
    await escribir('banos', '2');
    await escribir('precioNoche', '245000');

    await pulsar('[data-siguiente]');

    // 3. Paso 2: horarios, reglas y política

    expect(pasoActual()).toBe('Horarios y reglas');

    await escribir('horaEntrada', '15:00');
    await escribir('horaSalida', '11:00');
    await escribir('reglas', 'No se permiten fiestas.');
    await seleccionar('idPolitica', 1);

    await pulsar('[data-siguiente]');

    // 4. Paso 3: servicios

    expect(pasoActual()).toBe('Servicios incluidos');

    const wifi =
      elemento.querySelector<HTMLInputElement>('input[type="checkbox"]')!;

    wifi.click();

    await estable();

    // Volver atrás conserva los datos

    await pulsar('[data-anterior]');

    expect(
      elemento.querySelector<HTMLTextAreaElement>('#reglas')!.value
    ).toBe('No se permiten fiestas.');

    await pulsar('[data-siguiente]');

    expect(
      elemento.querySelector<HTMLInputElement>('input[type="checkbox"]')!.checked
    ).toBe(true);

    await pulsar('[data-siguiente]');

    // 5. Paso 4: fotografías

    expect(pasoActual()).toBe('Fotografías del alojamiento');

    await subirImagenes(imagen());

    expect(elemento.querySelectorAll('[data-imagen]').length).toBe(1);

    // 6. Guardar borrador: POST /alojamientos multipart

    await pulsar('[data-guardar-borrador]');

    const crear = http.expectOne(url);

    expect(crear.request.method).toBe('POST');

    const cuerpo = crear.request.body as FormData;

    expect(cuerpo instanceof FormData).toBe(true);

    const datos = JSON.parse(cuerpo.get('datos') as string);

    expect(datos).toEqual({
      titulo: 'Casa campestre en Villa de Leyva',
      descripcion: 'Casa amplia con jardín.',
      ubicacion: 'Calle 123 #45-67',
      ciudad: 'Villa de Leyva',
      tipo: 'Casa',
      capacidad: 6,
      habitaciones: 3,
      camas: 4,
      banos: 2,
      precioNoche: 245000,
      idPolitica: 1,
      horaEntrada: '15:00',
      horaSalida: '11:00',
      reglas: 'No se permiten fiestas.',
      amenidades: ['Wifi']
    });

    // El estado lo asigna el backend

    expect(datos.estado).toBeUndefined();

    expect(cuerpo.getAll('archivos').length).toBe(1);

    crear.flush(
      {
        ...datos,
        idAlojamiento: 41,
        idAnfitrion: 7,
        estado: 'BORRADOR',
        imagenes: ['/uploads/41/casa.jpg']
      },
      { status: 201, statusText: 'Created' }
    );

    await estable();

    // 7. Confirmación y estado BORRADOR

    expect(
      elemento.querySelector('[role="status"]')?.textContent
    ).toContain('Borrador #41 guardado correctamente.');

    expect(
      elemento.querySelector('[data-estado]')?.textContent?.trim()
    ).toBe('BORRADOR');

    expect(elemento.textContent).toContain(
      '1 fotografía(s) guardada(s) en el servidor.'
    );

    // 8. Guardar otra vez actualiza el mismo borrador (no se duplica)

    await pulsar('[data-guardar-borrador]');

    http.expectNone(r => r.method === 'POST' && r.url === url);

    const actualizar = http.expectOne(`${url}/41`);

    expect(actualizar.request.method).toBe('PUT');

    actualizar.flush({
      ...datos,
      idAlojamiento: 41,
      idAnfitrion: 7,
      estado: 'BORRADOR'
    });

    await estable();

    expect(
      elemento.querySelector('[data-estado]')?.textContent?.trim()
    ).toBe('BORRADOR');

  });

  // ======================================
  // CA-097 INFORMACIÓN MÍNIMA
  // ======================================

  it('CA-097 impide enviar a revisión sin la información mínima e indica los datos faltantes', async () => {

    // Solo título e imagen: suficiente para borrador, no para revisión

    await escribir('titulo', 'Apartamento en Chapinero');

    await pulsar('ol li:nth-child(4) button');

    await subirImagenes(imagen());

    await pulsar('[data-enviar-revision]');

    // No se envía nada al backend

    http.expectNone(url);

    http.expectNone(r => r.url.endsWith('/enviarrevision'));

    // Mensaje claro con los datos faltantes

    expect(alerta()).toContain(
      'Para enviar a revisión falta: descripción, ubicación, ciudad, ' +
      'tipo de alojamiento, capacidad, habitaciones, baños, precio por noche, ' +
      'hora de entrada, hora de salida, reglas, política de cancelación.'
    );

    const pendientes = Array.from(
      elemento.querySelectorAll('[data-faltantes] li')
    ).map(li => li.textContent ?? '');

    expect(pendientes.length).toBe(12);

    expect(pendientes[0]).toContain('descripción');

    expect(pendientes[0]).toContain('Ir al paso 1');

    expect(
      pendientes.find(p => p.includes('política de cancelación'))
    ).toContain('Ir al paso 2');

    // Regresar al paso correspondiente conservando lo escrito

    const irPaso1 = Array.from(
      elemento.querySelectorAll<HTMLButtonElement>('[data-faltantes] button')
    )[0];

    irPaso1.click();

    await estable();

    expect(pasoActual()).toBe('Información del alojamiento');

    expect(
      elemento.querySelector<HTMLInputElement>('#titulo')!.value
    ).toBe('Apartamento en Chapinero');

    expect(fixture.componentInstance.imagenesSeleccionadas.length).toBe(1);

    // Sin título tampoco se puede guardar el borrador

    await escribir('titulo', '');

    await pulsar('[data-guardar-borrador]');

    http.expectNone(url);

    expect(errorCampo('titulo')).toBe(
      'Debes indicar el título del alojamiento.'
    );

  });

  it('CA-097 muestra los datos faltantes que informa el backend al enviar a revisión', async () => {

    const c = fixture.componentInstance;

    c.draft = {
      ...c.draft,
      titulo: 'Cabaña',
      descripcion: 'Cabaña en el bosque',
      ubicacion: 'Km 5 vía La Calera',
      ciudad: 'La Calera',
      tipo: 'Cabaña',
      capacidad: 4,
      habitaciones: 2,
      banos: 1,
      precioNoche: 180000,
      horaEntrada: '14:00',
      horaSalida: '12:00',
      reglas: 'Sin mascotas',
      idPolitica: 1
    };

    await pulsar('ol li:nth-child(4) button');

    await subirImagenes(imagen());

    await pulsar('[data-enviar-revision]');

    http.expectOne(url).flush(
      { idAlojamiento: 9, idAnfitrion: 7, estado: 'BORRADOR', titulo: 'Cabaña' },
      { status: 201, statusText: 'Created' }
    );

    http.expectOne(`${url}/9/enviarrevision`).flush(
      { message: 'Completa los datos antes de enviar a revisión. Falta: al menos una imagen.' },
      { status: 409, statusText: 'Conflict' }
    );

    await estable();

    expect(alerta()).toContain(
      'Completa los datos antes de enviar a revisión. Falta: al menos una imagen.'
    );

    expect(alerta()).toContain('El borrador #9 se conserva.');

    expect(
      elemento.querySelector('[data-estado]')?.textContent?.trim()
    ).toBe('BORRADOR');

  });

  it('CA-094 confirma el borrador creado aunque el envío a revisión no esté disponible en el servidor', async () => {

    const c = fixture.componentInstance;

    c.draft = {
      ...c.draft,
      titulo: 'Finca en Anapoima',
      descripcion: 'Finca con piscina',
      ubicacion: 'Vereda El Rosario',
      ciudad: 'Anapoima',
      tipo: 'Finca',
      capacidad: 10,
      habitaciones: 4,
      banos: 3,
      precioNoche: 600000,
      horaEntrada: '15:00',
      horaSalida: '11:00',
      reglas: 'Sin fiestas',
      idPolitica: 1
    };

    await pulsar('ol li:nth-child(4) button');

    await subirImagenes(imagen());

    await pulsar('[data-enviar-revision]');

    http.expectOne(url).flush(
      { idAlojamiento: 9, idAnfitrion: 7, estado: 'BORRADOR', titulo: 'Finca en Anapoima' },
      { status: 201, statusText: 'Created' }
    );

    http.expectOne(`${url}/9/enviarrevision`).flush(
      { message: 'La ruta solicitada no existe.' },
      { status: 404, statusText: 'Not Found' }
    );

    await estable();

    expect(
      elemento.querySelector('[role="status"]')?.textContent
    ).toContain('Borrador #9 guardado correctamente.');

    expect(alerta()).toContain(
      'El borrador #9 está guardado, pero no se pudo enviar a revisión'
    );

    expect(alerta()).not.toContain('La ruta solicitada no existe.');

    expect(
      elemento.querySelector('[data-estado]')?.textContent?.trim()
    ).toBe('BORRADOR');

  });

  // ======================================
  // CA-098 CAPACIDAD Y PRECIO
  // ======================================

  it('CA-098 rechaza capacidad y precio iguales o menores que cero', async () => {

    await escribir('titulo', 'Casa en Melgar');

    // Capacidad igual a cero

    await escribir('capacidad', '0');

    expect(errorCampo('capacidad')).toBe(
      'La capacidad debe ser un número entero mayor que cero.'
    );

    // Capacidad negativa

    await escribir('capacidad', '-3');

    expect(errorCampo('capacidad')).toBe(
      'La capacidad debe ser un número entero mayor que cero.'
    );

    // Capacidad no entera

    await escribir('capacidad', '2.5');

    expect(errorCampo('capacidad')).toBe(
      'La capacidad debe ser un número entero mayor que cero.'
    );

    // Precio igual a cero

    await escribir('precioNoche', '0');

    expect(errorCampo('precioNoche')).toBe(
      'El precio por noche debe ser mayor que cero.'
    );

    // Precio negativo

    await escribir('precioNoche', '-100');

    expect(errorCampo('precioNoche')).toBe(
      'El precio por noche debe ser mayor que cero.'
    );

    // No deja avanzar con valores inválidos

    await pulsar('[data-siguiente]');

    expect(pasoActual()).toBe('Información del alojamiento');

    expect(alerta()).toContain(
      'La capacidad debe ser un número entero mayor que cero.'
    );

    // Tampoco al guardar: no se envía nada

    fixture.componentInstance.agregarImagenes([imagen()]);

    await pulsar('[data-guardar-borrador]');

    http.expectNone(url);

    expect(pasoActual()).toBe('Información del alojamiento');

    // Con valores mayores que cero desaparecen los errores

    await escribir('capacidad', '4');

    await escribir('precioNoche', '150000');

    expect(errorCampo('capacidad')).toBeNull();

    expect(errorCampo('precioNoche')).toBeNull();

    await pulsar('[data-siguiente]');

    expect(pasoActual()).toBe('Horarios y reglas');

  });

});
