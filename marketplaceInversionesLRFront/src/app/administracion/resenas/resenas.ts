import { DatePipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';

import { Resena } from '../../resenas/models/resena';
import { AdminResenasService, MAX_MOTIVO_RESENA } from '../services/admin-resenas.service';

/** Reseñas por página. */
const TAMANIO = 10;

/**
 * Moderación de reseñas (HU-14, CA-066 y CA-069): copia de `pages/admin/reviews` del proyecto base.
 * El administrador oculta una reseña con un motivo o la vuelve a mostrar. La fila se actualiza con la
 * reseña que devuelve el backend; el backend la deja de mostrar en el alojamiento y en el promedio.
 */
@Component({
  selector: 'app-admin-resenas',
  standalone: true,
  templateUrl: './resenas.html',
  imports: [DatePipe],
})
export class ResenasAdmin implements OnInit {
  private readonly admin = inject(AdminResenasService);

  readonly headers = ['Huésped', 'Alojamiento', 'Reserva', 'Calificación', 'Fecha', 'Comentario', 'Estado', 'Acción'];
  readonly starList = [1, 2, 3, 4, 5];
  readonly filtros = [
    { valor: 'Todas', texto: 'Todas las reseñas' },
    { valor: 'Visible', texto: 'Visibles' },
    { valor: 'Oculta', texto: 'Ocultas' },
  ];
  readonly maxMotivo = MAX_MOTIVO_RESENA;

  readonly reviews = signal<Resena[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);
  readonly search = signal('');
  readonly estadoFiltro = signal('Todas');
  readonly pagina = signal(0);
  /** Id de la reseña que se está volviendo a mostrar. */
  readonly mostrando = signal<number | null>(null);
  readonly errorFila = signal<string | null>(null);

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    const filtro = this.estadoFiltro();
    return this.reviews().filter(
      (r) =>
        `${this.huesped(r)} ${this.alojamiento(r)} ${this.comentario(r)}`.toLowerCase().includes(term) &&
        (filtro === 'Todas' || (filtro === 'Oculta') === this.oculta(r)),
    );
  });
  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.filtered().length / TAMANIO)));
  readonly paginaActual = computed(() => Math.min(this.pagina(), this.totalPaginas() - 1));
  readonly visibles = computed(() => {
    const inicio = this.paginaActual() * TAMANIO;
    return this.filtered().slice(inicio, inicio + TAMANIO);
  });
  /** Hasta 5 números de página alrededor de la actual. */
  readonly paginasVisibles = computed(() => {
    const total = this.totalPaginas();
    const inicio = Math.max(0, Math.min(this.paginaActual() - 2, total - 5));
    return Array.from({ length: Math.min(total, inicio + 5) - inicio }, (_, i) => inicio + i);
  });

  // ---- Modal: ocultar reseña ----
  readonly aOcultar = signal<Resena | null>(null);
  readonly motivo = signal('');
  readonly enviando = signal(false);
  readonly errorAccion = signal<string | null>(null);
  readonly motivoValido = computed(() => {
    const largo = this.motivo().trim().length;
    return largo > 0 && largo <= MAX_MOTIVO_RESENA;
  });
  readonly puedeConfirmar = computed(() => !this.enviando() && this.motivoValido());

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    this.admin.todas().subscribe({
      next: (resenas) => {
        this.reviews.set(resenas);
        this.loading.set(false);
      },
      error: () => {
        this.reviews.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  // ---- Lectura de campos (ResenaDTO) ----
  huesped(r: Resena): string { return r.nombreUsuario ?? ''; }
  alojamiento(r: Resena): string { return r.tituloAlojamiento ?? ''; }
  reserva(r: Resena): string { return r.idReserva != null ? `#${r.idReserva}` : ''; }
  comentario(r: Resena): string { return r.comentario ?? ''; }
  oculta(r: Resena): boolean { return String(r.estado ?? '').toUpperCase() === 'OCULTA'; }

  // ---- Filtros y paginación ----
  buscar(valor: string): void { this.search.set(valor); this.pagina.set(0); }
  filtrarEstado(valor: string): void { this.estadoFiltro.set(valor); this.pagina.set(0); }
  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas()) {
      this.pagina.set(pagina);
    }
  }

  // ---- Moderación ----
  pedirOcultar(r: Resena): void {
    this.errorAccion.set(null);
    this.errorFila.set(null);
    this.motivo.set('');
    this.aOcultar.set(r);
  }

  cancelarOcultar(): void {
    if (!this.enviando()) {
      this.aOcultar.set(null);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.cancelarOcultar();
  }

  /** CA-066: PATCH /admin/resenas/{id}/ocultar; la fila cambia solo cuando el backend lo confirma. */
  confirmarOcultar(): void {
    const r = this.aOcultar();
    if (!r || !this.puedeConfirmar()) {
      return;
    }
    this.enviando.set(true);
    this.errorAccion.set(null);
    this.admin.ocultar(r.idResena, this.motivo().trim()).subscribe({
      next: (actualizada) => {
        this.reemplazar(r, actualizada);
        this.enviando.set(false);
        this.aOcultar.set(null);
      },
      error: (error) => {
        this.errorAccion.set(this.admin.mensajeDeError(error, 'No se pudo ocultar la reseña.'));
        this.enviando.set(false);
      },
    });
  }

  mostrar(r: Resena): void {
    if (this.mostrando() !== null) {
      return;
    }
    this.mostrando.set(r.idResena);
    this.errorFila.set(null);
    this.admin.mostrar(r.idResena).subscribe({
      next: (actualizada) => {
        this.reemplazar(r, actualizada);
        this.mostrando.set(null);
      },
      error: (error) => {
        this.errorFila.set(this.admin.mensajeDeError(error, 'No se pudo mostrar la reseña.'));
        this.mostrando.set(null);
      },
    });
  }

  private reemplazar(original: Resena, actualizada: Resena): void {
    this.reviews.update((lista) =>
      lista.map((x) => (x.idResena === original.idResena ? { ...x, ...actualizada } : x)),
    );
  }
}
