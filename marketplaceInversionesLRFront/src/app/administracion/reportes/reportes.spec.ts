import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';

import { ReportesAdmin } from './reportes';
import { API_URL } from '../../core/services/api-url';
import { PaginaExportaciones } from '../models/reporte';
import { routes } from '../../app.routes';

/**
 * HU-05 Exportar reportes de gestión en PDF o Excel (SCRUM-260).
 */
describe('ReportesAdmin (HU-05)', () => {
  let http: HttpTestingController;

  const historial = (contenido: PaginaExportaciones['contenido']): PaginaExportaciones => ({
    contenido,
    pagina: 0,
    tamanio: 20,
    totalElementos: contenido.length,
    totalPaginas: 1,
  });
  const registro = {
    idRegistro: 3,
    tipoReporte: 'reservas',
    formato: 'excel',
    cantidadRegistros: 12,
    idUsuario: 1,
    nombreUsuario: 'Admin LR',
    fecha: '2026-11-14T10:30:00',
  };

  function crear() {
    TestBed.configureTestingModule({
      imports: [ReportesAdmin],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: 'http://api' }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ReportesAdmin);
    fixture.detectChanges();
    http.expectOne((r) => r.url === 'http://api/admin/reportes/historial').flush(historial([]));
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:reporte');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  it('CA-027: exporta el reporte elegido en Excel y queda registrado en el historial con quién y cuándo', () => {
    const fixture = crear();
    const c = fixture.componentInstance;

    c.elegirTipo('reservas');
    c.exportReport('excel');
    expect(c.exportando()).toBe('excel');

    const req = http.expectOne((r) => r.url === 'http://api/admin/reportes/reservas');
    expect(req.request.params.get('formato')).toBe('excel');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['xlsx'], { type: 'application/octet-stream' }), {
      headers: { 'Content-Disposition': 'attachment; filename="reporte-reservas.xlsx"' },
    });

    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(c.mensaje()).toEqual({ ok: true, texto: 'Reporte exportado en Excel correctamente.' });

    http.expectOne((r) => r.url === 'http://api/admin/reportes/historial').flush(historial([registro]));
    fixture.detectChanges();

    const fila = (fixture.nativeElement.querySelector('tbody tr') as HTMLElement).textContent ?? '';
    expect(fila).toContain('Reporte de reservas');
    expect(fila).toContain('Excel');
    expect(fila).toContain('Admin LR');
    expect(fila).toContain('14/11/2026 10:30');
  });

  it('CA-027: exporta en PDF y muestra el error del backend si el reporte no se puede generar', async () => {
    const fixture = crear();
    const c = fixture.componentInstance;

    c.elegirTipo('demanda');
    c.exportReport('pdf');
    const req = http.expectOne((r) => r.url === 'http://api/admin/reportes/demanda');
    expect(req.request.params.get('formato')).toBe('pdf');
    req.flush(new Blob([JSON.stringify({ mensaje: 'Reporte no válido.' })]), { status: 400, statusText: 'Bad Request' });
    await new Promise((r) => setTimeout(r));

    expect(c.mensaje()).toEqual({ ok: false, texto: 'Reporte no válido.' });
    expect(c.exportando()).toBeNull();
  });

  it('solo el administrador puede entrar a los reportes', () => {
    const ruta = routes.find((r) => r.path === 'admin/reportes');
    expect(ruta?.data?.['roles']).toEqual(['Administrador']);
  });
});
