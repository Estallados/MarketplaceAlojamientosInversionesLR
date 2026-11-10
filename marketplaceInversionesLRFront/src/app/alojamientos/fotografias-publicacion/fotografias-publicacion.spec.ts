import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  TestRequest,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { FotografiasPublicacion } from './fotografias-publicacion';
import { ImagenAlojamiento } from '../models/alojamiento';
import { API_URL } from '../../core/services/api-url';

// ======================================
// HU-16 EDITAR LAS FOTOGRAFÍAS DE LA PUBLICACIÓN
// Componente real con el servicio real; solo el backend se sustituye con
// HttpTestingController.
// ======================================

describe('FotografiasPublicacion (HU-16)', () => {

  const baseUrl = 'http://localhost:8081';

  const url = `${baseUrl}/alojamientos/41/imagenes`;

  let http: HttpTestingController;

  let fixture: ComponentFixture<FotografiasPublicacion>;

  let emitidas: ImagenAlojamiento[][];

  let revocado: string[];

  const foto = (idImagen: number, principal = false): ImagenAlojamiento => ({
    idImagen,
    idAlojamiento: 41,
    url: `https://res.cloudinary.com/demo/image/upload/${idImagen}.jpg`,
    principal
  });

  const actuales = [foto(1, true), foto(2), foto(3)];

  async function crear(imagenes: ImagenAlojamiento[] = actuales): Promise<void> {

    await TestBed.configureTestingModule({
      imports: [FotografiasPublicacion],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_URL, useValue: baseUrl }
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(FotografiasPublicacion);
    fixture.componentRef.setInput('idAlojamiento', 41);
    fixture.componentRef.setInput('imagenes', imagenes);

    emitidas = [];
    revocado = [];
    fixture.componentInstance.imagenesCambiadas.subscribe(lista => {
      emitidas.push(lista);
      // Como el padre: la lista del backend vuelve a entrar.
      fixture.componentRef.setInput('imagenes', lista);
    });
    fixture.componentInstance.accesoRevocado.subscribe(motivo => revocado.push(motivo));

    await estable();
  }

  afterEach(() => {
    http.verify();
  });

  // ======================================
  // UTILIDADES
  // ======================================

  function elemento(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function existe(selector: string): boolean {
    return elemento().querySelector(selector) !== null;
  }

  function texto(selector: string): string {
    return elemento().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function tarjeta(idImagen: number): HTMLElement {
    return elemento().querySelector<HTMLElement>(`[data-foto][data-id="${idImagen}"]`)!;
  }

  async function clic(raiz: ParentNode, selector: string): Promise<void> {
    raiz.querySelector<HTMLButtonElement>(selector)!.click();
    await estable();
  }

  async function elegirArchivos(archivos: File[]): Promise<void> {
    const input = elemento().querySelector<HTMLInputElement>('[data-archivo]')!;
    Object.defineProperty(input, 'files', { value: archivos, configurable: true });
    input.dispatchEvent(new Event('change'));
    await estable();
  }

  function archivo(nombre: string, tipo: string, tamanio = 1024): File {
    const f = new File([new Uint8Array(8)], nombre, { type: tipo });
    Object.defineProperty(f, 'size', { value: tamanio });
    return f;
  }

  function rechazar(req: TestRequest, status: number, message: string): void {
    req.flush({ message }, { status, statusText: 'Error' });
  }

  // ======================================
  // CA-100 EDITAR LAS FOTOGRAFÍAS
  // ======================================

  it('CA-100 muestra las fotografías actuales con la principal marcada, como el original', async () => {

    await crear();

    expect(elemento().querySelectorAll('[data-foto]').length).toBe(3);
    expect(tarjeta(1).querySelector('img')!.getAttribute('src')).toBe(actuales[0].url);
    expect(texto('[data-foto][data-id="1"] [data-principal]')).toBe('Fotografía principal');
    expect(texto('[data-foto][data-id="2"] [data-principal]')).toBe('Seleccionar como principal');
    expect(existe('[data-agregar-foto]')).toBe(true);
  });

  it('CA-100 cambia la fotografía principal con PATCH y muestra el orden que devuelve el backend', async () => {

    await crear();

    // Pulsar la que ya es principal no hace nada.
    await clic(tarjeta(1), '[data-principal]');
    http.expectNone(r => r.method === 'PATCH');

    await clic(tarjeta(3), '[data-principal]');

    const req = http.expectOne(`${url}/3/principal`);
    expect(req.request.method).toBe('PATCH');
    req.flush([foto(3, true), foto(1), foto(2)]);
    await estable();

    expect(emitidas).toEqual([[foto(3, true), foto(1), foto(2)]]);
    expect(texto('[data-foto][data-id="3"] [data-principal]')).toBe('Fotografía principal');
    expect(texto('[data-mensaje-fotos]')).toBe('Fotografía principal actualizada.');
  });

  it('CA-100 pide confirmación y elimina la imagen con DELETE', async () => {

    await crear();

    await clic(tarjeta(2), '[data-eliminar-foto]');
    expect(existe('[data-modal-eliminar-foto]')).toBe(true);
    http.expectNone(r => r.method === 'DELETE');

    await clic(elemento(), '[data-confirmar-eliminar]');

    const req = http.expectOne(`${url}/2`);
    expect(req.request.method).toBe('DELETE');
    req.flush([foto(1, true), foto(3)]);
    await estable();

    expect(existe('[data-modal-eliminar-foto]')).toBe(false);
    expect(elemento().querySelectorAll('[data-foto]').length).toBe(2);
    expect(texto('[data-mensaje-fotos]')).toBe('Imagen eliminada.');
  });

  it('CA-100 no ofrece eliminar la única imagen, porque el backend exige conservar al menos una', async () => {

    await crear([foto(1, true)]);

    expect(existe('[data-eliminar-foto]')).toBe(false);
  });

  it('CA-100 muestra el rechazo del backend al eliminar (409)', async () => {

    await crear();

    await clic(tarjeta(2), '[data-eliminar-foto]');
    await clic(elemento(), '[data-confirmar-eliminar]');
    rechazar(http.expectOne(`${url}/2`), 409, 'El alojamiento debe conservar al menos una imagen.');
    await estable();

    expect(texto('[data-error-fotos]')).toBe('El alojamiento debe conservar al menos una imagen.');
    expect(existe('[data-modal-eliminar-foto]')).toBe(false);
    expect(emitidas).toEqual([]);
  });

  it('CA-100 sube imágenes válidas con POST multipart en el campo archivos', async () => {

    await crear();

    const nueva = archivo('terraza.png', 'image/png');
    await elegirArchivos([nueva]);

    const req = http.expectOne(url);
    expect(req.request.method).toBe('POST');
    const cuerpo = req.request.body as FormData;
    expect(cuerpo.getAll('archivos').length).toBe(1);
    expect((cuerpo.get('archivos') as File).name).toBe('terraza.png');

    req.flush([...actuales, foto(4)], { status: 201, statusText: 'Created' });
    await estable();

    expect(elemento().querySelectorAll('[data-foto]').length).toBe(4);
    expect(texto('[data-mensaje-fotos]')).toBe('Imagen agregada.');
  });

  it('CA-100 rechaza sin llamar al backend archivos de otro formato, de más de 5 MB o que superen 15 imágenes', async () => {

    await crear();

    await elegirArchivos([archivo('plano.pdf', 'application/pdf')]);
    expect(texto('[data-error-fotos]')).toBe('La fotografía plano.pdf no es válida. Usa JPG, PNG o WEBP de máximo 5 MB.');

    await elegirArchivos([archivo('grande.jpg', 'image/jpeg', 6 * 1024 * 1024)]);
    expect(texto('[data-error-fotos]')).toContain('grande.jpg no es válida');

    const muchas = Array.from({ length: 13 }, (_, i) => archivo(`f${i}.jpg`, 'image/jpeg'));
    await elegirArchivos(muchas);
    expect(texto('[data-error-fotos]')).toBe('Puedes cargar máximo 15 fotografías por alojamiento.');

    http.expectNone(url);
  });

  it('CA-100 con 15 imágenes ya no ofrece agregar más', async () => {

    await crear(Array.from({ length: 15 }, (_, i) => foto(i + 1, i === 0)));

    expect(existe('[data-agregar-foto]')).toBe(false);
  });

  it('CA-100 informa cuando el backend no puede guardar la imagen (503)', async () => {

    await crear();

    await elegirArchivos([archivo('sala.webp', 'image/webp')]);
    rechazar(http.expectOne(url), 503, 'No se pudo guardar la imagen. Intenta de nuevo.');
    await estable();

    expect(texto('[data-error-fotos]')).toBe('No se pudo guardar la imagen. Intenta de nuevo.');
    expect(elemento().querySelectorAll('[data-foto]').length).toBe(3);
  });

  // ======================================
  // CA-101 RESTRICCIÓN PARA NO AUTORIZADOS
  // ======================================

  it('CA-101 avisa al formulario cuando el backend rechaza el cambio de imágenes (403 o 401)', async () => {

    await crear();

    await clic(tarjeta(2), '[data-principal]');
    rechazar(http.expectOne(`${url}/2/principal`), 403, 'Sin permiso');
    await estable();

    await elegirArchivos([archivo('a.jpg', 'image/jpeg')]);
    rechazar(http.expectOne(url), 401, 'No autenticado');
    await estable();

    expect(revocado).toEqual(['denegado', 'sin-sesion']);
    expect(emitidas).toEqual([]);
  });
});
