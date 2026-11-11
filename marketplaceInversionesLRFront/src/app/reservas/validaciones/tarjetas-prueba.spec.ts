import {
  NUMERO_FALLA_PASARELA,
  TARJETAS_PRUEBA,
  cvcValido,
  formatearNumero,
  formatearVencimiento,
  luhnValido,
  marcaTarjeta,
  metodoDeTarjeta,
  numeroValido,
  vencimientoValido
} from './tarjetas-prueba';

/**
 * HU-27 (SCRUM-246): tarjetas de prueba y validaciones del formulario de pago.
 */
describe('tarjetas de prueba (HU-27)', () => {

  it('CA-160 todas las tarjetas de prueba son números válidos y no se repiten', () => {
    expect(TARJETAS_PRUEBA.every(t => numeroValido(t.numero))).toBe(true);
    expect(new Set(TARJETAS_PRUEBA.map(t => t.numero)).size).toBe(TARJETAS_PRUEBA.length);
    expect(TARJETAS_PRUEBA.every(t => t.metodo.startsWith('pm_card_'))).toBe(true);
  });

  it('CA-160 la tarjeta elige el método de prueba solo si el backend lo ofrece', () => {
    const ofrecidos = ['pm_card_visa', 'pm_card_chargeDeclinedInsufficientFunds'];
    expect(metodoDeTarjeta('4242424242424242', ofrecidos)).toBe('pm_card_visa');
    expect(metodoDeTarjeta('4000000000009995', ofrecidos)).toBe('pm_card_chargeDeclinedInsufficientFunds');
    // De prueba, pero el backend no la ofrece.
    expect(metodoDeTarjeta('5555555555554444', ofrecidos)).toBeNull();
    // Válida pero no de prueba (una tarjeta "real").
    expect(metodoDeTarjeta('4111111111111111', ofrecidos)).toBeNull();
    expect(metodoDeTarjeta(NUMERO_FALLA_PASARELA, ['pm_card_error'])).toBe('pm_card_error');
  });

  it('valida el número con el algoritmo de Luhn y reconoce la marca', () => {
    expect(luhnValido('4242424242424242')).toBe(true);
    expect(luhnValido('4242424242424241')).toBe(false);
    expect(numeroValido('42424242')).toBe(false);
    expect(marcaTarjeta('4242')).toBe('visa');
    expect(marcaTarjeta('5555')).toBe('mastercard');
    expect(marcaTarjeta('3782')).toBe('amex');
    expect(marcaTarjeta('9999')).toBe('');
  });

  it('formatea el número y el vencimiento mientras se escribe', () => {
    expect(formatearNumero('4242424242424242')).toBe('4242 4242 4242 4242');
    expect(formatearNumero('378282246310005')).toBe('3782 822463 10005');
    expect(formatearVencimiento('1240')).toBe('12/40');
    expect(formatearVencimiento('1')).toBe('1');
  });

  it('el vencimiento vale hasta el último día del mes y el CVC depende de la marca', () => {
    const hoy = new Date(2026, 10, 11);
    expect(vencimientoValido('11/26', hoy)).toBe(true);
    expect(vencimientoValido('10/26', hoy)).toBe(false);
    expect(vencimientoValido('13/30', hoy)).toBe(false);
    expect(cvcValido('123', 'visa')).toBe(true);
    expect(cvcValido('123', 'amex')).toBe(false);
    expect(cvcValido('1234', 'amex')).toBe(true);
  });
});
