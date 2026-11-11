import { Reserva } from './reserva';
import { hoyLocal } from '../../disponibilidad/validaciones/fechas-estadia';

// ======================================
// ESTADO DE UNA RESERVA PARA MOSTRAR (HU-25)
// Mismas etiquetas y colores que el proyecto base. El estado lo da el
// backend; "Activa" y "Finalizada" se derivan de las fechas y de
// `completada`, sin cambiar el estado guardado.
// ======================================

export type PestanaReserva = 'Próximas' | 'Activas' | 'Finalizadas' | 'Canceladas';

export const PESTANAS_RESERVA: readonly PestanaReserva[] = ['Próximas', 'Activas', 'Finalizadas', 'Canceladas'];

/** La estadía ya empezó (la llegada es hoy o antes). */
export function yaInicio(r: Reserva, hoy = hoyLocal()): boolean {
  return r.fechaEntrada <= hoy;
}

export function categoriaReserva(r: Reserva, hoy = hoyLocal()): PestanaReserva {
  if (r.estado === 'CANCELADA') {
    return 'Canceladas';
  }
  if (r.completada) {
    return 'Finalizadas';
  }
  if (r.estado === 'CONFIRMADA' && yaInicio(r, hoy)) {
    return 'Activas';
  }
  // CONFIRMADA futura, PENDIENTE_PAGO o EN_REVISION
  return 'Próximas';
}

export function etiquetaReserva(r: Reserva, hoy = hoyLocal()): string {
  if (r.estado === 'CANCELADA') {
    return 'Cancelada';
  }
  if (r.completada) {
    return 'Finalizada';
  }
  if (r.estado === 'EN_REVISION') {
    return 'En revisión';
  }
  if (r.estado === 'PENDIENTE_PAGO') {
    return 'Pendiente de pago';
  }
  return yaInicio(r, hoy) ? 'Activa' : 'Confirmada';
}

export function claseEtiquetaReserva(etiqueta: string): string {
  const tono =
    etiqueta === 'Cancelada'
      ? 'bg-red-50 text-red-700'
      : etiqueta === 'Finalizada'
        ? 'bg-slate-100 text-slate-600'
        : etiqueta === 'Activa'
          ? 'bg-sky-50 text-sky-700'
          : etiqueta === 'Confirmada'
            ? 'bg-emerald-50 text-emerald-700'
            : 'bg-amber-50 text-amber-700';
  return 'inline-flex rounded-full px-3 py-1 text-xs font-bold ' + tono;
}

/** AAAA-MM-DD (o fecha-hora ISO) como dd/MM/aaaa, sin desfase de zona horaria. */
export function fechaDdMmAaaa(valor: string | null | undefined): string {
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(valor ?? '');
  return partes ? `${partes[3]}/${partes[2]}/${partes[1]}` : (valor || '—');
}

/** Fecha corta legible (por ejemplo 05 nov 2026), como el detalle del proyecto base. */
export function fechaCorta(valor: string | null | undefined): string {
  if (!valor) {
    return '';
  }
  const soloFecha = /^(\d{4})-(\d{2})-(\d{2})$/.exec(valor);
  const fecha = soloFecha ? new Date(+soloFecha[1], +soloFecha[2] - 1, +soloFecha[3]) : new Date(valor);
  return Number.isNaN(fecha.getTime())
    ? ''
    : fecha.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' });
}

const FORMATO_COP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });

export function monedaCop(valor: number | null | undefined): string {
  return typeof valor === 'number' ? FORMATO_COP.format(valor) : '—';
}
