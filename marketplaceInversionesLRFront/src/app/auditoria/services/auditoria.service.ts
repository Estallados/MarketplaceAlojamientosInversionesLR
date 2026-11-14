import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { EventoAuditoria, FiltrosAuditoria, PaginaAuditoria } from '../models/evento-auditoria';

/** Acciones que registra el backend (constantes de AuditoriaService), para sugerirlas en el filtro. */
export const ACCIONES_AUDITORIA = [
  'ACCESO_DENEGADO',
  'ALOJAMIENTO_ACTUALIZADO',
  'ALOJAMIENTO_CAMBIO_ESTADO',
  'ALOJAMIENTO_CREADO',
  'CANCELACION',
  'CODIGO_VERIFICACION_ENVIADO',
  'CONTRASENIA_RESTABLECIDA',
  'CORREO_VERIFICADO',
  'CREACION_ADMINISTRADOR',
  'CUENTA_DESACTIVADA',
  'CUENTA_REACTIVADA',
  'DISPONIBILIDAD',
  'ERROR_INTERNO',
  'IMAGEN_ALOJAMIENTO',
  'LOGIN',
  'PAGO',
  'PERFIL_ACTUALIZADO',
  'RECUPERACION_SOLICITADA',
  'REGISTRO_USUARIO',
  'REPORTE_EXPORTADO',
  'RESENA_CREADA',
  'RESENA_MODERADA',
  'RESERVA_CREADA',
  'SEGUNDO_FACTOR',
  'SERVICIO_ADICIONAL',
  'TARIFA_ACTUALIZADA',
  'USUARIO_LIBERADO',
  'USUARIO_RESTRINGIDO',
];

/** Texto en español de un resultado. */
export function etiquetaResultado(resultado: string): string {
  switch (resultado) {
    case 'EXITOSO':
      return 'Exitoso';
    case 'RECHAZADO':
      return 'Rechazado';
    case 'ERROR':
      return 'Error';
    default:
      return resultado || '—';
  }
}

/** Clases de Tailwind de la etiqueta de color de un resultado. */
export function claseResultado(resultado: string): string {
  let tono = 'bg-brand-50 text-brand-700';
  if (resultado === 'RECHAZADO') {
    tono = 'bg-amber-50 text-amber-700';
  } else if (resultado === 'ERROR') {
    tono = 'bg-red-50 text-red-700';
  }
  return 'inline-flex rounded-full px-3 py-1 text-xs font-bold ' + tono;
}

/**
 * Consulta de la auditoría (HU-13): GET /admin/auditoria con filtros y GET /admin/auditoria/{id}.
 * Solo para administradores; el token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/admin/auditoria`;

  /** Una página de eventos, del más reciente al más antiguo. */
  pagina(pagina: number, tamanio: number, filtros: FiltrosAuditoria = {}): Observable<PaginaAuditoria> {
    let params = new HttpParams().set('pagina', pagina).set('tamanio', tamanio);
    if (filtros.accion) params = params.set('accion', filtros.accion);
    if (filtros.idActor) params = params.set('idActor', filtros.idActor);
    if (filtros.desde) params = params.set('desde', filtros.desde);
    if (filtros.hasta) params = params.set('hasta', filtros.hasta);
    return this.http
      .get<Partial<PaginaAuditoria> & { content?: EventoAuditoria[]; totalElements?: number; totalPages?: number }>(
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

  /** Detalle de un evento. */
  evento(id: number): Observable<EventoAuditoria> {
    return this.http.get<EventoAuditoria>(`${this.base}/${id}`);
  }

  /** Mensaje legible a partir de un error HTTP (CA-065: sin permisos no se accede). */
  mensajeDeError(error: unknown, porDefecto: string): string {
    if (!(error instanceof HttpErrorResponse)) {
      return porDefecto;
    }
    if (error.status === 0) {
      return 'No hay conexión con el servidor.';
    }
    if (error.status === 401 || error.status === 403) {
      return 'No tienes permisos para consultar la auditoría.';
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
}
