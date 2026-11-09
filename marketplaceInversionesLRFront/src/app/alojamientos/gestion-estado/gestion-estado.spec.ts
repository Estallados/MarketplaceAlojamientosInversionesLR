import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { GestionEstado } from './gestion-estado';
import { EditarPublicacion } from '../editar-publicacion/editar-publicacion';
import { Availability } from '../availability/availability';
import { AlojamientoRespuesta, EstadoAlojamiento } from '../models/alojamiento';
import {
  ESTADOS_ALOJAMIENTO,
  accionesPermitidas,
  aceptaNuevasReservas,
  esEstadoAlojamiento,
  presentacionEstado
} from '../models/estado-publicacion';
import { API_URL } from '../../core/services/api-url';
import { RolUsuario, SessionService } from '../../core/services/session.service';
import { routes } from '../../app.routes';

// ======================================
// HU-17 ENVIAR A REVISIÓN Y GESTIONAR EL ESTADO
// Se prueba la pantalla real de edición (y la de disponibilidad) con el
// servicio HTTP real; solo el backend se sustituye con HttpTestingController.
// ======================================

describe('Estados de la publicación (HU-17)', () => {

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

  /** Publicación completa del anfitrión 7: puede enviarse a revisión. */
  function publicacion(
    estado: EstadoAlojamiento,
    id = 41,
    cambios: Partial<AlojamientoRespuesta> = {}
  ): AlojamientoRespuesta {

    return {
      idAlojamiento: id,
      idAnfitrion: 7,
      estado,
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
      imagenes: ['/uploads/casa.jpg'],
      serviciosAdicionales: [],
      ...cambios
    };
  }

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

  /** Abre la edición como anfitrión propietario y responde detalle, perfil y políticas. */
  async function abrirComoPropietario(
    alojamiento: AlojamientoRespuesta
  ): Promise<void> {

    const id = alojamiento.idAlojamiento;

    await harness.navigateByUrl(`/host/alojamientos/${id}/editar`, EditarPublicacion);

    http.expectOne(`${url}/${id}`).flush(alojamiento);
    await estable();

    http.expectOne(urlPerfil).flush({ idUsuario: 7, rol: 'ANFITRION' });
    await estable();

    http.expectOne(`${url}/politicas`).flush([
      { idPolitica: 1, nombre: 'Flexible', condiciones: 'Reembolso total' }
    ]);
    await estable();
  }

  async function pulsar(selector: string): Promise<void> {
    boton(selector)!.click();
    await estable();
  }

  function boton(selector: string): HTMLButtonElement | null {
    return elemento().querySelector<HTMLButtonElement>(selector);
  }

  function insignia(): HTMLElement {
    return elemento().querySelector<HTMLElement>('[data-estado]')!;
  }

  function texto(selector: string): string {
    return elemento().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function gestion(): GestionEstado {
    return harness.fixture.debugElement
      .query(By.directive(GestionEstado))
      .componentInstance as GestionEstado;
  }

  /** Botones de transición visibles. */
  function accionesVisibles(): string[] {
    return ['data-enviar-revision', 'data-pausar', 'data-reanudar']
      .filter(atributo => boton(`[${atributo}]`) !== null);
  }

  // ======================================
  // CA-104 ESTADOS PERMITIDOS
  // ======================================

  const ESPERADO: Record<EstadoAlojamiento, { etiqueta: string; acciones: string[] }> = {
    BORRADOR: { etiqueta: 'Borrador', acciones: ['data-enviar-revision'] },
    PENDIENTE_REVISION: { etiqueta: 'Pendiente de revisión', acciones: [] },
    ACTIVA: { etiqueta: 'Activa', acciones: ['data-pausar'] },
    PAUSADA: { etiqueta: 'Pausada', acciones: ['data-reanudar'] },
    BLOQUEADA: { etiqueta: 'Bloqueada', acciones: [] }
  };

  it('CA-104 define únicamente los cinco estados del contrato, cada uno con una insignia distinta', () => {

    expect([...ESTADOS_ALOJAMIENTO]).toEqual(
      ['BORRADOR', 'PENDIENTE_REVISION', 'ACTIVA', 'PAUSADA', 'BLOQUEADA']
    );

    const clases = ESTADOS_ALOJAMIENTO.map(e => presentacionEstado(e).clases);
    expect(new Set(clases).size).toBe(5);

    // Las transiciones del anfitrión según alojamientos.yaml.
    expect(ESTADOS_ALOJAMIENTO.map(e => accionesPermitidas(e))).toEqual([
      ['enviarRevision'], [], ['pausar'], ['reanudar'], []
    ]);
  });

  it.each([...ESTADOS_ALOJAMIENTO])(
    'CA-104 muestra la insignia del estado %s sin alterar el valor del servidor y solo sus transiciones',
    async estado => {

      await abrirComoPropietario(publicacion(estado));

      // La etiqueta es amigable, pero el valor del servidor no se modifica.
      expect(insignia().textContent?.trim()).toBe(ESPERADO[estado].etiqueta);
      expect(insignia().getAttribute('data-valor')).toBe(estado);

      expect(accionesVisibles()).toEqual(ESPERADO[estado].acciones);
    }
  );

  it('CA-104 muestra un estado desconocido como no reconocido y no ofrece acciones sobre él', async () => {

    const desconocido = 'ELIMINADA' as EstadoAlojamiento;

    expect(esEstadoAlojamiento(desconocido)).toBe(false);
    expect(esEstadoAlojamiento('activa')).toBe(false);
    expect(accionesPermitidas(desconocido)).toEqual([]);

    await abrirComoPropietario(publicacion(desconocido));

    expect(insignia().textContent?.trim()).toBe('Estado no reconocido');

    // No se presenta como otro estado ni se reescribe el valor recibido.
    expect(insignia().getAttribute('data-valor')).toBe('ELIMINADA');
    expect(accionesVisibles()).toEqual([]);
    expect(elemento().querySelector('[data-disponible]')).toBeNull();
  });

  it('CA-104 no permite pedir una transición incompatible con el estado actual ni al administrador', async () => {

    await abrirComoPropietario(publicacion('BORRADOR'));

    // Aunque se invoque directamente, pausar o reanudar un BORRADOR no envía nada.
    gestion().solicitarPausa();
    gestion().confirmarPausa();
    gestion().reanudar();
    await estable();

    http.expectNone(`${url}/41/pausar`);
    http.expectNone(`${url}/41/reanudar`);
    expect(texto('[data-error-estado]')).toContain('no está disponible para una publicación en estado "Borrador"');
    expect(insignia().getAttribute('data-valor')).toBe('BORRADOR');

    // El administrador ve el estado, pero las transiciones son del anfitrión (403 en el backend).
    rolActual = 'Administrador';

    await harness.navigateByUrl('/admin/alojamientos/42/editar', EditarPublicacion);
    http.expectOne(`${url}/42`).flush(publicacion('ACTIVA', 42, { idAnfitrion: 99 }));
    await estable();
    http.expectOne(`${url}/politicas`).flush([]);
    await estable();

    expect(insignia().textContent?.trim()).toBe('Activa');
    expect(accionesVisibles()).toEqual([]);
    expect(elemento().querySelector('[data-solo-anfitrion]')).not.toBeNull();
  });

  // ======================================
  // CA-108 PAUSAR PUBLICACIÓN
  // ======================================

  it('CA-108 pausa una publicación ACTIVA tras confirmar y la muestra PAUSADA sin disponibilidad para nuevas reservas', async () => {

    await abrirComoPropietario(publicacion('ACTIVA'));

    expect(elemento().querySelector('[data-disponible]')).not.toBeNull();

    // Pide confirmación antes de enviar nada.
    await pulsar('[data-pausar]');

    expect(elemento().querySelector('[data-confirmar-pausa]')).not.toBeNull();
    expect(texto('[data-confirmar-pausa]')).toContain('no recibirá nuevas reservas');
    http.expectNone(`${url}/41/pausar`);

    await pulsar('[data-confirmar-pausa-si]');

    const peticion = http.expectOne(`${url}/41/pausar`);

    expect(peticion.request.method).toBe('POST');

    // Mientras responde el servidor: sin doble envío y sin cambio optimista.
    expect(boton('[data-confirmar-pausa-si]')!.disabled).toBe(true);
    gestion().confirmarPausa();
    http.expectNone(`${url}/41/pausar`);
    expect(insignia().getAttribute('data-valor')).toBe('ACTIVA');

    peticion.flush(publicacion('PAUSADA'));
    await estable();

    expect(insignia().textContent?.trim()).toBe('Pausada');
    expect(insignia().getAttribute('data-valor')).toBe('PAUSADA');
    expect(texto('[data-mensaje-estado]')).toContain('quedó pausada');
    expect(texto('[data-no-disponible]')).toContain('no está disponible para nuevas reservas');
    expect(elemento().querySelector('[data-disponible]')).toBeNull();
    expect(accionesVisibles()).toEqual(['data-reanudar']);
  });

  it('CA-108 cancelar la confirmación no pausa la publicación', async () => {

    await abrirComoPropietario(publicacion('ACTIVA'));

    await pulsar('[data-pausar]');
    await pulsar('[data-confirmar-pausa-no]');

    http.expectNone(`${url}/41/pausar`);
    expect(elemento().querySelector('[data-confirmar-pausa]')).toBeNull();
    expect(insignia().getAttribute('data-valor')).toBe('ACTIVA');
  });

  it('CA-108 muestra el error del servidor y vuelve a consultar el estado real cuando la pausa no se permite', async () => {

    await abrirComoPropietario(publicacion('ACTIVA'));

    await pulsar('[data-pausar]');
    await pulsar('[data-confirmar-pausa-si]');

    // Otro usuario ya la había pausado: el servidor rechaza la transición.
    http.expectOne(`${url}/41/pausar`).flush(
      { message: 'No se puede pasar de PAUSADA a PAUSADA.' },
      { status: 409, statusText: 'Conflict' }
    );
    await estable();

    expect(texto('[data-error-estado]')).toContain('No se puede pasar de PAUSADA a PAUSADA.');

    // Se sincroniza con el estado que tiene el servidor.
    const recarga = http.expectOne(`${url}/41`);
    expect(recarga.request.method).toBe('GET');
    recarga.flush(publicacion('PAUSADA'));
    await estable();

    expect(insignia().getAttribute('data-valor')).toBe('PAUSADA');
    expect(accionesVisibles()).toEqual(['data-reanudar']);
  });

  it('CA-108 trata el 403 del servidor como falta de permiso y retira la gestión', async () => {

    await abrirComoPropietario(publicacion('ACTIVA'));

    await pulsar('[data-pausar]');
    await pulsar('[data-confirmar-pausa-si]');

    http.expectOne(`${url}/41/pausar`).flush(
      { message: 'Acceso denegado', status: 403 },
      { status: 403, statusText: 'Forbidden' }
    );
    await estable();

    expect(elemento().querySelector('[data-gestion-estado]')).toBeNull();
    expect(texto('[role="alert"]')).toContain('Solo el anfitrión propietario');
  });

  it('CA-108 reanuda una publicación PAUSADA con el endpoint real y muestra el estado que devuelve el servidor', async () => {

    await abrirComoPropietario(publicacion('PAUSADA'));

    await pulsar('[data-reanudar]');

    const peticion = http.expectOne(`${url}/41/reanudar`);
    expect(peticion.request.method).toBe('POST');

    peticion.flush(publicacion('ACTIVA'));
    await estable();

    expect(insignia().getAttribute('data-valor')).toBe('ACTIVA');
    expect(texto('[data-mensaje-estado]')).toContain('Estado actual: Activa');
    expect(accionesVisibles()).toEqual(['data-pausar']);
  });

  // ======================================
  // CA-157 ENVIAR A REVISIÓN
  // ======================================

  it('CA-157 envía a revisión una publicación completa y la deja PENDIENTE_REVISION sin activarla', async () => {

    await abrirComoPropietario(publicacion('BORRADOR'));

    await pulsar('[data-enviar-revision]');

    const peticion = http.expectOne(`${url}/41/enviarrevision`);

    expect(peticion.request.method).toBe('POST');
    expect(boton('[data-enviar-revision]')!.disabled).toBe(true);
    expect(insignia().getAttribute('data-valor')).toBe('BORRADOR');

    peticion.flush(publicacion('PENDIENTE_REVISION'));
    await estable();

    expect(insignia().textContent?.trim()).toBe('Pendiente de revisión');
    expect(insignia().getAttribute('data-valor')).toBe('PENDIENTE_REVISION');
    expect(texto('[data-mensaje-estado]')).toContain('Quedará activa cuando un administrador la apruebe');

    // No se activa: ni disponible para reservas ni acciones de pausa.
    expect(elemento().querySelector('[data-disponible]')).toBeNull();
    expect(accionesVisibles()).toEqual([]);
  });

  it('CA-157 indica los datos que faltan y no envía una publicación incompleta', async () => {

    await abrirComoPropietario(
      publicacion('BORRADOR', 41, {
        descripcion: '',
        idPolitica: undefined,
        imagenes: [],
        horaSalida: undefined
      })
    );

    await pulsar('[data-enviar-revision]');

    http.expectNone(`${url}/41/enviarrevision`);

    const faltantes = Array.from(
      elemento().querySelectorAll('[data-faltantes-revision] li')
    ).map(li => li.textContent?.trim());

    expect(faltantes).toEqual([
      'descripción',
      'hora de salida',
      'política de cancelación',
      'al menos una imagen'
    ]);

    expect(texto('[data-error-estado]')).toContain('Completa los datos antes de enviar a revisión');
    expect(insignia().getAttribute('data-valor')).toBe('BORRADOR');
  });

  it('CA-157 muestra lo que falta según el servidor cuando solo el backend puede comprobarlo', async () => {

    await abrirComoPropietario(publicacion('BORRADOR'));

    await pulsar('[data-enviar-revision]');

    http.expectOne(`${url}/41/enviarrevision`).flush(
      { message: 'Completa los datos antes de enviar a revisión. Falta: fechas de disponibilidad desde hoy.' },
      { status: 409, statusText: 'Conflict' }
    );
    await estable();

    expect(texto('[data-error-estado]')).toContain('Falta: fechas de disponibilidad desde hoy.');

    http.expectOne(`${url}/41`).flush(publicacion('BORRADOR'));
    await estable();

    expect(insignia().getAttribute('data-valor')).toBe('BORRADOR');
    expect(boton('[data-enviar-revision]')!.disabled).toBe(false);
  });

  it('CA-157 pide guardar los cambios del formulario antes de enviar a revisión', async () => {

    await abrirComoPropietario(publicacion('BORRADOR'));

    const titulo = elemento().querySelector<HTMLInputElement>('#titulo')!;
    titulo.value = 'Casa campestre renovada';
    titulo.dispatchEvent(new Event('input'));
    await estable();

    await pulsar('[data-enviar-revision]');

    http.expectNone(`${url}/41/enviarrevision`);
    expect(texto('[data-error-estado]')).toContain('Guarda los cambios');
  });

  // ======================================
  // CA-159 PUBLICACIONES NO DISPONIBLES
  // ======================================

  it('CA-159 solo una publicación ACTIVA se considera disponible para nuevas reservas', () => {

    expect(ESTADOS_ALOJAMIENTO.filter(e => aceptaNuevasReservas(e))).toEqual(['ACTIVA']);
    expect(aceptaNuevasReservas('PAUSADA')).toBe(false);
    expect(aceptaNuevasReservas('BLOQUEADA')).toBe(false);
    expect(aceptaNuevasReservas('DESCONOCIDO')).toBe(false);
  });

  it('CA-159 muestra una publicación PAUSADA como no disponible para nuevas reservas', async () => {

    await abrirComoPropietario(publicacion('PAUSADA'));

    expect(texto('[data-no-disponible]')).toContain('no está disponible para nuevas reservas');
    expect(elemento().querySelector('[data-disponible]')).toBeNull();

    // La única acción coherente es reanudar.
    expect(accionesVisibles()).toEqual(['data-reanudar']);
  });

  it('CA-159 muestra una publicación BLOQUEADA como no disponible y no ofrece reanudarla ni pausarla', async () => {

    await abrirComoPropietario(publicacion('BLOQUEADA'));

    expect(insignia().textContent?.trim()).toBe('Bloqueada');
    expect(texto('[data-no-disponible]')).toContain('no está disponible para nuevas reservas');
    expect(texto('[data-descripcion-estado]')).toContain('no se puede reanudar');
    expect(elemento().querySelector('[data-disponible]')).toBeNull();
    expect(accionesVisibles()).toEqual([]);
  });

  it('CA-159 la pantalla de disponibilidad advierte que una publicación pausada o bloqueada no acepta nuevas reservas', async () => {

    const casos: [EstadoAlojamiento, number, boolean][] = [
      ['PAUSADA', 51, true],
      ['BLOQUEADA', 52, true],
      ['ACTIVA', 53, false]
    ];

    for (const [estado, id, aviso] of casos) {

      await harness.navigateByUrl(`/host/alojamientos/${id}/disponibilidad`, Availability);

      http.expectOne(`${url}/${id}`).flush(publicacion(estado, id));
      http.expectOne(`${url}/${id}/disponibilidad`).flush({
        idAlojamiento: id,
        estadoAlojamiento: estado,
        periodos: [],
        reservados: []
      });
      await estable();

      expect(insignia().getAttribute('data-valor')).toBe(estado);

      if (aviso) {
        expect(texto('[data-no-disponible]')).toContain('no está disponible para nuevas reservas');
      } else {
        expect(elemento().querySelector('[data-no-disponible]')).toBeNull();
      }
    }
  });
});
