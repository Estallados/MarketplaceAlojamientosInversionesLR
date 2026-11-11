import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';

import { Cotizacion } from '../../cotizacion/models/cotizacion';
import { ConfiguracionPago } from '../models/pago';
import { PagoService } from '../services/pago.service';
import {
  TARJETAS_PRUEBA,
  cvcValido,
  formatearNumero,
  formatearVencimiento,
  marcaTarjeta,
  metodoDeTarjeta,
  numeroValido,
  soloDigitos,
  vencimientoValido
} from '../validaciones/tarjetas-prueba';

const ETIQUETAS_MARCA: Record<string, string> = {
  visa: 'Visa',
  mastercard: 'Mastercard',
  amex: 'American Express'
};

/** Tarjeta de prueba que se muestra como ayuda, con el resultado que da el backend. */
export interface AyudaTarjeta {
  numero: string;
  formateado: string;
  descripcion: string;
  aprobada: boolean;
}

// ======================================
// MODAL "CONFIRMA Y PAGA" (HU-27)
// Copia el modal pago-reserva del proyecto base. Se escriben los datos de
// una tarjeta de prueba (número, vencimiento, CVC y titular), que solo se
// validan aquí y nunca se envían ni se guardan. El número elige el método
// de prueba del backend (como las tarjetas de prueba de Stripe) y una
// tarjeta que no sea de prueba se rechaza sin llamar al backend (CA-160).
// El cobro lo hace POST /reservas en Confirmar reserva con (pagar).
// TODO(HU-27): con modo STRIPE_TEST se podría usar Stripe Elements
// (@stripe/stripe-js) en lugar de este formulario, como el proyecto base.
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

  /** Emite el método de prueba que corresponde a la tarjeta escrita. */
  readonly pagar = output<string>();

  readonly cerrado = output<void>();

  readonly config = signal<ConfiguracionPago | null>(null);

  readonly cargandoConfig = signal(true);

  readonly errorConfig = signal<string | null>(null);

  // Datos de la tarjeta: solo viven en este componente.
  readonly titular = signal('');

  readonly numero = signal('');

  readonly vencimiento = signal('');

  readonly cvc = signal('');

  readonly digitos = computed(() => soloDigitos(this.numero()));

  readonly marca = computed(() => marcaTarjeta(this.digitos()));

  readonly etiquetaMarca = computed(() => ETIQUETAS_MARCA[this.marca()] ?? '');

  private readonly metodosDisponibles = computed(() =>
    (this.config()?.metodosPrueba ?? []).map(m => m.metodo)
  );

  /** Método de prueba de la tarjeta escrita; null si no es una tarjeta de prueba disponible. */
  readonly metodo = computed(() => metodoDeTarjeta(this.digitos(), this.metodosDisponibles()));

  readonly errorNumero = computed(() => {
    const d = this.digitos();
    if (!numeroValido(d)) {
      return d.length >= 13 ? 'El número de tarjeta no es válido.' : null;
    }
    return this.metodo() === null
      ? 'Usa una tarjeta de prueba: en este ambiente no se aceptan tarjetas reales.'
      : null;
  });

  readonly errorVencimiento = computed(() => {
    const v = this.vencimiento();
    if (vencimientoValido(v)) {
      return null;
    }
    return v.length === 5 ? 'La fecha no es válida o la tarjeta está vencida.' : null;
  });

  readonly datosValidos = computed(() =>
    this.metodo() !== null && vencimientoValido(this.vencimiento()) && cvcValido(this.cvc(), this.marca())
  );

  /** Tarjetas de prueba que ofrece el backend, para quien hace la demostración. */
  readonly ayuda = computed<AyudaTarjeta[]>(() => {
    const metodos = this.config()?.metodosPrueba ?? [];
    return TARJETAS_PRUEBA.flatMap(t => {
      const m = metodos.find(x => x.metodo === t.metodo);
      return m
        ? [{
          numero: t.numero,
          formateado: formatearNumero(t.numero),
          descripcion: m.descripcion,
          aprobada: m.resultado === 'APROBADO'
        }]
        : [];
    });
  });

  readonly modoStripe = computed(() => this.config()?.modo === 'STRIPE_TEST');

  readonly moneda = computed(() => (this.config()?.moneda ?? 'COP').toUpperCase());

  readonly puedePagar = computed(() => !this.pagando() && !!this.config() && this.datosValidos());

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
      },
      error: () => {
        this.cargandoConfig.set(false);
        this.errorConfig.set('No se pudo cargar la configuración de pagos. Inténtalo de nuevo.');
      }
    });
  }

  // ---- Campos: se formatean mientras se escribe ----

  onNumero(campo: HTMLInputElement): void {
    const formateado = formatearNumero(soloDigitos(campo.value));
    campo.value = formateado;
    this.numero.set(formateado);
  }

  onVencimiento(campo: HTMLInputElement): void {
    const formateado = formatearVencimiento(campo.value);
    campo.value = formateado;
    this.vencimiento.set(formateado);
  }

  onCvc(campo: HTMLInputElement): void {
    const d = soloDigitos(campo.value).slice(0, 4);
    campo.value = d;
    this.cvc.set(d);
  }

  /** Escribe una tarjeta de la ayuda en el campo del número. */
  usarTarjeta(numero: string): void {
    if (!this.pagando()) {
      this.numero.set(formatearNumero(numero));
    }
  }

  confirmarPago(): void {

    const metodo = this.metodo();

    if (this.puedePagar() && metodo) {
      this.pagar.emit(metodo);
    }
  }

  cerrar(): void {
    if (!this.pagando()) {
      this.cerrado.emit();
    }
  }
}
