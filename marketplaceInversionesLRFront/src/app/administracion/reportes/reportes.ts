import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';

import { FormatoReporte, RegistroExportacion, TIPOS_REPORTE, TipoReporte } from '../models/reporte';
import { AdminReportesService } from '../services/admin-reportes.service';

/** Registros del historial por página. */
const TAMANIO = 20;

/**
 * Reportes de gestión (HU-05), con el diseño del proyecto base: elegir el tipo de reporte, exportarlo en
 * PDF o Excel y consultar el historial de exportaciones con quién y cuándo las pidió (CA-027).
 */
@Component({
  selector: 'app-admin-reportes',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './reportes.html',
})
export class ReportesAdmin implements OnInit, OnDestroy {
  private readonly admin = inject(AdminReportesService);

  readonly reportTypes = TIPOS_REPORTE;
  readonly historyHeaders = ['Reporte', 'Formato', 'Registros', 'Usuario solicitante', 'Fecha y hora'];

  readonly report = signal<TipoReporte>('reservas');
  /** Formato que se está exportando (null si no hay exportación en curso). */
  readonly exportando = signal<FormatoReporte | null>(null);
  readonly mensaje = signal<{ ok: boolean; texto: string } | null>(null);

  readonly history = signal<RegistroExportacion[]>([]);
  readonly loadingHistory = signal(true);
  readonly errorHistory = signal(false);
  readonly pagina = signal(0);
  readonly totalPaginas = signal(1);
  readonly totalElementos = signal(0);

  readonly paginasVisibles = computed(() => {
    const total = this.totalPaginas();
    const inicio = Math.max(0, Math.min(this.pagina() - 2, total - 5));
    return Array.from({ length: Math.min(total, inicio + 5) - inicio }, (_, i) => inicio + i);
  });

  private urlsPendientes: string[] = [];

  ngOnInit(): void {
    this.cargarHistorial();
  }

  ngOnDestroy(): void {
    this.urlsPendientes.forEach((u) => URL.revokeObjectURL(u));
  }

  elegirTipo(valor: string): void {
    this.report.set(valor as TipoReporte);
    this.mensaje.set(null);
  }

  cargarHistorial(): void {
    this.loadingHistory.set(true);
    this.errorHistory.set(false);
    this.admin.historial(this.pagina(), TAMANIO).subscribe({
      next: (p) => {
        this.history.set(p.contenido);
        this.totalPaginas.set(Math.max(1, p.totalPaginas));
        this.totalElementos.set(p.totalElementos);
        this.loadingHistory.set(false);
      },
      error: () => {
        this.history.set([]);
        this.errorHistory.set(true);
        this.loadingHistory.set(false);
      },
    });
  }

  irAPagina(pagina: number): void {
    if (pagina >= 0 && pagina < this.totalPaginas() && pagina !== this.pagina()) {
      this.pagina.set(pagina);
      this.cargarHistorial();
    }
  }

  exportReport(formato: FormatoReporte): void {
    if (this.exportando()) {
      return;
    }
    this.exportando.set(formato);
    this.mensaje.set(null);
    this.admin.exportar(this.report(), formato).subscribe({
      next: ({ blob, nombre }) => {
        this.exportando.set(null);
        if (!blob || blob.size === 0) {
          this.mensaje.set({ ok: false, texto: 'El servidor devolvió un archivo vacío.' });
          return;
        }
        this.descargar(blob, nombre);
        this.mensaje.set({
          ok: true,
          texto: `Reporte exportado en ${formato === 'pdf' ? 'PDF' : 'Excel'} correctamente.`,
        });
        this.pagina.set(0);
        this.cargarHistorial(); // la exportación quedó registrada (CA-027)
      },
      error: (error: unknown) => {
        this.mensaje.set({ ok: false, texto: this.admin.mensajeDeError(error, 'No se pudo exportar el reporte.') });
        this.exportando.set(null);
      },
    });
  }

  // ---- Presentación del historial ----
  reporteDe(r: RegistroExportacion): string {
    const valor = (r.tipoReporte ?? '').toLowerCase();
    return this.reportTypes.find((t) => t.valor === valor)?.texto ?? r.tipoReporte;
  }

  formatoDe(r: RegistroExportacion): string {
    const f = (r.formato ?? '').toLowerCase();
    return f.startsWith('pdf') ? 'PDF' : f.startsWith('exc') || f.startsWith('xls') ? 'Excel' : f.toUpperCase();
  }

  usuarioDe(r: RegistroExportacion): string {
    return r.nombreUsuario || (r.idUsuario != null ? `Usuario #${r.idUsuario}` : '—');
  }

  private descargar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    document.body.appendChild(enlace); // Firefox exige que el enlace esté en el documento
    enlace.click();
    enlace.remove();
    // Se libera después: revocarlo de inmediato cancela la descarga en algunos navegadores
    this.urlsPendientes.push(url);
    setTimeout(() => {
      URL.revokeObjectURL(url);
      this.urlsPendientes = this.urlsPendientes.filter((u) => u !== url);
    }, 1000);
  }
}
