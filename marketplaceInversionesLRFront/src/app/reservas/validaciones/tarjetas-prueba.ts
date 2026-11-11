// ======================================
// TARJETAS DE PRUEBA (HU-27)
// Números de https://docs.stripe.com/testing y el método de prueba del
// backend (GET /pagos/configuracion) que produce el mismo resultado.
// Los datos de la tarjeta solo se validan en el navegador: al backend
// únicamente viaja el método de prueba (pm_card_...), nunca la tarjeta.
// ======================================

export type MarcaTarjeta = 'visa' | 'mastercard' | 'amex' | '';

/** Tarjeta de prueba y el método del backend que le corresponde. */
export interface TarjetaPrueba {
  /** Solo dígitos. */
  numero: string;
  metodo: string;
}

/**
 * Número reservado por el proyecto (no es de Stripe ni de ninguna red) para simular una falla de la
 * pasarela; solo funciona con el simulador del backend (pm_card_error).
 */
export const NUMERO_FALLA_PASARELA = '9999999999999995';

export const TARJETAS_PRUEBA: readonly TarjetaPrueba[] = [
  { numero: '4242424242424242', metodo: 'pm_card_visa' },
  { numero: '4000056655665556', metodo: 'pm_card_visa_debit' },
  { numero: '5555555555554444', metodo: 'pm_card_mastercard' },
  { numero: '378282246310005', metodo: 'pm_card_amex' },
  { numero: '4000000000000002', metodo: 'pm_card_chargeDeclined' },
  { numero: '4000000000009995', metodo: 'pm_card_chargeDeclinedInsufficientFunds' },
  { numero: '4000000000009987', metodo: 'pm_card_chargeDeclinedLostCard' },
  { numero: '4000000000009979', metodo: 'pm_card_chargeDeclinedStolenCard' },
  { numero: '4000000000000069', metodo: 'pm_card_chargeDeclinedExpiredCard' },
  { numero: '4000000000000127', metodo: 'pm_card_chargeDeclinedIncorrectCvc' },
  { numero: '4000000000000119', metodo: 'pm_card_chargeDeclinedProcessingError' },
  { numero: '4000002760003184', metodo: 'pm_card_authenticationRequired' },
  { numero: NUMERO_FALLA_PASARELA, metodo: 'pm_card_error' }
];

/** Solo los dígitos de lo escrito. */
export function soloDigitos(valor: string): string {
  return valor.replace(/\D/g, '');
}

/** Marca según los primeros dígitos (como el proyecto base). */
export function marcaTarjeta(digitos: string): MarcaTarjeta {
  if (/^4/.test(digitos)) {
    return 'visa';
  }
  if (/^(5[1-5]|2(2[2-9]|[3-6]\d|7[01]|720))/.test(digitos)) {
    return 'mastercard';
  }
  if (/^3[47]/.test(digitos)) {
    return 'amex';
  }
  return '';
}

/** Algoritmo de Luhn: detecta números de tarjeta mal escritos. */
export function luhnValido(digitos: string): boolean {
  if (!/^\d+$/.test(digitos)) {
    return false;
  }
  let suma = 0;
  let doble = false;
  for (let i = digitos.length - 1; i >= 0; i--) {
    let n = Number(digitos[i]);
    if (doble) {
      n *= 2;
      if (n > 9) {
        n -= 9;
      }
    }
    suma += n;
    doble = !doble;
  }
  return suma % 10 === 0;
}

export function numeroValido(digitos: string): boolean {
  return digitos.length >= 13 && digitos.length <= 19 && luhnValido(digitos);
}

/** Agrupa de a 4 (American Express: 4-6-5). */
export function formatearNumero(digitos: string): string {
  const d = digitos.slice(0, 19);
  if (marcaTarjeta(d) === 'amex') {
    return [d.slice(0, 4), d.slice(4, 10), d.slice(10, 15)].filter(Boolean).join(' ');
  }
  return d.replace(/(\d{4})(?=\d)/g, '$1 ');
}

/** MM/AA mientras se escribe. */
export function formatearVencimiento(valor: string): string {
  const d = soloDigitos(valor).slice(0, 4);
  return d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
}

/** La tarjeta vale hasta el último día del mes indicado. */
export function vencimientoValido(valor: string, hoy = new Date()): boolean {
  const m = /^(\d{2})\/(\d{2})$/.exec(valor);
  if (!m) {
    return false;
  }
  const mes = Number(m[1]);
  return mes >= 1 && mes <= 12 && new Date(2000 + Number(m[2]), mes, 0, 23, 59, 59) >= hoy;
}

export function cvcValido(cvc: string, marca: MarcaTarjeta): boolean {
  return new RegExp(`^\\d{${marca === 'amex' ? 4 : 3}}$`).test(cvc);
}

/**
 * Método de prueba que corresponde a la tarjeta, solo si el backend lo ofrece.
 *
 * @returns el método, o null si no es una tarjeta de prueba disponible
 */
export function metodoDeTarjeta(digitos: string, metodosDisponibles: readonly string[]): string | null {
  const tarjeta = TARJETAS_PRUEBA.find(t => t.numero === digitos);
  return tarjeta && metodosDisponibles.includes(tarjeta.metodo) ? tarjeta.metodo : null;
}
