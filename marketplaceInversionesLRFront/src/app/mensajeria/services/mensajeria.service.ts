import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { SessionService } from '../../core/services/session.service';
import { DetalleAlojamientoService } from '../../alojamientos/services/detalle-alojamiento.service';
import { Conversacion, ConversacionApi, Mensaje, MensajeApi } from '../models/mensajeria';

/** Página que devuelve el backend. */
interface Pagina<T> {
  contenido?: T[];
  content?: T[];
  totalPaginas?: number;
  totalPages?: number;
}

/**
 * Mensajería entre huésped y anfitrión (HU-28): /conversaciones.
 * Solo los participantes ven y escriben en una conversación (CA-167); el token lo agrega el interceptor.
 */
@Injectable({ providedIn: 'root' })
export class MensajeriaService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly alojamientos = inject(DetalleAlojamientoService);
  private readonly base = `${inject(API_URL)}/conversaciones`;

  /** Mis conversaciones, las más recientes primero, con el título de su alojamiento. */
  conversaciones(tamanio = 50): Observable<Conversacion[]> {
    const params = new HttpParams().set('pagina', 0).set('tamanio', tamanio);
    return this.http.get<Pagina<ConversacionApi>>(this.base, { params }).pipe(
      map((p) => (p?.contenido ?? p?.content ?? []).map((c) => this.aConversacion(c))),
      switchMap((lista) => this.conTitulos(lista)),
    );
  }

  /** Mensajes de una conversación, del más antiguo al más reciente (hasta `maxPaginas` de 100). */
  mensajes(conversacion: Conversacion, maxPaginas = 10): Observable<Mensaje[]> {
    const pedir = (pagina: number) =>
      this.http.get<Pagina<MensajeApi>>(`${this.base}/${conversacion.id}/mensajes`, {
        params: new HttpParams().set('pagina', pagina).set('tamanio', 100),
      });
    const items = (p: Pagina<MensajeApi>) =>
      (p?.contenido ?? p?.content ?? []).map((m) => this.aMensaje(m, conversacion.miId));
    return pedir(0).pipe(
      switchMap((primera) => {
        const paginas = Math.min(primera?.totalPaginas ?? primera?.totalPages ?? 1, maxPaginas);
        if (paginas <= 1) {
          return of(items(primera));
        }
        const resto = Array.from({ length: paginas - 1 }, (_, i) => pedir(i + 1));
        return forkJoin(resto).pipe(map((otras) => [...items(primera), ...otras.flatMap(items)]));
      }),
      map((lista) => [...lista].sort((a, b) => a.fecha.localeCompare(b.fecha) || a.id - b.id)),
    );
  }

  /** Envía un mensaje (1 a 2000 caracteres). 403 si no participas; 404 si no existe. */
  enviar(conversacion: Conversacion, contenido: string): Observable<Mensaje> {
    return this.http
      .post<MensajeApi>(`${this.base}/${conversacion.id}/mensajes`, { contenido })
      .pipe(map((m) => this.aMensaje(m, conversacion.miId)));
  }

  /**
   * Inicia o retoma la conversación con el anfitrión de un alojamiento (CA-169):
   * 201 si es nueva, 200 si ya existía. 403 si el alojamiento es tuyo; 404 si no existe.
   */
  iniciar(idAlojamiento: number, mensaje?: string): Observable<Conversacion> {
    const cuerpo = mensaje?.trim() ? { idAlojamiento, mensaje: mensaje.trim() } : { idAlojamiento };
    return this.http.post<ConversacionApi>(this.base, cuerpo).pipe(map((c) => this.aConversacion(c)));
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
    return error.status === 403 ? 'No participas en esta conversación.' : porDefecto;
  }

  // ================= Conversión de DTO =================

  private aConversacion(c: ConversacionApi): Conversacion {
    // Si soy anfitrión el contacto es el huésped; en cualquier otro caso, el anfitrión.
    const soyAnfitrion = this.session.rol() === 'Anfitrión';
    const contacto = (soyAnfitrion ? c.nombreUsuario : c.nombreAnfitrion) || 'Conversación';
    return {
      id: c.idConversacion,
      contacto,
      iniciales: this.iniciales(contacto),
      miId: soyAnfitrion ? c.idAnfitrion : c.idUsuario,
      idAlojamiento: c.idAlojamiento,
      alojamiento: '',
      fechaCreacion: c.fechaCreacion ?? '',
    };
  }

  private aMensaje(m: MensajeApi, miId: number): Mensaje {
    return {
      id: m.idMensaje,
      texto: m.contenido ?? '',
      fecha: m.fechaEnvio ?? '',
      remitente: m.nombreUsuario ?? '',
      mio: m.idUsuario === miId,
    };
  }

  /** El DTO no trae el título del alojamiento: se consulta GET /alojamientos/{id} (si falla, queda vacío). */
  private conTitulos(lista: Conversacion[]): Observable<Conversacion[]> {
    const ids = [...new Set(lista.map((c) => c.idAlojamiento).filter((id) => id != null))];
    if (ids.length === 0) {
      return of(lista);
    }
    return forkJoin(
      ids.map((id) =>
        this.alojamientos.obtener(id).pipe(
          map((a) => [id, a?.titulo ?? ''] as const),
          catchError(() => of([id, ''] as const)),
        ),
      ),
    ).pipe(
      map((pares) => {
        const titulos = new Map<number, string>(pares);
        return lista.map((c) => ({ ...c, alojamiento: titulos.get(c.idAlojamiento) ?? '' }));
      }),
    );
  }

  private iniciales(nombre: string): string {
    return (
      nombre
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((p) => p[0])
        .join('')
        .toUpperCase() || '?'
    );
  }
}
