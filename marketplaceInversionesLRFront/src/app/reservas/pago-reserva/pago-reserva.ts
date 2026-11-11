import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';

import { Cotizacion } from '../../cotizacion/models/cotizacion';
import { ConfiguracionPago, MetodoPagoPrueba } from '../models/pago';
import { PagoService } from '../services/pago.service';

/** Texto de cada motivo de rechazo según el sufijo del método de prueba de Stripe (como en el proyecto base). */
const MOTIVOS_RECHAZO: Record<string, string> = {
  InsufficientFunds: 'fondos insuficientes',
  LostCard: 'tarjeta reportada como perdida',
  StolenCard: 'tarjeta reportada como robada',
  ExpiredCard: 'tarjeta vencida',
  IncorrectCvc: 'CVC incorrecto',
  IncorrectNumber: 'número incorrecto',
  ProcessingError: 'error de procesamiento',
};

/** Método aprobado que se usa para "Tarjeta válida" si el backend lo ofrece. */
const METODO_APROBADO_PREFERIDO = 'pm_card_visa';

/** Opción del selector: el método de prueba que se envía y su texto. */
export interface OpcionPago {
  metodo: string;
  texto: string;
}

// ======================================
// MODAL "CONFIRMA Y PAGA" (HU-27)
// Copia el modal pago-reserva del proyecto base. Solo usa los métodos de
// prueba que devuelve GET /pagos/configuracion: no pide datos de tarjeta y
// nunca hace un cobro real (CA-160). El cobro lo hace POST /reservas en la
// pantalla Confirmar reserva, que recibe el método elegido con (pagar).
// TODO(HU-27): con modo STRIPE_TEST se podría agregar Stripe Elements
// (@stripe/stripe-js) para escribir tarjetas de prueba, como el proyecto base.
// ======================================

@Component({
  selector: 'app-pago-reserva',
  standalone: true,
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './pago-reserva.html'
})
export class PagoReserva implements OnInit {

  private readonly pagos = inject(PagoService);

  readonly cotizacion = input.required<Cotizacion>();

  readonly titulo = input('');

  /** true mientras POST /reservas está en curso. */
  readonly pagando = input(false);

  /** Motivo del último rechazo del pago (CA-162). */
  readonly error = input<string | null>(null);

  readonly pagar = output<string>();

  readonly cerrado = output<void>();

  readonly config = signal<ConfiguracionPago | null>(null);

  readonly cargandoConfig = signal(true);

  readonly errorConfig = signal<string | null>(null);

  readonly metodoElegido = signal('');

  /** Tarjeta válida y luego cada motivo de rechazo o error que ofrece el backend. */
  readonly resultados = computed<OpcionPago[]>(() => {
    const metodos = this.config()?.metodosPrueba ?? [];
    const aprobados = metodos.filter(m => m.resultado === 'APROBADO');
    const opciones: OpcionPago[] = [];
    const aprobado = aprobados.find(m => m.metodo === METODO_APROBADO_PREFERIDO) ?? aprobados[0];
    if (aprobado) {
      opciones.push({ metodo: aprobado.metodo, texto: 'Tarjeta válida · pago aprobado' });
    }
    for (const m of metodos.filter(x => x.resultado !== 'APROBADO')) {
      opciones.push({ metodo: m.metodo, texto: `Tarjeta no válida · ${this.motivoRechazo(m)}` });
    }
    return opciones;
  });

  readonly modoStripe = computed(() => this.config()?.modo === 'STRIPE_TEST');

  readonly moneda = computed(() => (this.config()?.moneda ?? 'COP').toUpperCase());

  readonly puedePagar = computed(() => !this.pagando() && !!this.config() && !!this.metodoElegido());

  ngOnInit(): void {
    this.cargarConfiguracion();
  }

  cargarConfiguracion(): void {

    this.cargandoConfig.set(true);
    this.errorConfig.set(null);

    this.pagos.configuracion().subscribe({
      next: config => {
        this.config.set(config);
        this.cargandoConfig.set(false);
        this.metodoElegido.set(this.resultados()[0]?.metodo ?? '');
      },
      error: () => {
        this.cargandoConfig.set(false);
        this.errorConfig.set('No se pudo cargar la configuración de pagos. Inténtalo de nuevo.');
      }
    });
  }

  elegirMetodo(metodo: string): void {
    this.metodoElegido.set(metodo);
  }

  confirmarPago(): void {
    if (this.puedePagar()) {
      this.pagar.emit(this.metodoElegido());
    }
  }

  cerrar(): void {
    if (!this.pagando()) {
      this.cerrado.emit();
    }
  }

  private motivoRechazo(m: MetodoPagoPrueba): string {
    const sufijo = m.metodo.replace(/^pm_card_chargeDeclined/, '');
    if (MOTIVOS_RECHAZO[sufijo]) {
      return MOTIVOS_RECHAZO[sufijo];
    }
    return m.metodo === 'pm_card_chargeDeclined' ? 'tarjeta rechazada' : (m.descripcion || m.metodo).toLowerCase();
  }
}
