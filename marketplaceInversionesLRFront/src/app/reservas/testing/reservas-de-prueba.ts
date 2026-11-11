import { Reserva } from '../models/reserva';
import { diaSiguiente, hoyLocal } from '../../disponibilidad/validaciones/fechas-estadia';

// Datos de prueba compartidos por las pruebas de reservas (HU-25). Solo los usan los .spec.ts.

/** Fecha AAAA-MM-DD a n días de hoy (negativo: en el pasado). */
export function enDias(n: number): string {
  if (n >= 0) {
    let f = hoyLocal();
    for (let i = 0; i < n; i++) {
      f = diaSiguiente(f);
    }
    return f;
  }
  const d = new Date();
  d.setDate(d.getDate() + n);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function reservaDePrueba(id: number, cambios: Partial<Reserva> = {}): Reserva {
  return {
    idReserva: id,
    fechaEntrada: enDias(10),
    fechaSalida: enDias(13),
    noches: 3,
    huespedes: 2,
    estado: 'CONFIRMADA',
    total: 740000,
    idHuesped: 12,
    idAlojamiento: 7,
    fechaCreacion: '2026-11-01T10:00:00',
    tituloAlojamiento: `Casa ${id}`,
    ciudad: 'Bogotá',
    imagenAlojamiento: `https://ejemplo.com/${id}.jpg`,
    completada: false,
    ...cambios
  };
}
