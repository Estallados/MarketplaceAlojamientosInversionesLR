// ======================================
// VALIDACIONES DEL ALOJAMIENTO (HU-15, HU-16)
// Mismas reglas que el backend (LimitesTexto y PublicacionService).
// Cada función devuelve el mensaje de error o '' si el valor es válido.
// ======================================

/** Máximo de caracteres de título, ubicación, ciudad, tipo y nombre de servicio. */
export const TEXTO_CORTO = 255;

/** Máximo de caracteres de descripción y reglas. */
export const TEXTO_LARGO = 2000;

/** Máximo del precio por noche y del costo de un servicio adicional. */
export const MONTO_MAXIMO = 1_000_000_000;

export function errorLongitud(
  valor: string,
  maximo: number
): string {

  return valor.trim().length > maximo
    ? `Puede tener máximo ${maximo} caracteres.`
    : '';
}

export function errorTitulo(valor: string): string {

  if (!valor.trim()) {
    return 'Debes indicar el título del alojamiento.';
  }

  return errorLongitud(valor, TEXTO_CORTO);
}

/** CA-098: capacidad entera mayor que cero. */
export function errorCapacidad(
  valor: number | null,
  noNumerico = false
): string {

  if (
    noNumerico ||
    (
      valor !== null &&
      (
        !Number.isInteger(valor) ||
        valor <= 0
      )
    )
  ) {

    return (
      'La capacidad debe ser un número ' +
      'entero mayor que cero.'
    );
  }

  return '';
}

/** CA-098: precio por noche mayor que cero. */
export function errorPrecioNoche(
  valor: number | null,
  noNumerico = false
): string {

  if (noNumerico) {
    return 'El precio por noche debe ser mayor que cero.';
  }

  if (valor === null) {
    return '';
  }

  if (
    typeof valor !== 'number' ||
    !(valor > 0)
  ) {

    return 'El precio por noche debe ser mayor que cero.';
  }

  if (valor > MONTO_MAXIMO) {

    return (
      'El precio por noche debe ser positivo ' +
      'y de máximo 1.000.000.000.'
    );
  }

  return '';
}

/** Habitaciones, camas y baños: entero no negativo. */
export function errorNoNegativo(
  nombre: string,
  valor: number | null,
  noNumerico = false
): string {

  if (
    noNumerico ||
    (
      valor !== null &&
      (
        !Number.isInteger(valor) ||
        valor < 0
      )
    )
  ) {

    return (
      `La cantidad de ${nombre} ` +
      'debe ser un entero no negativo.'
    );
  }

  return '';
}

// ======================================
// SERVICIOS ADICIONALES (CA-185)
// ======================================

export function errorNombreServicio(valor: string): string {

  if (!valor.trim()) {
    return 'Debes indicar el nombre del servicio.';
  }

  return errorLongitud(valor, TEXTO_CORTO);
}

export function errorCostoServicio(
  valor: number | null,
  noNumerico = false
): string {

  if (
    noNumerico ||
    valor === null ||
    typeof valor !== 'number' ||
    !(valor > 0)
  ) {

    return 'El costo debe ser un valor positivo.';
  }

  if (valor > MONTO_MAXIMO) {

    return 'El costo puede ser de máximo 1.000.000.000.';
  }

  return '';
}
