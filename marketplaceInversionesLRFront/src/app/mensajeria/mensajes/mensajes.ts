import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { interval } from 'rxjs';

import { SessionService } from '../../core/services/session.service';
import { Conversacion, MAX_MENSAJE, Mensaje } from '../models/mensajeria';
import { MensajeriaService } from '../services/mensajeria.service';

/** Mensajes de un mismo día. */
interface GrupoDia {
  etiqueta: string;
  mensajes: Mensaje[];
}

/** Cada cuánto se buscan mensajes nuevos (el backend no tiene tiempo real). */
const REFRESCO_MS = 10_000;

/**
 * Bandeja de conversaciones y chat entre huésped y anfitrión (HU-28).
 * La usan /mensajes (huésped) y /host/chat (anfitrión). Cada mensaje muestra quién lo envió y
 * cuándo (CA-165); solo se ven las conversaciones propias (CA-167); ?conversacion=ID abre la que
 * se acaba de iniciar desde el detalle del alojamiento (CA-169).
 */
@Component({
  selector: 'app-mensajes',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './mensajes.html',
})
export class Mensajes implements OnInit {
  private readonly service = inject(MensajeriaService);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly MAX_CARACTERES = MAX_MENSAJE;
  readonly esAnfitrion = computed(() => this.session.rol() === 'Anfitrión');

  /** Sube cada vez que el usuario envía un mensaje: descarta refrescos que ya quedaron viejos. */
  private versionMensajes = 0;

