/** Servicio adicional con costo que ofrece el alojamiento. */
export interface ServicioAdicionalDetalle {
  idServicio?: number;
  nombre: string;
  costo: number;
}

/**
 * Alojamiento tal como lo devuelve GET /alojamientos/{id} (AlojamientoDTO).
 * Solo los campos que usa la pantalla de detalle (HU-18).
 */
export interface DetalleAlojamiento {
  idAlojamiento?: number;
  titulo: string;
  descripcion?: string;
  ubicacion?: string;
  ciudad?: string;
  latitud?: number | null;
  longitud?: number | null;
  tipo?: string;
  capacidad?: number;
  precioNoche?: number;
  reglas?: string;
  habitaciones?: number;
  camas?: number;
  banos?: number;
  horaEntrada?: string;
  horaSalida?: string;
  nombrePolitica?: string;
  amenidades?: string[];
  serviciosAdicionales?: ServicioAdicionalDetalle[];
  estado?: string;
  nombreAnfitrion?: string;
  imagenes?: string[];
  promedioCalificacion?: number | null;
  cantidadResenas?: number;
}

/** Reseña publicada de un alojamiento. */
export interface ResenaDetalle {
  idResena: number;
  calificacion: number;
  comentario: string | null;
  fecha: string;
  nombreUsuario: string;
}

/** Respuesta de GET /alojamientos/{id}/resenas. */
export interface ResumenResenasDetalle {
  resenas?: { contenido: ResenaDetalle[] } | null;
  promedio?: number | null;
  cantidad?: number | null;
}
