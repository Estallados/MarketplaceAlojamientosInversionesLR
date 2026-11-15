import { contrasenaValida, faltasContrasena, mensajeContrasena } from './contrasena';

// ======================================
// CA-135 POLÍTICA DE CONTRASEÑA
// Los mismos casos que ExceptionChekerTest en el backend: el front no debe
// aceptar una contraseña que el backend rechace, ni al revés.
// ======================================

describe('Política de contraseña (CA-135)', () => {

  it('CA-135 acepta una contraseña que cumple todas las reglas', () => {
    for (const valida of ['Clave#2026', 'Aa1!aaaa', 'Ñandú#2026']) {
      expect(contrasenaValida(valida)).toBe(true);
      expect(mensajeContrasena(valida)).toBe('');
    }
  });

  it('CA-135 identifica cada regla que falla', () => {
    expect(faltasContrasena('Ab1!')).toEqual(['entre 8 y 64 caracteres']);
    expect(faltasContrasena('clave#2026')).toEqual(['una mayúscula']);
    expect(faltasContrasena('CLAVE#2026')).toEqual(['una minúscula']);
    expect(faltasContrasena('Clave#Abcd')).toEqual(['un número']);
    expect(faltasContrasena('Clave2026x')).toEqual(['un símbolo']);
  });

  it('CA-135 rechaza más de 64 caracteres o más de 72 bytes', () => {
    expect(contrasenaValida('Aa1!' + 'a'.repeat(60))).toBe(true);
    expect(contrasenaValida('Aa1!' + 'a'.repeat(61))).toBe(false);
    // 38 caracteres pero 74 bytes: BCrypt no lo admite
    expect(contrasenaValida('Ñ1!' + 'ñ'.repeat(35))).toBe(false);
    expect(contrasenaValida(null)).toBe(false);
  });

  it('CA-135 el mensaje dice exactamente qué falta', () => {
    expect(mensajeContrasena('clave2026')).toBe('La contraseña debe tener una mayúscula y un símbolo.');
    expect(mensajeContrasena('')).toBe(
      'La contraseña debe tener entre 8 y 64 caracteres, una mayúscula, una minúscula, un número y un símbolo.'
    );
  });
});
