import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { Notificacion, tituloNotificacion } from '../models/notificacion';
import { NotificacionesService } from '../services/notificaciones.service';

/**
 * Centro de notificaciones del usuario (HU-02): lista de la más reciente a la más antigua,
 * marcar una o todas como leídas y estado vacío (CA-010, CA-013).
 */
@Component({
  selector: 'app-mis-notificaciones',
  standalone: true,
  templateUrl: './mis-notificaciones.html',
})
export class MisNotificaciones implements OnInit, OnDestroy {
  private readonly notificaciones = inject(NotificacionesService);

  // ==================== DATOS ====================
  readonly items = signal<Notificacion[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal(false);
  readonly sinLeer = computed(() => this.items().filter((n) => !n.leida).length);

  // ==================== ACCIONES ====================
  readonly marcando = signal(false);
  readonly aviso = signal('');
  readonly errorAccion = signal('');
  private temporizadorAviso?: ReturnType<typeof setTimeout>;

  // ==================== INICIALIZACIÓN ====================
  ngOnInit(): void {
    this.cargarNotificaciones();
  }

  ngOnDestroy(): void {
    clearTimeout(this.temporizadorAviso);
  }

  reintentar(): void {
    this.cargarNotificaciones();
  }

  private cargarNotificaciones(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    this.notificaciones.listar().subscribe({
      next: (lista) => {
        // El backend ya las ordena; se asegura el orden de la más reciente a la más antigua (CA-013)
        this.items.set([...(lista ?? [])].sort((a, b) => (b.fecha ?? '').localeCompare(a.fecha ?? '')));
        this.notificaciones.sinLeer.set(this.sinLeer());
        this.loading.set(false);
      },
      error: () => {
        this.items.set([]);
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  // ==================== MARCAR COMO LEÍDAS ====================
  /** Al hacer clic en una notificación sin leer, se marca como leída. */
  abrir(notificacion: Notificacion): void {
    if (notificacion.leida) {
      return;
    }
    this.errorAccion.set('');
    this.notificaciones.marcarLeida(notificacion.idNotificacion).subscribe({
      next: () =>
        this.items.update((lista) =>
          lista.map((n) => (n.idNotificacion === notificacion.idNotificacion ? { ...n, leida: true } : n)),
        ),
      error: (error: unknown) =>
        this.errorAccion.set(
          this.notificaciones.mensajeDeError(error, 'No se pudo marcar la notificación como leída.'),
        ),
    });
  }

  marcarTodasLeidas(): void {
    if (this.marcando() || this.sinLeer() === 0) {
      return;
    }
    this.marcando.set(true);
    this.errorAccion.set('');
    this.notificaciones.marcarTodasLeidas().subscribe({
      next: () => {
        this.items.update((lista) => lista.map((n) => ({ ...n, leida: true })));
        this.marcando.set(false);
        this.mostrarAviso('Todas las notificaciones fueron marcadas como leídas.');
      },
      error: (error: unknown) => {
        this.errorAccion.set(
          this.notificaciones.mensajeDeError(error, 'No se pudieron marcar las notificaciones como leídas.'),
        );
        this.marcando.set(false);
      },
    });
  }

  private mostrarAviso(texto: string): void {
    clearTimeout(this.temporizadorAviso);
    this.aviso.set(texto);
    this.temporizadorAviso = setTimeout(() => this.aviso.set(''), 4000);
  }

  // ==================== PRESENTACIÓN ====================
  titulo(notificacion: Notificacion): string {
    return tituloNotificacion(notificacion.tipo);
  }

  claseItem(notificacion: Notificacion): string {
    return (
      'flex w-full gap-4 border-b border-ink-100 p-6 text-left last:border-0 hover:bg-ink-50 ' +
      (notificacion.leida ? '' : 'bg-brand-50/40')
    );
  }

  /** Tiempo transcurrido en texto corto: Ahora, Hace 5 min, Hace 3 h, Hace 2 d o la fecha. */
  tiempo(fecha: string | undefined): string {
    if (!fecha) {
      return '';
    }
    const instante = new Date(fecha);
    if (Number.isNaN(instante.getTime())) {
      return '';
    }
    const segundos = Math.round((Date.now() - instante.getTime()) / 1000);
    if (segundos < 60) return 'Ahora';
    const minutos = Math.floor(segundos / 60);
    if (minutos < 60) return `Hace ${minutos} min`;
    const horas = Math.floor(minutos / 60);
    if (horas < 24) return `Hace ${horas} h`;
    const dias = Math.floor(horas / 24);
    if (dias < 7) return `Hace ${dias} d`;
    return instante.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
