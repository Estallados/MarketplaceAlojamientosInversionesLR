import {
  Component,
  DestroyRef,
  inject,
  input,
  linkedSignal,
  output,
  signal
} from '@angular/core';

import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize } from 'rxjs';

import {
  ServicioAdicional,
  ServicioAdicionalEntrada
} from '../models/alojamiento';

import { PublicacionHostService } from '../services/publicacion-host.service';

import {
  errorCostoServicio,
  errorNombreServicio
} from '../validaciones/validaciones-alojamiento';

/** Formulario de un servicio (nuevo o en edición). */
interface FormularioServicio {
  nombre: string;
  costo: number | null;
}

type CampoServicio = 'nombre' | 'costo';

const FORMATO_COP = new Intl.NumberFormat('es-CO', {
  style: 'currency',
  currency: 'COP',
  minimumFractionDigits: 0,
  maximumFractionDigits: 2
});

// ======================================
// SERVICIOS ADICIONALES CON COSTO
// HU-16, CA-185
//
// Registra, modifica y elimina los servicios adicionales de la
// publicación. Los cambios aplican a cotizaciones nuevas; que las
// reservas confirmadas conserven su valor lo garantiza el backend.
// ======================================

@Component({
  selector: 'app-servicios-adicionales',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './servicios-adicionales.html'
})
export class ServiciosAdicionales {

  private readonly publicaciones =
    inject(PublicacionHostService);

  private readonly destroyRef =
    inject(DestroyRef);

  // ======================================
  // ENTRADAS Y SALIDAS
  // ======================================

  /** Alojamiento al que pertenecen los servicios. */
  readonly idAlojamiento = input.required<number>();

  /** Servicios que trae el detalle del alojamiento. */
  readonly servicios = input<ServicioAdicional[]>([]);

  /** Lista actualizada después de cada operación. */
  readonly serviciosCambiados = output<ServicioAdicional[]>();

  /** El backend respondió 403 o 401 (CA-101). */
  readonly accesoRevocado = output<'denegado' | 'sin-sesion'>();

  // ======================================
  // ESTADO
  // ======================================

  /** Servicios mostrados; parte de la entrada y se actualiza localmente. */
  readonly lista = linkedSignal(() => [...this.servicios()]);

  readonly procesando = signal(false);

  readonly error = signal('');

  readonly mensaje = signal('');

  /** Servicio en edición (idServicio). */
  readonly editando = signal<number | null>(null);

  /** Servicio pendiente de confirmar su eliminación. */
  readonly porEliminar = signal<ServicioAdicional | null>(null);

  nuevo: FormularioServicio = { nombre: '', costo: null };

  edicion: FormularioServicio = { nombre: '', costo: null };

  private intentoNuevo = false;

  private intentoEdicion = false;

  private readonly noNumericos = new Set<'nuevo' | 'edicion'>();

  // ======================================
  // VALIDACIÓN (nombre no vacío y costo positivo)
  // ======================================

  errorNuevo(campo: CampoServicio): string {
    return this.errorDe(this.nuevo, campo, this.noNumericos.has('nuevo'));
  }

  errorEdicion(campo: CampoServicio): string {
    return this.errorDe(this.edicion, campo, this.noNumericos.has('edicion'));
  }

  mostrarErrorNuevo(campo: CampoServicio): boolean {
    return this.intentoNuevo && this.errorNuevo(campo) !== '';
  }

  mostrarErrorEdicion(campo: CampoServicio): boolean {
    return this.intentoEdicion && this.errorEdicion(campo) !== '';
  }

  revisarNumero(
    formulario: 'nuevo' | 'edicion',
    evento: Event
  ): void {

    const campo = evento.target as HTMLInputElement;

    if (campo.validity?.badInput) {
      this.noNumericos.add(formulario);
    } else {
      this.noNumericos.delete(formulario);
    }
  }

  private errorDe(
    formulario: FormularioServicio,
    campo: CampoServicio,
    noNumerico: boolean
  ): string {

    return campo === 'nombre'
      ? errorNombreServicio(formulario.nombre)
      : errorCostoServicio(formulario.costo, noNumerico);
  }

  private esValido(
    formulario: FormularioServicio,
    noNumerico: boolean
  ): boolean {

    return (
      this.errorDe(formulario, 'nombre', noNumerico) === '' &&
      this.errorDe(formulario, 'costo', noNumerico) === ''
    );
  }

  private entradaDe(
    formulario: FormularioServicio
  ): ServicioAdicionalEntrada {

    return {
      nombre: formulario.nombre.trim(),
      costo: formulario.costo as number
    };
  }

  formatoCosto(costo: number): string {
    return FORMATO_COP.format(costo);
  }

