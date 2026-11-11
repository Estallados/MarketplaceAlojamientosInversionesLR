import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { PaginaReservas, Reserva, ReservaEntrada } from '../models/reserva';

/** Resultado de POST /reservas. */
export interface ReservaCreada {

  reserva: Reserva;

  /** true si el backend devolvió la respuesta guardada de una petición repetida. */
  repetida: boolean;
}

// ======================================
// SERVICIO DE RESERVAS (S07)
// POST /reservas de docs/openapi/reservas-pagos.yaml
// Exige sesión con rol HUESPED; authInterceptor agrega el token.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class ReservaService {

  private readonly http = inject(HttpClient);

  private readonly baseUrl = inject(API_URL);

  private readonly apiUrl =
    `${this.baseUrl.replace(/\/+$/, '')}/reservas`;

  // ====================================
  // CREAR RESERVA (HU-22)
  // POST /reservas con la cabecera Idempotency-Key (RNF-008)
  // El backend revalida la disponibilidad (CA-074), confirma de inmediato
  // con el monto cotizado (CA-070, CA-075, CA-086) y bloquea el período (CA-076).
  // 400 datos · 401 sesión · 402 pago rechazado · 403 rol o alojamiento propio
  // 404 cotización o alojamiento · 409 cotización vencida/usada, no ACTIVA (CA-071),
  // período tomado (CA-072) o petición en proceso · 422 clave reutilizada · 5xx
  // ====================================

  /**
   * @param entrada cotización a reservar
   * @param claveIdempotencia clave única por intento; repetirla solo al reintentar la misma petición
   */
  crear(
    entrada: ReservaEntrada,
    claveIdempotencia: string
  ): Observable<ReservaCreada> {

    return this.http
      .post<Reserva>(this.apiUrl, entrada, {
        headers: { 'Idempotency-Key': claveIdempotencia },
        observe: 'response'
      })
      .pipe(
        map(respuesta => ({
          reserva: respuesta.body as Reserva,
          // Idempotent-Replayed: true si es la respuesta guardada de un reintento (CA-141).
          repetida: respuesta.headers.get('Idempotent-Replayed') === 'true'
        }))
      );
  }

  /** Clave nueva para un intento de reserva (1 a 100 caracteres según el contrato). */
  nuevaClaveIdempotencia(): string {

    const c = globalThis.crypto;

    if (typeof c.randomUUID === 'function') {
      return c.randomUUID();
    }

    // randomUUID solo existe en contexto seguro (https o localhost).
    return Array.from(
      c.getRandomValues(new Uint8Array(16)),
      b => b.toString(16).padStart(2, '0')
    ).join('');
  }

  // ====================================
  // HISTORIAL DE RESERVAS (HU-25)
  // GET /reservas/mias        reservas del huésped de la sesión (CA-078)
  // GET /reservas/anfitrion   reservas de los alojamientos del anfitrión (CA-079; 403 a los demás)
  // GET /reservas/{id}        detalle para su huésped, el anfitrión del alojamiento o ADMIN (404 a los demás)
  // ====================================

  misReservas(pagina = 0, tamanio = 20): Observable<PaginaReservas> {

    return this.http.get<PaginaReservas>(`${this.apiUrl}/mias`, { params: { pagina, tamanio } });
  }

  /** Todas las reservas del huésped, recorriendo todas las páginas. */
  todasMisReservas(tamanio = 100): Observable<Reserva[]> {

    return this.misReservas(0, tamanio).pipe(
      expand(p => p.pagina + 1 < p.totalPaginas ? this.misReservas(p.pagina + 1, tamanio) : EMPTY),
      reduce((todas, p) => [...todas, ...(p.contenido ?? [])], [] as Reserva[])
    );
  }

  reservasAnfitrion(pagina = 0, tamanio = 10): Observable<PaginaReservas> {

    return this.http.get<PaginaReservas>(`${this.apiUrl}/anfitrion`, { params: { pagina, tamanio } });
  }

  obtener(id: number): Observable<Reserva> {

    return this.http.get<Reserva>(`${this.apiUrl}/${id}`);
  }
}
