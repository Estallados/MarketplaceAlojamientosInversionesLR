import { CurrencyPipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { PublicacionAdmin } from '../models/publicacion-admin';
import {
  ACCIONES_REVISION as ACCIONES,
  AccionRevision,
  AdminPublicacionesService,
  ESTADOS_PUBLICACION,
  MAX_MOTIVO,
  accionesDe,
  claseBadge,
  estadoDe,
  etiquetaEstado,
} from '../services/admin-publicaciones.service';

/**
 * Cola de revisión de publicaciones del administrador (HU-32).
 * Lista las publicaciones (por defecto las pendientes) y permite aprobar o rechazar con motivo.
 */
@Component({
  selector: 'app-revisar-publicaciones',
  standalone: true,
  templateUrl: './revisar-publicaciones.html',
  imports: [CurrencyPipe],
})
export class RevisarPublicaciones implements OnInit {
  private readonly router = inject(Router);
  private readonly admin = inject(AdminPublicacionesService);
  private readonly TAMANIO = 10;

  readonly acciones = ACCIONES;
  readonly estados = ESTADOS_PUBLICACION;
  readonly maxMotivo = MAX_MOTIVO;
  readonly headers = ['Alojamiento', 'Anfitrión', 'Ciudad', 'Tipo', 'Precio / noche', 'Capacidad', 'Estado', 'Acciones'];

  readonly publicaciones = signal<PublicacionAdmin[]>([]);
  readonly cargando = signal(true);
  readonly errorCarga = signal(false);
  readonly mensaje = signal('');

  readonly busqueda = signal('');
  readonly filtroEstado = signal('PENDIENTE_REVISION');
  readonly pagina = signal(0);

  readonly filtradas = computed(() => {
    const termino = this.busqueda().trim().toLowerCase();
    return this.publicaciones().filter(
      (p) =>
        `${p.titulo} ${p.nombreAnfitrion ?? ''} ${p.ciudad ?? ''}`.toLowerCase().includes(termino) &&
        (this.filtroEstado() === 'Todos' || estadoDe(p) === this.filtroEstado()),
    );
  });
  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.filtradas().length / this.TAMANIO)));
  readonly paginaActual = computed(() => Math.min(this.pagina(), this.totalPaginas() - 1));
  readonly visibles = computed(() => {
    const inicio = this.paginaActual() * this.TAMANIO;
    return this.filtradas().slice(inicio, inicio + this.TAMANIO);
  });

  readonly pendientes = computed(() => this.contar('PENDIENTE_REVISION'));
  readonly activas = computed(() => this.contar('ACTIVA'));
  readonly enBorrador = computed(() => this.contar('BORRADOR'));

  // ---- Modal de aprobar / rechazar ----
  readonly accion = signal<{ tipo: AccionRevision; publicacion: PublicacionAdmin } | null>(null);
  readonly enviando = signal(false);
  readonly errorAccion = signal<string | null>(null);
  readonly motivo = signal('');
  readonly config = computed(() => {
    const a = this.accion();
    return a ? ACCIONES[a.tipo] : null;
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
    this.cargando.set(true);
    this.errorCarga.set(false);
    this.admin.todas().subscribe({
      next: (lista) => {
        this.publicaciones.set(lista);
        this.cargando.set(false);
      },
      error: () => {
        this.errorCarga.set(true);
        this.cargando.set(false);
      },
    });
  }

  buscar(valor: string): void {
    this.busqueda.set(valor);
    this.pagina.set(0);
  }

  filtrarEstado(valor: string): void {
    this.filtroEstado.set(valor);
    this.pagina.set(0);
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas()) {
      this.pagina.set(pagina);
    }
  }

  verDetalle(id: number | undefined): void {
    if (id != null) {
      void this.router.navigate(['/admin/alojamientos', id]);
    }
  }

  accionesDe(p: PublicacionAdmin): AccionRevision[] {
    return accionesDe(estadoDe(p));
  }

  etiqueta(p: PublicacionAdmin): string {
    return etiquetaEstado(estadoDe(p));
  }

  badge(p: PublicacionAdmin): string {
    return claseBadge(estadoDe(p));
  }

  pedirAccion(tipo: AccionRevision, publicacion: PublicacionAdmin): void {
    this.errorAccion.set(null);
    this.mensaje.set('');
    this.motivo.set('');
    this.accion.set({ tipo, publicacion });
  }

  cancelarAccion(): void {
    if (!this.enviando()) {
      this.accion.set(null);
    }
  }

  confirmarAccion(): void {
    const a = this.accion();
    const id = a?.publicacion.idAlojamiento;
    if (!a || id == null || !this.puedeConfirmar()) {
      return;
    }
    this.enviando.set(true);
    this.errorAccion.set(null);
    this.admin.ejecutar(a.tipo, id, this.motivo().trim()).subscribe({
      next: (actualizada) => {
        const nueva: PublicacionAdmin = { ...a.publicacion, ...actualizada, estado: ACCIONES[a.tipo].resultado };
        this.publicaciones.update((lista) => lista.map((p) => (p.idAlojamiento === id ? nueva : p)));
        this.mensaje.set(
          a.tipo === 'aprobar'
            ? `"${a.publicacion.titulo}" fue aprobada y ya está publicada.`
            : `"${a.publicacion.titulo}" fue rechazada y volvió a borrador.`,
        );
        this.enviando.set(false);
        this.accion.set(null);
      },
      error: (error: unknown) => {
        this.errorAccion.set(this.admin.mensajeDeError(error, 'No se pudo completar la acción.'));
        this.enviando.set(false);
      },
    });
  }

  private contar(estado: string): number {
    return this.publicaciones().filter((p) => estadoDe(p) === estado).length;
  }
}
