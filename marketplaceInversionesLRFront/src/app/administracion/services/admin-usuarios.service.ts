import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import {
  AlojamientoDeUsuario,
  ReservaDeUsuario,
  TipoEstado,
  TipoRol,
  UsuarioAdmin,
} from '../models/usuario-admin';

/** Largo máximo del motivo (igual que en el backend). */
export const MAX_MOTIVO_USUARIO = 500;

/** Textos del cuadro de confirmación de cada acción sobre la cuenta. */
export const ACCIONES_USUARIO = {
  restringir: {
    titulo: 'Restringir usuario',
    texto: '¿Estás seguro de que deseas restringir esta cuenta? No podrá iniciar sesión ni seguir usando la plataforma.',
    etiqueta: 'Motivo de la restricción',
    confirmar: 'Confirmar restricción',
    necesitaMotivo: true,
  },
  activar: {
    titulo: 'Activar usuario',
    texto: '¿Deseas activar nuevamente esta cuenta?',
    etiqueta: '',
    confirmar: 'Confirmar activación',
    necesitaMotivo: false,
  },
} as const;

export type TipoAccionUsuario = keyof typeof ACCIONES_USUARIO;

/** Página que devuelve el backend. */
interface Pagina<T> {
  contenido?: T[];
  content?: T[];
  totalPaginas?: number;
  totalPages?: number;
}

/** Normaliza sin depender de cómo escribe el backend el enum (Huésped, HUESPED, ...). */
function normalizar(valor: unknown): string {
  return String(valor ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase();
}

export function tipoRol(rol: unknown): TipoRol {
  const r = normalizar(rol);
  if (r.includes('ADMIN')) return 'ADMIN';
  if (r.includes('ANFITRION')) return 'ANFITRION';
  if (r.includes('HUESPED')) return 'HUESPED';
  return '';
}

export function tipoEstado(estado: unknown): TipoEstado {
  const e = normalizar(estado);
  if (e.includes('RESTRING')) return 'RESTRINGIDO';
  if (e.includes('INACT')) return 'INACTIVO';
  if (e.includes('ACTIV')) return 'ACTIVO';
  return '';
}

export function etiquetaRol(rol: unknown): string {
  return { ADMIN: 'Administrador', ANFITRION: 'Anfitrión', HUESPED: 'Huésped', '': String(rol ?? '') }[tipoRol(rol)];
}

export function etiquetaEstado(estado: unknown): string {
  return { ACTIVO: 'Activo', RESTRINGIDO: 'Restringido', INACTIVO: 'Inactivo', '': String(estado ?? '') }[
    tipoEstado(estado)
    ];
}

export function claseEstado(estado: unknown): string {
  const e = tipoEstado(estado);
  let tono = 'bg-slate-100 text-slate-600';
  if (e === 'ACTIVO') {
    tono = 'bg-emerald-50 text-emerald-700';
  } else if (e === 'RESTRINGIDO') {
    tono = 'bg-red-50 text-red-700';
  }
  return 'inline-flex rounded-full px-3 py-1 text-xs font-bold ' + tono;
}

/**
 * Administración de cuentas (HU-12).
 * GET /admin/usuarios, PATCH /admin/usuarios/{id}/restringir y /liberar,
 * y para el detalle GET /admin/usuarios/{id}/reservas y /alojamientos. El token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AdminUsuariosService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/usuarios`;

  /** Todos los usuarios (páginas de 100). */
  usuarios(): Observable<UsuarioAdmin[]> {
    return this.todas<UsuarioAdmin>(this.base);
  }

  /** Busca un usuario por id dentro de la lista (el backend no tiene GET /admin/usuarios/{id}). */
  obtener(id: number): Observable<UsuarioAdmin | null> {
    return this.usuarios().pipe(map((lista) => lista.find((u) => u.idUsuario === id) ?? null));
  }

  /** Reservas del usuario como huésped. */
  reservasDeUsuario(id: number): Observable<ReservaDeUsuario[]> {
    return this.todas<ReservaDeUsuario>(`${this.base}/${id}/reservas`);
  }

  /** Alojamientos del usuario como anfitrión, en cualquier estado. */
  alojamientosDeUsuario(id: number): Observable<AlojamientoDeUsuario[]> {
    return this.todas<AlojamientoDeUsuario>(`${this.base}/${id}/alojamientos`);
  }

  /** Restringe la cuenta con motivo obligatorio (CA-058). */
  restringir(id: number, motivo: string): Observable<UsuarioAdmin> {
    return this.http.patch<UsuarioAdmin>(`${this.base}/${id}/restringir`, { motivo });
  }

  /** Activa una cuenta restringida o inactiva (motivo opcional). */
  liberar(id: number, motivo?: string): Observable<UsuarioAdmin> {
    return this.http.patch<UsuarioAdmin>(`${this.base}/${id}/liberar`, motivo ? { motivo } : {});
  }

  /** Mensaje legible a partir de un error HTTP del backend. */
  mensajeDeError(error: unknown, porDefecto: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
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
    return error.status === 403 ? 'No tienes permisos para realizar esta operación.' : porDefecto;
  }

  /** Pide todas las páginas (de 100) de un listado paginado. */
  private todas<T>(url: string, maxPaginas = 20): Observable<T[]> {
    const pagina = (n: number) =>
      this.http.get<Pagina<T>>(url, { params: new HttpParams().set('pagina', n).set('tamanio', 100) });
    return pagina(0).pipe(
      switchMap((primera) => {
        const contenido = primera?.contenido ?? primera?.content ?? [];
        const total = Math.min(primera?.totalPaginas ?? primera?.totalPages ?? 1, maxPaginas);
        if (total <= 1) {
          return of(contenido);
        }
        const resto = Array.from({ length: total - 1 }, (_, i) => pagina(i + 1));
        return forkJoin(resto).pipe(
          map((otras) => [contenido, ...otras.map((p) => p?.contenido ?? p?.content ?? [])].flat()),
        );
      }),
    );
  }
}
