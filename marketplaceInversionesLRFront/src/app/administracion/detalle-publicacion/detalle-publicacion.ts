import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { HistorialPublicacion, PublicacionAdmin } from '../models/publicacion-admin';
import {
  ACCIONES_REVISION as ACCIONES,
  AccionRevision,
  AdminPublicacionesService,
  MAX_MOTIVO,
  accionesDe,
  claseBadge,
  estadoDe,
  etiquetaEstado,
  valorDe,
} from '../services/admin-publicaciones.service';

/**
 * Detalle de una publicación para el administrador (HU-32): datos, anfitrión, historial de estados
 * y botones para aprobar o rechazar con motivo.
 */
@Component({
  selector: 'app-detalle-publicacion',
  standalone: true,
  templateUrl: './detalle-publicacion.html',
  imports: [CurrencyPipe, DatePipe],
})
export class DetallePublicacion implements OnInit {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly admin = inject(AdminPublicacionesService);

  /** Parámetro :id de la ruta admin/alojamientos/:id */
  readonly id = Number(this.route.snapshot.paramMap.get('id'));
  readonly acciones = ACCIONES;
  readonly maxMotivo = MAX_MOTIVO;
  readonly valorDe = valorDe;

  readonly publicacion = signal<PublicacionAdmin | null>(null);
  readonly historial = signal<HistorialPublicacion[]>([]);
  readonly cargando = signal(true);
  readonly errorCarga = signal(false);
  readonly mensaje = signal('');

  readonly estado = computed(() => estadoDe(this.publicacion()));
  readonly etiqueta = computed(() => etiquetaEstado(this.estado()));
  readonly badge = computed(() => claseBadge(this.estado()));
  readonly disponibles = computed(() => accionesDe(this.estado()));
  readonly imagenes = computed(() => (this.publicacion()?.imagenes ?? []).slice(0, 3));
  readonly info = computed(() => {
    const p = this.publicacion();
    if (!p) {
      return [];
    }
    return [
      { label: 'Tipo', value: p.tipo ?? '' },
      { label: 'Ciudad', value: p.ciudad ?? '' },
      { label: 'Ubicación', value: valorDe(p, 'direccion', 'ubicacion') },
      { label: 'Capacidad', value: p.capacidad != null ? `${p.capacidad} huéspedes` : '' },
      { label: 'Habitaciones', value: p.habitaciones != null ? String(p.habitaciones) : '' },
      { label: 'Camas', value: p.camas != null ? String(p.camas) : '' },
      { label: 'Baños', value: p.banos != null ? String(p.banos) : '' },
    ].filter((fila) => !!fila.value);
  });

  // ---- Modal de aprobar / rechazar ----
  readonly accion = signal<AccionRevision | null>(null);
  readonly enviando = signal(false);
  readonly errorAccion = signal<string | null>(null);
  readonly motivo = signal('');
  readonly config = computed(() => {
    const a = this.accion();
    return a ? ACCIONES[a] : null;
  });
  readonly puedeConfirmar = computed(() => {
    const c = this.config();
    const largo = this.motivo().trim().length;
    return !!c && !this.enviando() && (!c.requiereMotivo || (largo > 0 && largo <= MAX_MOTIVO));
  });

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.cancelarAccion();
  }

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    if (!Number.isInteger(this.id) || this.id <= 0) {
      this.errorCarga.set(true);
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.admin.obtener(this.id).subscribe({
      next: (p) => {
        this.publicacion.set(p);
        this.cargando.set(false);
        this.cargarHistorial();
      },
      error: () => {
        this.errorCarga.set(true);
        this.cargando.set(false);
      },
    });
  }

  volver(): void {
    void this.router.navigateByUrl('/admin/alojamientos');
  }

  pedirAccion(tipo: AccionRevision): void {
    this.errorAccion.set(null);
    this.mensaje.set('');
    this.motivo.set('');
    this.accion.set(tipo);
  }

  cancelarAccion(): void {
    if (!this.enviando()) {
      this.accion.set(null);
    }
  }

  confirmarAccion(): void {
    const tipo = this.accion();
    const actual = this.publicacion();
    if (!tipo || !actual || !this.puedeConfirmar()) {
      return;
    }
    this.enviando.set(true);
    this.errorAccion.set(null);
    this.admin.ejecutar(tipo, this.id, this.motivo().trim()).subscribe({
      next: (actualizada) => {
        this.publicacion.set({ ...actual, ...actualizada, estado: ACCIONES[tipo].resultado });
        this.mensaje.set(
          tipo === 'aprobar'
            ? 'La publicación fue aprobada y ya está visible para los huéspedes.'
            : 'La publicación fue rechazada y volvió a borrador. Se notificó al anfitrión.',
        );
        this.cargarHistorial();
        this.enviando.set(false);
        this.accion.set(null);
      },
      error: (error: unknown) => {
        this.errorAccion.set(this.admin.mensajeDeError(error, 'No se pudo completar la acción.'));
        this.enviando.set(false);
      },
    });
  }

  /** El historial es informativo: si falla no bloquea la página. */
  private cargarHistorial(): void {
    this.admin.historial(this.id).subscribe({
      next: (h) => this.historial.set(h ?? []),
      error: () => this.historial.set([]),
    });
  }
}
