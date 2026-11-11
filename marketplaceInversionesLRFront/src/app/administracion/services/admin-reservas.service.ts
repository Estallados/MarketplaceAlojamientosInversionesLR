import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { ReservaAdmin, ReservaAdminVista } from '../models/reserva-admin';
import { AdminPublicacionesService } from './admin-publicaciones.service';

/** Usuario mínimo para armar la tabla de reservas. */
interface UsuarioBasico {
  idUsuario: number;
  nombre: string;
  rol: string;
}

/** Página que devuelve el backend. */
interface Pagina<T> {
  contenido?: T[];
  content?: T[];
}

/**
 * Reservas para el administrador (HU-11): permite ver las reservas que quedaron EN_REVISION al bloquear
 * un alojamiento (CA-112).
 *
 * El backend todavía no tiene un GET /admin/reservas, así que se arman con los endpoints que sí existen:
 * GET /admin/usuarios (huéspedes) + GET /admin/usuarios/{id}/reservas, y GET /admin/alojamientos para el
 * anfitrión y la ciudad.
 */
@Injectable({ providedIn: 'root' })
export class AdminReservasService {
  private readonly http = inject(HttpClient);
  private readonly publicaciones = inject(AdminPublicacionesService);
  private readonly base = `${inject(API_URL)}/admin/usuarios`;

  /** Todas las reservas de todos los huéspedes, con huésped, anfitrión y ciudad. */
  todas(): Observable<ReservaAdminVista[]> {
    return forkJoin({
      usuarios: this.lista<UsuarioBasico>(this.base, 100),
      alojamientos: this.publicaciones.todas(),
    }).pipe(
      switchMap(({ usuarios, alojamientos }) => {
        const huespedes = usuarios.filter((u) => this.esHuesped(u.rol));
        if (huespedes.length === 0) {
          return of([] as ReservaAdminVista[]);
        }
        const porAlojamiento = new Map(alojamientos.map((a) => [a.idAlojamiento, a]));
        const hoy = new Date().toISOString().slice(0, 10);
        return forkJoin(
          huespedes.map((h) =>
            this.lista<ReservaAdmin>(`${this.base}/${h.idUsuario}/reservas`, 100).pipe(
              map((reservas) =>
                reservas.map<ReservaAdminVista>((r) => {
                  const alojamiento = porAlojamiento.get(r.idAlojamiento);
                  return {
                    ...r,
                    nombreHuesped: h.nombre,
                    nombreAnfitrion: alojamiento?.nombreAnfitrion ?? '—',
                    ciudad: alojamiento?.ciudad ?? '',
                    completada: r.estado === 'CONFIRMADA' && !!r.fechaSalida && r.fechaSalida < hoy,
                  };
                }),
              ),
            ),
          ),
        ).pipe(map((grupos) => grupos.flat().sort((a, b) => b.idReserva - a.idReserva)));
      }),
    );
  }

  private esHuesped(rol: unknown): boolean {
    return String(rol ?? '')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toUpperCase()
      .includes('HUESPED');
  }

  private lista<T>(url: string, tamanio: number): Observable<T[]> {
    const params = new HttpParams().set('pagina', 0).set('tamanio', tamanio);
    return this.http.get<Pagina<T>>(url, { params }).pipe(map((r) => r?.contenido ?? r?.content ?? []));
  }
}
