// ======================================
// POLÍTICA DE CONTRASEÑA (RNF-005, CA-135)
// Las mismas reglas que ExceptionCheker en el backend: entre 8 y 64
// caracteres (y no más de 72 bytes, el límite de BCrypt), una mayúscula,
// una minúscula, un número y un símbolo. Mayúsculas y minúsculas cuentan
// con tilde o eñe; símbolo es cualquier carácter que no sea A-Z, a-z o 0-9.
// ======================================

export const CONTRASENA_MINIMO = 8;

export const CONTRASENA_MAXIMO = 64;

/** Límite de BCrypt en el backend. */
export const CONTRASENA_MAXIMO_BYTES = 72;

/** Texto de ayuda que se muestra bajo el campo. */
export const AYUDA_CONTRASENA =
  `Entre ${CONTRASENA_MINIMO} y ${CONTRASENA_MAXIMO} caracteres, con una mayúscula, ` +
  'una minúscula, un número y un símbolo.';

/**
 * Condiciones que la contraseña todavía no cumple, en el orden de la ayuda.
 * Vacío si es válida.
 */
export function faltasContrasena(valor: string | null | undefined): string[] {

  const texto = valor ?? '';

  const faltas: string[] = [];

  if (
    texto.length < CONTRASENA_MINIMO ||
    texto.length > CONTRASENA_MAXIMO ||
    new TextEncoder().encode(texto).length > CONTRASENA_MAXIMO_BYTES
  ) {
    faltas.push(`entre ${CONTRASENA_MINIMO} y ${CONTRASENA_MAXIMO} caracteres`);
  }

  if (!/\p{Lu}/u.test(texto)) {
    faltas.push('una mayúscula');
  }

  if (!/\p{Ll}/u.test(texto)) {
    faltas.push('una minúscula');
  }

  if (!/\d/.test(texto)) {
    faltas.push('un número');
  }

  if (!/[^A-Za-z0-9]/.test(texto)) {
    faltas.push('un símbolo');
  }

  return faltas;
}

export function contrasenaValida(valor: string | null | undefined): boolean {
  return faltasContrasena(valor).length === 0;
}

/** Mensaje que dice exactamente qué falta, o '' si la contraseña es válida. */
export function mensajeContrasena(valor: string | null | undefined): string {

  const faltas = faltasContrasena(valor);

  if (faltas.length === 0) {
    return '';
  }

  const lista = faltas.length === 1
    ? faltas[0]
    : `${faltas.slice(0, -1).join(', ')} y ${faltas[faltas.length - 1]}`;

  return `La contraseña debe tener ${lista}.`;
}