  readonly conversations = signal<Conversacion[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly messages = signal<Mensaje[]>([]);
  readonly draft = signal('');
  readonly search = signal('');
  readonly loading = signal(true);
  readonly errorCarga = signal(false);
  readonly loadingMessages = signal(false);
  readonly errorMensajes = signal<string | null>(null);
  readonly sending = signal(false);
  readonly errorEnvio = signal<string | null>(null);

  readonly filtered = computed(() => {
    const term = this.search().trim().toLowerCase();
    return this.conversations().filter((c) => `${c.contacto} ${c.alojamiento}`.toLowerCase().includes(term));
  });
  readonly active = computed(() => this.conversations().find((c) => c.id === this.selectedId()) ?? null);

  /** Mensajes agrupados por día (Hoy, Ayer o la fecha). */
  readonly grupos = computed(() => {
    const grupos: GrupoDia[] = [];
    for (const m of this.messages()) {
      const etiqueta = this.etiquetaDia(m.fecha);
      const ultimo = grupos.at(-1);
      if (ultimo?.etiqueta === etiqueta) {
        ultimo.mensajes.push(m);
      } else {
        grupos.push({ etiqueta, mensajes: [m] });
      }
    }
    return grupos;
  });

  ngOnInit(): void {
    this.cargarConversaciones();
    interval(REFRESCO_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refrescar());
  }

  cargarConversaciones(): void {
    this.loading.set(true);
    this.errorCarga.set(false);
    this.service.conversaciones().subscribe({
      next: (lista) => {
        this.conversations.set(lista);
        this.loading.set(false);
        // ?conversacion=ID abre esa conversación (viene de "Contactar al anfitrión")
        const pedida = Number(this.route.snapshot.queryParamMap.get('conversacion'));
        const actual = this.selectedId();
        const destino =
          lista.find((c) => c.id === actual) ?? (pedida > 0 ? lista.find((c) => c.id === pedida) : undefined) ?? lista[0];
        if (destino && destino.id !== actual) {
          this.select(destino.id);
        }
      },
      error: () => {
        this.errorCarga.set(true);
        this.loading.set(false);
      },
    });
  }

  select(id: number): void {
    this.selectedId.set(id);
    this.errorEnvio.set(null);
    this.cargarMensajes(id, false);
  }

  private cargarMensajes(id: number, silencioso: boolean): void {
    const conversacion = this.conversations().find((c) => c.id === id);
    if (!conversacion) {
      return;
    }
    if (!silencioso) {
      this.loadingMessages.set(true);
      this.errorMensajes.set(null);
      this.messages.set([]);
    }
    const version = this.versionMensajes;
    this.service.mensajes(conversacion).subscribe({
      next: (lista) => {
        if (this.selectedId() !== id || (silencioso && version !== this.versionMensajes)) {
          return; // cambió de conversación o envió un mensaje mientras llegaba esta respuesta
        }
        const hayNuevos = lista.length !== this.messages().length;
        this.messages.set(lista);
        this.loadingMessages.set(false);
        if (hayNuevos) {
          this.irAlFinal();
        }
      },
      error: (error: unknown) => {
        if (!silencioso) {
          this.errorMensajes.set(this.service.mensajeDeError(error, 'No se pudieron cargar los mensajes.'));
          this.loadingMessages.set(false);
        }
      },
    });
  }

  /** Refresco periódico: solo si la pestaña está a la vista. */
  private refrescar(): void {
    if (document.hidden) {
      return;
    }
    this.service.conversaciones().subscribe({
      next: (lista) => {
        this.conversations.set(lista);
        this.errorCarga.set(false);
        this.loading.set(false);
      },
      error: () => undefined, // un fallo puntual no interrumpe: se reintenta en el siguiente ciclo
    });
    const id = this.selectedId();
    if (id !== null) {
      this.cargarMensajes(id, true);
    }
  }

  send(): void {
    const texto = this.draft().trim();
    const conversacion = this.active();
    if (!texto || !conversacion || this.sending()) {
      return;
    }
    if (texto.length > MAX_MENSAJE) {
      this.errorEnvio.set(`El mensaje no puede superar ${MAX_MENSAJE} caracteres.`);
      return;
    }
    this.sending.set(true);
    this.errorEnvio.set(null);
    this.service.enviar(conversacion, texto).subscribe({
      next: (mensaje) => {
        this.versionMensajes++;
        this.draft.set('');
        this.sending.set(false);
        if (this.selectedId() === conversacion.id) {
          this.messages.update((lista) => [...lista, { ...mensaje, mio: true }]);
          this.irAlFinal();
        }
      },
      error: (error: unknown) => {
        this.errorEnvio.set(this.service.mensajeDeError(error, 'No se pudo enviar el mensaje.'));
        this.sending.set(false); // el borrador se conserva para reintentar
      },
    });
  }

  /** Abre el detalle del alojamiento de la conversación. */
  irAlAlojamiento(c: Conversacion): void {
    void this.router.navigate(['/alojamientos', c.idAlojamiento]);
  }

  private irAlFinal(): void {
    setTimeout(() => {
      const el = this.scroller()?.nativeElement;
      if (el) {
        el.scrollTop = el.scrollHeight;
      }
    });
  }

  private etiquetaDia(iso: string): string {
    const fecha = new Date(iso);
    if (Number.isNaN(fecha.getTime())) {
      return '';
    }
    const dia = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const diferencia = Math.round((dia(new Date()) - dia(fecha)) / 86400000);
    if (diferencia === 0) return 'Hoy';
    if (diferencia === 1) return 'Ayer';
    return fecha.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  itemClass(c: Conversacion): string {
    return (
      'flex w-full gap-3 border-b border-slate-100 p-5 text-left ' +
      (this.selectedId() === c.id ? 'bg-emerald-50' : 'hover:bg-slate-50')
    );
  }

  bubbleClass(m: Mensaje): string {
    return (
      'max-w-[70%] rounded-2xl px-5 py-3 text-sm leading-6 break-words ' +
      (m.mio ? 'bg-emerald-700 text-white' : 'border border-slate-200 bg-white')
    );
  }

  timeClass(m: Mensaje): string {
    return 'mt-1 text-right text-[10px] ' + (m.mio ? 'text-emerald-100' : 'text-slate-400');
  }
}
