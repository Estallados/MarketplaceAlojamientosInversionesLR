import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { AlojamientoResultado, CamposBusqueda, PaginaResultados } from '../models/busqueda';


@Injectable({ providedIn: 'root' })
export class BusquedaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_URL)}/alojamientos`;

  /** Catálogo: alojamientos ACTIVA con fechas abiertas desde hoy. */
  disponibles(tamanio = 100): Observable<AlojamientoResultado[]> {
    const params = new HttpParams().set('pagina', 0).set('tamanio', tamanio);
    return this.http
      .get<PaginaResultados>(`${this.base}/disponibles`, { params })
      .pipe(map((r) => r?.contenido ?? r?.content ?? []));
  }

  /** Búsqueda con filtros (ciudad, fechas, huéspedes). */
  buscar(filtros: Record<string, string | number>, tamanio = 100): Observable<AlojamientoResultado[]> {
    let params = new HttpParams().set('pagina', 0).set('tamanio', tamanio);
    for (const [clave, valor] of Object.entries(filtros)) {
      params = params.set(clave, String(valor));
    }
    return this.http
      .get<PaginaResultados>(this.base, { params })
      .pipe(map((r) => r?.contenido ?? r?.content ?? []));
  }

  /** Fecha de hoy (local) como YYYY-MM-DD. */
  fechaHoy(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  /** Valida la barra de búsqueda; devuelve el mensaje de error o null si está bien. */
  validarBusqueda(f: CamposBusqueda): string | null {
    if (!!f.entrada !== !!f.salida) {
      return 'Indica la fecha de entrada y la de salida.';
    }
    if (f.entrada && f.entrada < this.fechaHoy()) {
      return 'La fecha de entrada no puede estar en el pasado.';
    }
    if (f.entrada && f.salida <= f.entrada) {
      return 'La salida debe ser posterior a la entrada.';
    }
    const n = Number(f.huespedes);
    if (f.huespedes.trim() && (!Number.isInteger(n) || n < 1)) {
      return 'Los huéspedes deben ser un número mayor que cero.';
    }
    return null;
  }

  /** Parámetros de la URL (ciudad, llegada, salida, huespedes); omite los vacíos. */
  paramsBusqueda(f: CamposBusqueda): Record<string, string | number> {
    const p: Record<string, string | number> = {};
    if (f.ciudad.trim()) {
      p['ciudad'] = f.ciudad.trim();
    }
    if (f.entrada && f.salida) {
      p['llegada'] = f.entrada;
      p['salida'] = f.salida;
    }
    if (f.huespedes.trim()) {
      p['huespedes'] = Number(f.huespedes);
    }
    return p;
  }

  /** Primeras amenidades separadas por " · ". */
  amenidadesResumen(stay: AlojamientoResultado, max = 2): string {
    return (stay.amenidades ?? []).slice(0, max).join(' · ') || 'Sin servicios indicados';
  }
}
