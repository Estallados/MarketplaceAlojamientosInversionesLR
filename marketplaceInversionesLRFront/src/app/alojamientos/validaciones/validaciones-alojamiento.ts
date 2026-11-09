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

// ======================================
// DATOS MÍNIMOS PARA ENVIAR A REVISIÓN (CA-097, CA-157)
// Los mismos que revisa el backend (PublicacionService.camposFaltantes).
// Las fechas de disponibilidad desde hoy también se exigen, pero solo
// las comprueba el backend, que responde 409 con lo que falta.
// ======================================

export type DatoRequerido =
  | 'titulo'
  | 'descripcion'
  | 'ubicacion'
  | 'ciudad'
  | 'tipo'
  | 'capacidad'
  | 'habitaciones'
  | 'banos'
  | 'precioNoche'
  | 'horaEntrada'
  | 'horaSalida'
  | 'reglas'
  | 'idPolitica'
  | 'imagenes';

/** Datos de la publicación que se revisan; acepta el formulario o la respuesta del backend. */
export interface DatosParaRevision {
  titulo?: string | null;
  descripcion?: string | null;
  ubicacion?: string | null;
  ciudad?: string | null;
  tipo?: string | null;
  capacidad?: number | null;
  habitaciones?: number | null;
  banos?: number | null;
  precioNoche?: number | null;
  horaEntrada?: string | null;
  horaSalida?: string | null;
  reglas?: string | null;
  idPolitica?: number | null;
  /** Imágenes guardadas o por subir. */
  cantidadImagenes: number;
}

export interface DatoFaltanteRevision {
  campo: DatoRequerido;
  nombre: string;
}

/** Datos que faltan para enviar a revisión; vacía si está completa. */
export function datosFaltantesParaRevision(
  d: DatosParaRevision
): DatoFaltanteRevision[] {

  const vacio = (valor?: string | null) => !valor?.trim();

  const ausente = (valor?: number | null) =>
    valor === null || valor === undefined;

  const revision: [boolean, DatoRequerido, string][] = [
    [vacio(d.titulo), 'titulo', 'título'],
    [vacio(d.descripcion), 'descripcion', 'descripción'],
    [vacio(d.ubicacion), 'ubicacion', 'ubicación'],
    [vacio(d.ciudad), 'ciudad', 'ciudad'],
    [vacio(d.tipo), 'tipo', 'tipo de alojamiento'],
    [ausente(d.capacidad), 'capacidad', 'capacidad'],
    [ausente(d.habitaciones), 'habitaciones', 'habitaciones'],
    [ausente(d.banos), 'banos', 'baños'],
    [ausente(d.precioNoche), 'precioNoche', 'precio por noche'],
    [vacio(d.horaEntrada), 'horaEntrada', 'hora de entrada'],
    [vacio(d.horaSalida), 'horaSalida', 'hora de salida'],
    [vacio(d.reglas), 'reglas', 'reglas'],
    [ausente(d.idPolitica), 'idPolitica', 'política de cancelación'],
    [d.cantidadImagenes <= 0, 'imagenes', 'al menos una imagen']
  ];

  return revision
    .filter(([falta]) => falta)
    .map(([, campo, nombre]) => ({ campo, nombre }));
}
