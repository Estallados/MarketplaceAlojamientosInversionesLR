import {
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal
} from '@angular/core';

import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';

import { ImagenAlojamiento } from '../models/alojamiento';
import { PublicacionHostService } from '../services/publicacion-host.service';
import {
  MAXIMO_IMAGENES,
  TIPOS_IMAGEN,
  errorFotografias
} from '../validaciones/validaciones-alojamiento';

// ======================================
// FOTOGRAFÍAS DE LA PUBLICACIÓN (HU-16)
// Portado de la sección "Fotografías" de pages/host/edit-listing del
// proyecto base, conectado a las rutas del contrato:
// POST   /alojamientos/{id}/imagenes                     (subir)
// PATCH  /alojamientos/{id}/imagenes/{idImagen}/principal (principal)
// DELETE /alojamientos/{id}/imagenes/{idImagen}           (eliminar)
// Cada cambio se guarda de inmediato. Solo se ofrece lo que el backend
// permite: máximo 15 imágenes y al menos una.
// ======================================

@Component({
  selector: 'app-fotografias-publicacion',
  standalone: true,
  templateUrl: './fotografias-publicacion.html'
})
export class FotografiasPublicacion {

  private readonly publicaciones = inject(PublicacionHostService);

  private readonly destroyRef = inject(DestroyRef);

  /** Alojamiento que se edita. */
  readonly idAlojamiento = input.required<number>();

  /** Imágenes actuales (detalleImagenes), la principal primero. */
  readonly imagenes = input<ImagenAlojamiento[]>([]);

  /** Lista que devolvió el backend tras cada cambio. */
  readonly imagenesCambiadas = output<ImagenAlojamiento[]>();

  /** El backend respondió 403 o 401 (CA-101). */
  readonly accesoRevocado = output<'denegado' | 'sin-sesion'>();

  readonly procesando = signal(false);

  readonly error = signal('');

  readonly mensaje = signal('');

  /** Imagen pendiente de confirmar en el modal de eliminación. */
  readonly fotoAEliminar = signal<ImagenAlojamiento | null>(null);

  readonly tiposAceptados = TIPOS_IMAGEN.join(',');

  /** Se pueden subir más mientras no se llegue al máximo del backend. */
  readonly puedeAgregar = computed(() => this.imagenes().length < MAXIMO_IMAGENES);

  /** El backend exige conservar al menos una imagen. */
  readonly puedeEliminar = computed(() => this.imagenes().length > 1);

  // ======================================
  // 1. AGREGAR IMAGEN (POST /imagenes)
  // ======================================

  archivosElegidos(evento: Event): void {

    const input = evento.target as HTMLInputElement;

    const nuevas = Array.from(input.files ?? []);

    input.value = '';

    if (nuevas.length === 0 || this.procesando()) {
      return;
    }

    this.limpiarMensajes();

    const problema = errorFotografias(nuevas, this.imagenes().length);

    if (problema) {
      this.error.set(problema);
      return;
    }

    this.ejecutar(
      this.publicaciones.agregarImagenes(this.idAlojamiento(), nuevas),
      nuevas.length === 1 ? 'Imagen agregada.' : 'Imágenes agregadas.'
    );
  }

  // ======================================
  // 2. FOTOGRAFÍA PRINCIPAL (PATCH /principal)
  // ======================================

  setMain(foto: ImagenAlojamiento): void {

    if (foto.principal || this.procesando()) {
      return;
    }

    this.limpiarMensajes();

    this.ejecutar(
      this.publicaciones.marcarImagenPrincipal(this.idAlojamiento(), foto.idImagen),
      'Fotografía principal actualizada.'
    );
  }

  // ======================================
  // 3. ELIMINAR IMAGEN (DELETE), CON CONFIRMACIÓN
  // ======================================

  pedirEliminar(foto: ImagenAlojamiento): void {

    if (!this.puedeEliminar() || this.procesando()) {
      return;
    }

    this.limpiarMensajes();

    this.fotoAEliminar.set(foto);
  }

  cancelarEliminar(): void {

    if (!this.procesando()) {
      this.fotoAEliminar.set(null);
    }
  }

  confirmRemovePhoto(): void {

    const foto = this.fotoAEliminar();

    if (!foto || this.procesando()) {
      return;
    }

    this.ejecutar(
      this.publicaciones.eliminarImagen(this.idAlojamiento(), foto.idImagen),
      'Imagen eliminada.'
    );
  }

  // ======================================
  // UTILIDADES
  // ======================================

  private ejecutar(
    peticion: Observable<ImagenAlojamiento[]>,
    exito: string
  ): void {

    this.procesando.set(true);

    peticion
      .pipe(
        finalize(() => {
          this.procesando.set(false);
          this.fotoAEliminar.set(null);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: imagenes => {
          this.imagenesCambiadas.emit(imagenes ?? []);
          this.mensaje.set(exito);
        },
        error: err => this.manejarError(err)
      });
  }

  private limpiarMensajes(): void {

    this.error.set('');
    this.mensaje.set('');
  }

  private manejarError(err: unknown): void {

    if (err instanceof HttpErrorResponse) {

      // CA-101: el backend rechaza a quien no es propietario ni ADMIN

      if (err.status === 403) {
        this.accesoRevocado.emit('denegado');
        return;
      }

      if (err.status === 401) {
        this.accesoRevocado.emit('sin-sesion');
        return;
      }

      if (err.status === 0) {
        this.error.set('No se pudo conectar con Spring Boot en localhost:8081.');
        return;
      }

      if (err.status === 413) {
        this.error.set('El archivo supera el tamaño permitido (5 MB por imagen).');
        return;
      }

      // 400, 404, 409 (debe quedar una imagen) y 503 traen el motivo del backend.
      if (err.error && typeof err.error.message === 'string') {
        this.error.set(err.error.message);
        return;
      }

      this.error.set(`Ocurrió un error en el servidor (HTTP ${err.status}).`);
      return;
    }

    this.error.set(
      'No se pudo completar la operación. ' +
      'Revisa la conexión e inténtalo nuevamente.'
    );
  }
}
