import { DatePipe } from '@angular/common';
import { Component, HostListener, OnInit, computed, inject, signal } from '@angular/core';

import { EventoAuditoria, FiltrosAuditoria } from '../models/evento-auditoria';
import {
  ACCIONES_AUDITORIA,
  AuditoriaService,
  claseResultado,
  etiquetaResultado,
} from '../services/auditoria.service';
import { AdminUsuariosService, etiquetaRol } from '../../administracion/services/admin-usuarios.service';

/** Eventos por página. */
const TAMANIO = 20;

/** Nombre y rol de un usuario, para mostrar quién hizo cada acción. */
interface Actor {
  nombre: string;
  rol: string;
}

/**
 * Consulta de eventos de auditoría del administrador (HU-13).
 * Tabla paginada con filtros por acción, actor y fechas, y detalle de cada evento (CA-062).
 * Solo administradores: la ruta usa roleGuard y el backend responde 403 a otros roles (CA-065).
 */
@Component({
  selector: 'app-consultar-auditoria',
  standalone: true,
  templateUrl: './consultar-auditoria.html',
  imports: [DatePipe],
})
export class ConsultarAuditoria implements OnInit {
  private readonly auditoria = inject(AuditoriaService);
  private readonly usuarios = inject(AdminUsuariosService);

  readonly headers = ['Fecha y hora', 'Usuario', 'Rol', 'Acción', 'Entidad', 'Resultado', 'Detalle'];
  readonly acciones = ACCIONES_AUDITORIA;

  readonly auditEvents = signal<EventoAuditoria[]>([]);
  readonly loading = signal(true);
  readonly errorCarga = signal<string | null>(null);
  readonly errorFiltro = signal<string | null>(null);
  readonly pagina = signal(0);
  readonly totalPaginas = signal(1);
  readonly totalElementos = signal(0);

  // Filtros: se aplican al pulsar "Filtrar" para no consultar en cada tecla
  readonly accion = signal('');
  readonly idActor = signal('');
  readonly desde = signal('');
  readonly hasta = signal('');

  /** Usuarios por id (informativo: si no carga, se muestra el id). */
  private readonly actores = signal(new Map<number, Actor>());

  readonly paginasVisibles = computed(() => {
    const total = this.totalPaginas();
    const inicio = Math.max(0, Math.min(this.pagina() - 2, total - 5));
    return Array.from({ length: Math.min(total, inicio + 5) - inicio }, (_, i) => inicio + i);
  });

  // ---- Detalle de un evento ----
  readonly eventoDetalle = signal<EventoAuditoria | null>(null);
  readonly cargandoDetalle = signal(false);
  readonly errorDetalle = signal(false);
  readonly detalleAbierto = signal(false);
  readonly camposDetalle = computed(() => {
    const e = this.eventoDetalle();
    if (!e) {
      return [];
    }
    return [
      { clave: 'Evento', valor: `#${e.idEvento}` },
      { clave: 'Fecha y hora', valor: this.fechaTexto(e.fecha) },
      { clave: 'Acción', valor: e.accion },
      { clave: 'Resultado', valor: etiquetaResultado(e.resultado) },
      { clave: 'Usuario', valor: this.usuario(e) },
      { clave: 'Rol', valor: this.rol(e) || '—' },
      { clave: 'Entidad', valor: this.entidad(e) || '—' },
      { clave: 'Detalle', valor: e.detalle || '—' },
    ];
  });

  ngOnInit(): void {
    this.cargarActores();
    this.cargar();
  }

  cargar(): void {
    this.loading.set(true);
    this.errorCarga.set(null);
    this.auditoria.pagina(this.pagina(), TAMANIO, this.filtros()).subscribe({
      next: (p) => {
        this.auditEvents.set(p.contenido);
        this.totalPaginas.set(Math.max(1, p.totalPaginas));
        this.totalElementos.set(p.totalElementos);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.auditEvents.set([]);
        this.totalElementos.set(0);
        this.errorCarga.set(this.auditoria.mensajeDeError(error, 'No se pudieron cargar los eventos de auditoría.'));
        this.loading.set(false);
      },
    });
  }

  filtrar(): void {
    const actor = this.idActor().trim();
    if (actor && !(Number.isInteger(Number(actor)) && Number(actor) > 0)) {
      this.errorFiltro.set('El ID del actor debe ser un número entero positivo.');
      return;
    }
    if (this.desde() && this.hasta() && this.desde() > this.hasta()) {
      this.errorFiltro.set('La fecha "Desde" no puede ser posterior a "Hasta".');
      return;
    }
    this.errorFiltro.set(null);
    this.pagina.set(0);
    this.cargar();
  }

  limpiar(): void {
    this.accion.set('');
    this.idActor.set('');
    this.desde.set('');
    this.hasta.set('');
    this.filtrar();
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas() && pagina !== this.pagina()) {
      this.pagina.set(pagina);
      this.cargar();
    }
  }

  verDetalle(e: EventoAuditoria): void {
    this.detalleAbierto.set(true);
    this.errorDetalle.set(false);
    this.eventoDetalle.set(e);
    this.cargandoDetalle.set(true);
    this.auditoria.evento(e.idEvento).subscribe({
      next: (ev) => {
        this.eventoDetalle.set(ev ?? e);
        this.cargandoDetalle.set(false);
      },
      error: () => {
        this.errorDetalle.set(true);
        this.cargandoDetalle.set(false);
      },
    });
  }

  @HostListener('document:keydown.escape')
  cerrarDetalle(): void {
    this.detalleAbierto.set(false);
  }

  // ---- Lectura de campos ----
  usuario(e: EventoAuditoria): string {
    if (e.idUsuario == null) {
      return 'Sistema';
    }
    const actor = this.actores().get(e.idUsuario);
    return actor ? `${actor.nombre} (#${e.idUsuario})` : `Usuario #${e.idUsuario}`;
  }

  rol(e: EventoAuditoria): string {
    return e.idUsuario != null ? (this.actores().get(e.idUsuario)?.rol ?? '') : '';
  }

  entidad(e: EventoAuditoria): string {
    const tipo = e.entidad ?? '';
    return e.idEntidad != null ? `${tipo} #${e.idEntidad}` : tipo;
  }

  resultado(e: EventoAuditoria): string {
    return etiquetaResultado(e.resultado);
  }

  badgeClass(e: EventoAuditoria): string {
    return claseResultado(e.resultado);
  }

  private fechaTexto(fecha: string): string {
    const d = new Date(fecha);
    return Number.isNaN(d.getTime()) ? fecha : d.toLocaleString('es-CO');
  }

  private filtros(): FiltrosAuditoria {
    const actor = Number(this.idActor().trim());
    return {
      accion: this.accion().trim().toUpperCase() || undefined,
      idActor: Number.isInteger(actor) && actor > 0 ? actor : undefined,
      desde: this.desde() ? `${this.desde()}T00:00:00` : undefined,
      hasta: this.hasta() ? `${this.hasta()}T23:59:59` : undefined,
    };
  }

  private cargarActores(): void {
    this.usuarios.usuarios().subscribe({
      next: (lista) =>
        this.actores.set(new Map(lista.map((u) => [u.idUsuario, { nombre: u.nombre, rol: etiquetaRol(u.rol) }]))),
      error: () => this.actores.set(new Map()),
    });
  }
}