  // ======================================
  // 1. REGISTRAR SERVICIO
  // POST /alojamientos/{id}/servicios
  // ======================================

  agregar(): void {

    if (this.procesando()) {
      return;
    }

    this.intentoNuevo = true;

    this.limpiarMensajes();

    if (!this.esValido(this.nuevo, this.noNumericos.has('nuevo'))) {
      return;
    }

    this.ejecutar(
      this.publicaciones.agregarServicio(
        this.idAlojamiento(),
        this.entradaDe(this.nuevo)
      ),
      creado => {

        this.actualizarLista([...this.lista(), creado]);

        this.nuevo = { nombre: '', costo: null };

        this.intentoNuevo = false;

        this.mensaje.set(
          `Servicio "${creado.nombre}" agregado. ` +
          'Se aplicará a las nuevas cotizaciones.'
        );
      }
    );
  }

  // ======================================
  // 2. MODIFICAR SERVICIO
  // PUT /alojamientos/{id}/servicios/{idServicio}
  // ======================================

  editar(servicio: ServicioAdicional): void {

    if (this.procesando() || servicio.idServicio === undefined) {
      return;
    }

    this.limpiarMensajes();

    this.porEliminar.set(null);

    this.editando.set(servicio.idServicio);

    this.edicion = {
      nombre: servicio.nombre,
      costo: servicio.costo
    };

    this.intentoEdicion = false;

    this.noNumericos.delete('edicion');
  }

  cancelarEdicion(): void {

    this.editando.set(null);

    this.intentoEdicion = false;

    this.noNumericos.delete('edicion');
  }

  guardarEdicion(): void {

    const idServicio = this.editando();

    if (this.procesando() || idServicio === null) {
      return;
    }

    this.intentoEdicion = true;

    this.limpiarMensajes();

    if (!this.esValido(this.edicion, this.noNumericos.has('edicion'))) {
      return;
    }

    this.ejecutar(
      this.publicaciones.actualizarServicio(
        this.idAlojamiento(),
        idServicio,
        this.entradaDe(this.edicion)
      ),
      actualizado => {

        this.actualizarLista(
          this.lista().map(s =>
            s.idServicio === idServicio
              ? { ...s, ...actualizado }
              : s
          )
        );

        this.cancelarEdicion();

        this.mensaje.set(
          `Servicio "${actualizado.nombre}" actualizado. ` +
          'Las reservas ya confirmadas conservan su valor.'
        );
      }
    );
  }

  // ======================================
  // 3. ELIMINAR SERVICIO (con confirmación)
  // DELETE /alojamientos/{id}/servicios/{idServicio}
  // ======================================

  pedirEliminar(servicio: ServicioAdicional): void {

    if (this.procesando()) {
      return;
    }

    this.limpiarMensajes();

    this.cancelarEdicion();

    this.porEliminar.set(servicio);
  }

  cancelarEliminar(): void {
    this.porEliminar.set(null);
  }

  confirmarEliminar(): void {

    const servicio = this.porEliminar();

    if (
      this.procesando() ||
      !servicio ||
      servicio.idServicio === undefined
    ) {
      return;
    }

    const idServicio = servicio.idServicio;

    this.ejecutar(
      this.publicaciones.eliminarServicio(
        this.idAlojamiento(),
        idServicio
      ),
      () => {

        this.actualizarLista(
          this.lista().filter(s => s.idServicio !== idServicio)
        );

        this.porEliminar.set(null);

        this.mensaje.set(
          `Servicio "${servicio.nombre}" eliminado. ` +
          'Las reservas ya confirmadas conservan su valor.'
        );
      }
    );
  }

  // ======================================
  // UTILIDADES
  // ======================================

  private ejecutar<T>(
    peticion: Observable<T>,
    exito: (respuesta: T) => void
  ): void {

    this.procesando.set(true);

    peticion
      .pipe(
        finalize(() => {
          this.procesando.set(false);
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: exito,
        error: err => {
          this.manejarError(err);
        }
      });
  }

  private actualizarLista(servicios: ServicioAdicional[]): void {

    this.lista.set(servicios);

    this.serviciosCambiados.emit(servicios);
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

        this.error.set(
          'No se pudo conectar con Spring Boot en localhost:8081.'
        );

        return;
      }

      if (
        err.error &&
        typeof err.error.message === 'string'
      ) {

        this.error.set(err.error.message);

        return;
      }

      this.error.set(
        `Ocurrió un error en el servidor (HTTP ${err.status}).`
      );

      return;
    }

    this.error.set(
      'No se pudo completar la operación. ' +
      'Revisa la conexión e inténtalo nuevamente.'
    );
  }
}
