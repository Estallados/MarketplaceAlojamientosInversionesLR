import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { EMPTY, Observable, expand, map, reduce } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { SessionService } from '../../core/services/session.service';
import { Favorito, PaginaFavoritos } from '../models/favorito';

/** Elementos por página al leer todos los favoritos para pintar los corazones. */
const TAMANIO_SINCRONIZACION = 100;

// ======================================
// SERVICIO DE FAVORITOS (S13, HU-29)
// GET /favoritos, POST y DELETE /favoritos/{idAlojamiento} de docs/openapi/openapi.yaml
// El backend usa el usuario del token: cada uno ve y maneja solo los suyos.
// authInterceptor agrega el token.
// ======================================

@Injectable({
  providedIn: 'root'
})
export class FavoritoService {

  private readonly http = inject(HttpClient);

  private readonly session = inject(SessionService);

  private readonly apiUrl =
    `${inject(API_URL).replace(/\/+$/, '')}/favoritos`;

  // ====================================
  // ESTADO COMPARTIDO
  // ====================================

  /** Ids de los alojamientos favoritos confirmados por el backend. */
  private readonly ids = signal<ReadonlySet<number>>(new Set());

  /** Ids con una petición en curso (evita dobles clics). */
  private readonly pendientes = signal<ReadonlySet<number>>(new Set());

  /** Último error al agregar o quitar un favorito; null si no hay. */
  readonly mensaje = signal<string | null>(null);

  /** Solo los huéspedes con sesión vigente usan favoritos. */
  readonly disponible = computed(() => this.session.tieneRol('Huésped'));

  /** Correo del usuario cuyos favoritos están cargados (evita mezclar usuarios). */
  private cargadoPara = '';

  // ====================================
  // HTTP
  // ====================================

  /**
   * GET /favoritos?pagina=&tamanio= (CA-170).
   * Favoritos del usuario autenticado, del más reciente al más antiguo.
   */
  listar(pagina = 0, tamanio = 20): Observable<PaginaFavoritos> {

    return this.http.get<PaginaFavoritos>(this.apiUrl, {
      params: { pagina, tamanio }
    });
  }

  /**
   * POST /favoritos/{idAlojamiento} (CA-173).
   * 201 si es nuevo; 200 con el mismo favorito si ya estaba (no se duplica).
   * 404 si el alojamiento no existe o no está publicado.
   */
  agregar(idAlojamiento: number): Observable<Favorito> {

    return this.http.post<Favorito>(`${this.apiUrl}/${idAlojamiento}`, null);
  }

  /**
   * DELETE /favoritos/{idAlojamiento} (CA-171).
   * 200 si se quitó; 404 si no estaba en los favoritos del usuario.
   */
  eliminar(idAlojamiento: number): Observable<void> {

    return this.http
      .delete<unknown>(`${this.apiUrl}/${idAlojamiento}`)
      .pipe(map(() => undefined));
  }

  // ====================================
  // API PARA LOS COMPONENTES
  // ====================================

  esFavorito(id: number | null | undefined): boolean {

    return id != null && this.ids().has(id);
  }

  enCurso(id: number | null | undefined): boolean {

    return id != null && this.pendientes().has(id);
  }

  /** Marca como favoritos los de una lista que acaba de llegar del backend. */
  registrarLista(favoritos: Favorito[]): void {

    this.ids.update(actuales => {
      const nuevos = new Set(actuales);
      favoritos.forEach(f => nuevos.add(f.idAlojamiento));
      return nuevos;
    });
  }

  /**
   * Lee todos los favoritos del huésped una vez por usuario, para saber qué corazones
   * van llenos. Recorre todas las páginas porque no hay una consulta por alojamiento.
   */
  cargarSiCorresponde(): void {

    if (!this.disponible()) {
      return;
    }

    const correo = this.session.correo();

    if (this.cargadoPara === correo) {
      return;
    }

    this.cargadoPara = correo;
    this.ids.set(new Set());

    this.listar(0, TAMANIO_SINCRONIZACION)
      .pipe(
        expand(p =>
          p.pagina + 1 < p.totalPaginas
            ? this.listar(p.pagina + 1, TAMANIO_SINCRONIZACION)
            : EMPTY
        ),
        reduce((todos, p) => [...todos, ...(p.contenido ?? [])], [] as Favorito[])
      )
      .subscribe({
        next: lista => this.registrarLista(lista),
        // Si falla se reintenta en la próxima pantalla que lo pida.
        error: () => (this.cargadoPara = '')
      });
  }

  /**
   * Agrega o quita el favorito. El estado cambia solo cuando el backend lo confirma.
   *
   * @param id alojamiento
   * @param alTerminar se llama con true si la operación se completó
   */
  toggle(id: number | null | undefined, alTerminar?: (exito: boolean) => void): void {

    if (id == null || this.enCurso(id)) {
      return;
    }

    if (!this.disponible()) {
      this.mensaje.set('Inicia sesión como huésped para guardar alojamientos en favoritos.');
      alTerminar?.(false);
      return;
    }

    const eraFavorito = this.esFavorito(id);

    this.mensaje.set(null);
    this.marcarPendiente(id, true);

    const peticion: Observable<unknown> =
      eraFavorito ? this.eliminar(id) : this.agregar(id);

    peticion.subscribe({
      next: () => {
        this.marcarPendiente(id, false);
        this.marcarFavorito(id, !eraFavorito);
        alTerminar?.(true);
      },
      error: (error: HttpErrorResponse) => {
        this.marcarPendiente(id, false);

        // Quitar algo que ya no estaba en favoritos (404) deja el mismo resultado.
        if (eraFavorito && error.status === 404) {
          this.marcarFavorito(id, false);
          alTerminar?.(true);
          return;
        }

        this.mensaje.set(this.mensajeDeError(error, eraFavorito));
        alTerminar?.(false);
      }
    });
  }

  // ====================================
  // AUXILIARES
  // ====================================

  private marcarFavorito(id: number, esFavorito: boolean): void {

    this.ids.update(actuales => {
      const nuevos = new Set(actuales);
      if (esFavorito) {
        nuevos.add(id);
      } else {
        nuevos.delete(id);
      }
      return nuevos;
    });
  }

  private marcarPendiente(id: number, pendiente: boolean): void {

    this.pendientes.update(actuales => {
      const nuevos = new Set(actuales);
      if (pendiente) {
        nuevos.add(id);
      } else {
        nuevos.delete(id);
      }
      return nuevos;
    });
  }

  private mensajeDeError(error: HttpErrorResponse, eraFavorito: boolean): string {

    if (error.status === 401 || error.status === 403) {
      return 'Inicia sesión como huésped para guardar alojamientos en favoritos.';
    }

    if (error.status === 404) {
      return 'Este alojamiento ya no está disponible.';
    }

    return eraFavorito
      ? 'No se pudo quitar de favoritos. Inténtalo de nuevo.'
      : 'No se pudo guardar en favoritos. Inténtalo de nuevo.';
  }
}
