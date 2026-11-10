// ======================================
// FECHAS DE LA ESTADÍA (CA-082)
// Mismas reglas que el backend (CalendarioService.validarFechas):
// ambas fechas obligatorias, salida posterior a la llegada y llegada
// desde hoy. El backend sigue siendo la autoridad y responde 400.
//
// Las fechas se manejan como texto AAAA-MM-DD (formato del contrato) y
// se comparan como días del calendario, sin pasar por UTC: así no se
// corre un día por la zona horaria del navegador.
// ======================================

const FORMATO_FECHA = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Convierte AAAA-MM-DD en un número de día comparable, o null si el texto
 * no tiene el formato o no es una fecha real (por ejemplo 2026-02-30).
 */
export function diaDeFecha(valor: string | null | undefined): number | null {

  const partes = FORMATO_FECHA.exec(valor ?? '');

  if (!partes) {
    return null;
  }

  const anio = Number(partes[1]);
  const mes = Number(partes[2]);
  const dia = Number(partes[3]);

  // Date.UTC solo para validar el día y contar días; no depende de la zona horaria.
  const ms = Date.UTC(anio, mes - 1, dia);
  const fecha = new Date(ms);

  if (
    fecha.getUTCFullYear() !== anio ||
    fecha.getUTCMonth() !== mes - 1 ||
    fecha.getUTCDate() !== dia
  ) {
    return null;
  }

  return ms / 86_400_000;
}

/** Fecha de hoy en la zona horaria local, como AAAA-MM-DD. */
export function hoyLocal(ahora: Date = new Date()): string {

  const anio = ahora.getFullYear();
  const mes = String(ahora.getMonth() + 1).padStart(2, '0');
  const dia = String(ahora.getDate()).padStart(2, '0');

  return `${anio}-${mes}-${dia}`;
}

/** Día siguiente a una fecha AAAA-MM-DD, o '' si la fecha no es válida. */
export function diaSiguiente(valor: string): string {

  const dia = diaDeFecha(valor);

  if (dia === null) {
    return '';
  }

  return new Date((dia + 1) * 86_400_000).toISOString().slice(0, 10);
}

export interface ErroresFechas {
  llegada: string;
  salida: string;
}

/**
 * Errores de cada campo; cadenas vacías si las fechas son válidas.
 *
 * @param hoy fecha de hoy (AAAA-MM-DD) para no aceptar llegadas en el pasado
 */
export function erroresFechas(
  llegada: string,
  salida: string,
  hoy: string = hoyLocal()
): ErroresFechas {

  const errores: ErroresFechas = { llegada: '', salida: '' };

  const diaLlegada = diaDeFecha(llegada);
  const diaSalida = diaDeFecha(salida);

  if (!llegada) {
    errores.llegada = 'Selecciona la fecha de llegada.';
  } else if (diaLlegada === null) {
    errores.llegada = 'La fecha de llegada no es válida.';
  } else if (diaLlegada < (diaDeFecha(hoy) ?? diaLlegada)) {
    errores.llegada = 'La fecha de llegada no puede estar en el pasado.';
  }

  if (!salida) {
    errores.salida = 'Selecciona la fecha de salida.';
  } else if (diaSalida === null) {
    errores.salida = 'La fecha de salida no es válida.';
  } else if (diaLlegada !== null && diaSalida <= diaLlegada) {
    errores.salida =
      'Las fechas son inválidas: la salida debe ser posterior a la llegada.';
  }

  return errores;
}

/** Muestra AAAA-MM-DD como fecha legible sin cambiar de día por la zona horaria. */
export function fechaLegible(valor: string): string {

  const [anio, mes, dia] = valor.split('-').map(Number);

  if (!anio || !mes || !dia) {
    return valor;
  }

  return new Date(anio, mes - 1, dia).toLocaleDateString('es-CO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric'
  });
}

/** Noches entre llegada y salida; 0 si el rango no es válido. */
export function noches(llegada: string, salida: string): number {

  const a = diaDeFecha(llegada);
  const b = diaDeFecha(salida);

  return a !== null && b !== null && b > a ? b - a : 0;
}
