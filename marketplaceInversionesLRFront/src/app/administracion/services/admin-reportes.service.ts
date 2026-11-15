import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { ArchivoReporte, FormatoReporte, PaginaExportaciones, TipoReporte } from '../models/reporte';

/**
 * Reportes de gestión (HU-05): GET /admin/reportes/{tipo}?formato=pdf|excel descarga el archivo y deja
 * la exportación registrada; GET /admin/reportes/historial lista quién exportó qué y cuándo (CA-027).
 * El token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AdminReportesService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/reportes`;

  /** Descarga el reporte como archivo. */
  exportar(tipo: TipoReporte, formato: FormatoReporte): Observable<ArchivoReporte> {
    return this.http
      .get(`${this.base}/${tipo}`, {
        params: new HttpParams().set('formato', formato),
        responseType: 'blob',
        observe: 'response',
      })
      .pipe(
        map((r) => ({
          blob: r.body as Blob,
          nombre: this.nombreDeArchivo(r.headers.get('Content-Disposition'), tipo, formato),
        })),
        // Con responseType blob el cuerpo del error llega como Blob: se vuelve a leer como JSON.
        catchError((e: HttpErrorResponse) =>
          e.error instanceof Blob
            ? from(e.error.text()).pipe(
              switchMap((texto) =>
                throwError(
                  () => new HttpErrorResponse({ error: this.comoJson(texto), status: e.status, statusText: e.statusText }),
                ),
              ),
            )
            : throwError(() => e),
        ),
      );
  }

  /** Historial de exportaciones, de la más reciente a la más antigua. */
  historial(pagina = 0, tamanio = 20): Observable<PaginaExportaciones> {
    const params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    return this.http
      .get<Partial<PaginaExportaciones> & { content?: PaginaExportaciones['contenido'] }>(`${this.base}/historial`, {
        params,
      })
      .pipe(
        map((r) => ({
          contenido: r?.contenido ?? r?.content ?? [],
          pagina: r?.pagina ?? pagina,
          tamanio: r?.tamanio ?? tamanio,
          totalElementos: r?.totalElementos ?? 0,
          totalPaginas: r?.totalPaginas ?? 1,
        })),
      );
  }

  /** Mensaje legible a partir de un error HTTP del backend. */
  mensajeDeError(error: unknown, porDefecto: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    if (error.status === 401 || error.status === 403) {
      return 'No tienes permisos para exportar reportes.';
    }
    const cuerpo: unknown = error.error;
    if (typeof cuerpo === 'string' && cuerpo.trim()) {
      return cuerpo;
    }
    if (cuerpo !== null && typeof cuerpo === 'object') {
      const r = cuerpo as { mensaje?: string; message?: string };
      if (r.mensaje || r.message) {
        return (r.mensaje || r.message) as string;
      }
    }
    return porDefecto;
  }

  /** Nombre del archivo según Content-Disposition; si el navegador no deja leerla, el mismo del backend. */
  private nombreDeArchivo(cabecera: string | null, tipo: string, formato: FormatoReporte): string {
    const encontrado = cabecera?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
    return encontrado ? decodeURIComponent(encontrado[1]) : `reporte-${tipo}.${formato === 'excel' ? 'xlsx' : 'pdf'}`;
  }

  private comoJson(texto: string): unknown {
    try {
      return JSON.parse(texto);
    } catch {
      return texto;
    }
  }
}
