import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { HistorialPublicacion, Pagina, PublicacionAdmin } from '../models/publicacion-admin';

/** Acciones de revisión que el administrador aplica a una publicación (HU-32). */
export type AccionRevision = 'aprobar' | 'rechazar';

/** Textos y comportamiento del cuadro de confirmación de cada acción. */
export interface ConfigAccion {
  titulo: string;
  texto: string;
  etiquetaMotivo: string;
  confirmar: string;
  boton: string;
  requiereMotivo: boolean;
  peligro: boolean;
  /** Estado en el que queda la publicación si la acción se aplica. */
  resultado: string;
}

/** Largo máximo del motivo de rechazo (igual que en el backend). */
export const MAX_MOTIVO = 500;

/** Estados de una publicación con el texto que se muestra en pantalla. */
export const ESTADOS_PUBLICACION = [
  { valor: 'PENDIENTE_REVISION', texto: 'Pendiente de revisión' },
  { valor: 'ACTIVA', texto: 'Activa' },
  { valor: 'PAUSADA', texto: 'Pausada' },
  { valor: 'BLOQUEADA', texto: 'Bloqueada' },
  { valor: 'BORRADOR', texto: 'Borrador' },
];

/** Configuración de aprobar y rechazar. */
export const ACCIONES_REVISION: Record<AccionRevision, ConfigAccion> = {
  aprobar: {
    titulo: 'Aprobar publicación',
    texto: 'Pasará a ACTIVA y aparecerá en los resultados públicos. Se notificará al anfitrión.',
    etiquetaMotivo: '',
    confirmar: 'Confirmar aprobación',
    boton: 'Aprobar',
    requiereMotivo: false,
    peligro: false,
    resultado: 'ACTIVA',
  },
  rechazar: {
    titulo: 'Rechazar publicación',
    texto: 'Volverá a borrador para que el anfitrión la corrija. El motivo es obligatorio y se le notificará.',
    etiquetaMotivo: 'Motivo del rechazo',
    confirmar: 'Confirmar rechazo',
    boton: 'Rechazar',
    requiereMotivo: true,
    peligro: true,
    resultado: 'BORRADOR',
  },
};

/** Solo las publicaciones pendientes de revisión se pueden aprobar o rechazar. */
export function accionesDe(estado: string): AccionRevision[] {
  return estado === 'PENDIENTE_REVISION' ? ['aprobar', 'rechazar'] : [];
}

/** Primer valor no vacío entre varias claves posibles (los DTO pueden nombrarlas distinto). */
export function valorDe(obj: unknown, ...claves: string[]): string {
  if (!obj || typeof obj !== 'object') {
    return '';
  }
  const o = obj as Record<string, unknown>;
  for (const clave of claves) {
    const v = o[clave];
    if (v !== undefined && v !== null && v !== '' && typeof v !== 'object') {
      return String(v);
    }
  }
  return '';
}

/** Estado de una publicación como texto. */
export function estadoDe(p: PublicacionAdmin | null | undefined): string {
  return valorDe(p, 'estado');
}

/** Texto en español de un estado. */
export function etiquetaEstado(estado: string): string {
  return ESTADOS_PUBLICACION.find((e) => e.valor === estado)?.texto ?? (estado || 'Sin estado');
}

/** Clases de Tailwind de la etiqueta de color de un estado. */
export function claseBadge(estado: string): string {
  let tono = 'bg-slate-100 text-slate-600';
  if (estado === 'ACTIVA') {
    tono = 'bg-emerald-50 text-emerald-700';
  } else if (estado === 'BLOQUEADA') {
    tono = 'bg-red-50 text-red-700';
  } else if (estado === 'PENDIENTE_REVISION') {
    tono = 'bg-amber-50 text-amber-700';
  }
  return 'inline-flex rounded-full px-3 py-1 text-xs font-bold ' + tono;
}

/**
 * Revisión de publicaciones del administrador (HU-32).
 * GET /admin/alojamientos, POST /admin/alojamientos/{id}/aprobar y /rechazar.
 * El token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AdminPublicacionesService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_URL);
  private readonly base = `${this.api}/admin/alojamientos`;

  /** Una página de publicaciones, opcionalmente filtrada por estado. */
  pagina(pagina = 0, tamanio = 20, estado?: string): Observable<Pagina<PublicacionAdmin>> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    if (estado) {
      params = params.set('estado', estado);
    }
    return this.http
      .get<Partial<Pagina<PublicacionAdmin>> & { content?: PublicacionAdmin[]; totalElements?: number; totalPages?: number }>(
        this.base,
        { params },
      )
      .pipe(
        map((r) => ({
          contenido: r?.contenido ?? r?.content ?? [],
          pagina: r?.pagina ?? pagina,
          tamanio: r?.tamanio ?? tamanio,
          totalElementos: r?.totalElementos ?? r?.totalElements ?? 0,
          totalPaginas: r?.totalPaginas ?? r?.totalPages ?? 1,
        })),
      );
  }

  /** Todas las publicaciones (páginas de 100, hasta `maxPaginas`). */
  todas(estado?: string, maxPaginas = 20): Observable<PublicacionAdmin[]> {
    return this.pagina(0, 100, estado).pipe(
      switchMap((primera) => {
        const paginas = Math.min(primera.totalPaginas, maxPaginas);
        if (paginas <= 1) {
          return of(primera.contenido);
        }
        const resto = Array.from({ length: paginas - 1 }, (_, i) => this.pagina(i + 1, 100, estado));
        return forkJoin(resto).pipe(map((otras) => [primera.contenido, ...otras.map((p) => p.contenido)].flat()));
      }),
    );
  }

  /** Busca una publicación por id dentro del listado del administrador. */
  obtener(id: number): Observable<PublicacionAdmin> {
    return this.todas().pipe(
      map((lista) => {
        const encontrada = lista.find((p) => p.idAlojamiento === id);
        if (!encontrada) {
          throw new Error('Publicación no encontrada');
        }
        return encontrada;
      }),
    );
  }

  /** Historial de estados de la publicación (informativo). */
  historial(id: number): Observable<HistorialPublicacion[]> {
    return this.http.get<HistorialPublicacion[]>(`${this.api}/alojamientos/${id}/historial`);
  }

  /** PENDIENTE_REVISION → ACTIVA. */
  aprobar(id: number): Observable<PublicacionAdmin> {
    return this.http.post<PublicacionAdmin>(`${this.base}/${id}/aprobar`, {});
  }

  /** PENDIENTE_REVISION → BORRADOR, con motivo obligatorio. */
  rechazar(id: number, motivo: string): Observable<PublicacionAdmin> {
    return this.http.post<PublicacionAdmin>(`${this.base}/${id}/rechazar`, { motivo });
  }

  /** Aplica la acción elegida. */
  ejecutar(accion: AccionRevision, id: number, motivo = ''): Observable<PublicacionAdmin> {
    return accion === 'aprobar' ? this.aprobar(id) : this.rechazar(id, motivo);
  }

  /** Mensaje legible a partir de un error HTTP del backend. */
  mensajeDeError(error: unknown, porDefecto: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    if (error.status === 403) {
      return 'No tienes permisos para realizar esta operación.';
    }
    const cuerpo: unknown = error.error;
    if (typeof cuerpo === 'string' && cuerpo.trim()) {
      return cuerpo;
    }
    return valorDe(cuerpo, 'mensaje', 'message', 'error') || porDefecto;
  }
}
