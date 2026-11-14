import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { EMPTY, expand, finalize, reduce, timeout } from 'rxjs';

import { API_URL } from '../../core/services/api-url';
import { InsigniaEstado } from '../insignia-estado/insignia-estado';
import { AlojamientoRespuesta, EstadoAlojamiento } from '../models/alojamiento';
import { PublicacionHostService } from '../services/publicacion-host.service';
import { Cargando } from '../../core/ui/cargando/cargando';

export type FiltroAlojamiento = 'TODOS' | EstadoAlojamiento;

/** Elementos por página al leer todas las publicaciones. */
const TAMANIO_PAGINA = 100;

/** Espera máxima de cada página. */
const ESPERA_MS = 15000;

// ======================================
// MIS ALOJAMIENTOS (HU-19, CA-103)
// GET /alojamientos/mios: publicaciones del anfitrión de la sesión con el
// estado de cada una. Copia la pantalla host/listings del proyecto base;
// el estado se muestra con la insignia de HU-17.
// ======================================

@Component({
  selector: 'app-mis-alojamientos',
  standalone: true,
  imports: [Cargando, InsigniaEstado],
  templateUrl: './mis-alojamientos.html'
})
export class MisAlojamientos implements OnInit {

  private readonly router = inject(Router);

  private readonly publicaciones = inject(PublicacionHostService);

  private readonly baseUrl = inject(API_URL).replace(/\/+$/, '');

  // ======================================
  // ESTADO
  // ======================================

  readonly listings = signal<AlojamientoRespuesta[]>([]);

  readonly cargando = signal(false);

  readonly error = signal('');

  readonly filtro = signal<FiltroAlojamiento>('TODOS');

  readonly filtros: { valor: FiltroAlojamiento; nombre: string }[] = [
    { valor: 'TODOS', nombre: 'Todos los alojamientos' },
    { valor: 'ACTIVA', nombre: 'Disponibles' },
    { valor: 'PENDIENTE_REVISION', nombre: 'En revisión' },
    { valor: 'BORRADOR', nombre: 'Sin publicar' },
    { valor: 'PAUSADA', nombre: 'Pausados' },
    { valor: 'BLOQUEADA', nombre: 'Bloqueados' }
  ];

  // ======================================
  // CONTADORES
  // ======================================

  readonly total = computed(() => this.listings().length);

  readonly active = computed(() => this.contar('ACTIVA'));

  readonly pending = computed(() => this.contar('PENDIENTE_REVISION'));

  readonly drafts = computed(() => this.contar('BORRADOR'));

  readonly paused = computed(() => this.contar('PAUSADA'));

  readonly blocked = computed(() => this.contar('BLOQUEADA'));

  readonly alojamientosFiltrados = computed(() => {
    const estado = this.filtro();
    return estado === 'TODOS'
      ? this.listings()
      : this.listings().filter(alojamiento => alojamiento.estado === estado);
  });

  ngOnInit(): void {
    this.cargarAlojamientos();
  }

  cambiarFiltro(evento: Event): void {
    this.filtro.set((evento.target as HTMLSelectElement).value as FiltroAlojamiento);
  }

  // ======================================
  // CARGA (todas las páginas de GET /alojamientos/mios)
  // ======================================

  cargarAlojamientos(): void {

    if (this.cargando()) {
      return;
    }

    this.cargando.set(true);
    this.error.set('');

    this.publicaciones
      .obtenerMisAlojamientos(0, TAMANIO_PAGINA)
      .pipe(
        timeout(ESPERA_MS),
        expand(respuesta => {
          const siguiente = respuesta.pagina + 1;
          return siguiente >= respuesta.totalPaginas
            ? EMPTY
            : this.publicaciones.obtenerMisAlojamientos(siguiente, TAMANIO_PAGINA).pipe(timeout(ESPERA_MS));
        }),
        reduce((todos, respuesta) => [...todos, ...(respuesta.contenido ?? [])], [] as AlojamientoRespuesta[]),
        finalize(() => this.cargando.set(false))
      )
      .subscribe({
        next: alojamientos => this.listings.set(alojamientos),
        error: err => this.error.set(this.mensajeError(err))
      });
  }

  // ======================================
  // PRESENTACIÓN
  // ======================================

  formatearPrecio(precio?: number | null): string {

    if (precio === null || precio === undefined) {
      return 'Sin definir';
    }

    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      maximumFractionDigits: 0
    }).format(precio);
  }

  formatearFecha(valor?: string): string {

    if (!valor) {
      return 'Sin información';
    }

    const fecha = new Date(valor);

    if (Number.isNaN(fecha.getTime())) {
      return valor;
    }

    return fecha.toLocaleDateString('es-CO', {
      day: '2-digit',
      month: 'long',
      year: 'numeric'
    });
  }

  /** Foto principal (o la primera); las rutas relativas se sirven desde el backend. */
  obtenerImagen(alojamiento: AlojamientoRespuesta): string | null {

    const imagen =
      alojamiento.detalleImagenes?.find(i => i.principal)?.url ??
      alojamiento.imagenes?.[0] ??
      alojamiento.detalleImagenes?.[0]?.url;

    if (!imagen) {
      return null;
    }

    if (/^https?:\/\//i.test(imagen)) {
      return imagen;
    }

    return `${this.baseUrl}${imagen.startsWith('/') ? '' : '/'}${imagen}`;
  }

  mostrarCalificacion(alojamiento: AlojamientoRespuesta): string {

    const promedio = alojamiento.promedioCalificacion;

    return promedio === null || promedio === undefined ? 'Sin calificaciones' : promedio.toFixed(1);
  }

  // ======================================
  // NAVEGACIÓN
  // ======================================

  create(): void {
    void this.router.navigateByUrl('/host/alojamientos/crear');
  }

  /** "Editar detalles": la edición muestra todos los datos y el estado de la publicación. */
  edit(id: number): void {
    void this.router.navigate(['/host/alojamientos', id, 'editar']);
  }

  availability(id: number): void {
    void this.router.navigate(['/host/alojamientos', id, 'disponibilidad']);
  }

  // ======================================
  // AUXILIARES
  // ======================================

  private contar(estado: EstadoAlojamiento): number {
    return this.listings().filter(alojamiento => alojamiento.estado === estado).length;
  }

  private mensajeError(err: unknown): string {

    if (err instanceof Error && err.name === 'TimeoutError') {
      return 'El servidor tardó demasiado en responder. Inténtalo de nuevo.';
    }

    if (err instanceof HttpErrorResponse) {

      if (err.status === 0) {
        return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
      }

      if (err.status === 401) {
        return 'Tu sesión ha expirado. Inicia sesión nuevamente.';
      }

      if (err.status === 403) {
        return 'Solo los anfitriones pueden consultar sus alojamientos.';
      }

      if (typeof err.error?.message === 'string' && err.error.message.trim()) {
        return err.error.message;
      }
    }

    return 'No se pudieron cargar tus alojamientos. Inténtalo de nuevo.';
  }
}
