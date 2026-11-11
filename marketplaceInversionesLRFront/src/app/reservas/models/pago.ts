// ======================================
// PAGO DE PRUEBA (HU-27)
// GET /pagos/configuracion (ConfiguracionPagoDTO y MetodoPagoPruebaDTO del backend)
// ======================================

/** STRIPE_TEST: cobros en el modo de prueba de Stripe. SIMULADO: sin clave de Stripe. */
export type ModoPago = 'STRIPE_TEST' | 'SIMULADO';

/** Método de pago de prueba y el resultado que produce. */
export interface MetodoPagoPrueba {
  /** PaymentMethod de prueba que se envía en POST /reservas, por ejemplo pm_card_visa. */
  metodo: string;
  descripcion: string;
  resultado: 'APROBADO' | 'RECHAZADO' | 'ERROR' | string;
}

/** Configuración pública de pagos. */
export interface ConfiguracionPago {
  modo: ModoPago | string;
  /** Clave publicable de Stripe (solo en STRIPE_TEST). */
  clavePublica: string | null;
  moneda: string;
  metodosPrueba: MetodoPagoPrueba[];
}
