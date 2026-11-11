// ======================================
// ALOJAMIENTOS Y PUBLICACIONES (HU-15, HU-16)
// Esquemas de docs/openapi/alojamientos.yaml
// ======================================

// ======================================
// 1. ESTADOS DEL ALOJAMIENTO (CA-104)
// ======================================

export type EstadoAlojamiento =
  | 'BORRADOR'
  | 'PENDIENTE_REVISION'
  | 'ACTIVA'
  | 'PAUSADA'
  | 'BLOQUEADA';

// ======================================
// 2. DATOS DEL ALOJAMIENTO
// Esquema AlojamientoEntrada
// ======================================

/** Datos del alojamiento; en el borrador solo el título es obligatorio. */
export interface DatosAlojamiento {

  titulo: string;

  descripcion?: string;

  /** Dirección. */
  ubicacion?: string;

  ciudad?: string;

  latitud?: number;

  longitud?: number;

  /** Tipo de alojamiento, por ejemplo Apartamento o Casa. */
  tipo?: string;

  /** Mayor que cero (CA-098). */
  capacidad?: number;

  /** Mayor que cero (CA-098). */
  precioNoche?: number;

  habitaciones?: number;

  camas?: number;

  banos?: number;

  /** Formato HH:mm. */
  horaEntrada?: string;

  /** Formato HH:mm. */
  horaSalida?: string;

  reglas?: string;

  /** Servicios incluidos, por ejemplo wifi o piscina. */
  amenidades?: string[];

  /** Política de cancelación; obligatoria para enviar a revisión. */
  idPolitica?: number;

  porcentajeLimpieza?: number;

  porcentajeServicio?: number;
}

// ======================================
// 3. IMÁGENES
// Esquema Imagen
// ======================================

export interface ImagenAlojamiento {

  idImagen: number;

  url: string;

  idAlojamiento?: number;

  principal: boolean;
}

// ======================================
// 4. SERVICIOS ADICIONALES (HU-16, CA-185)
// Esquemas ServicioAdicionalEntrada y ServicioAdicional
// ======================================

/** Datos para registrar o modificar un servicio adicional. */
export interface ServicioAdicionalEntrada {

  nombre: string;

  /** Costo positivo (CA-185). */
  costo: number;
}

export interface ServicioAdicional
  extends ServicioAdicionalEntrada {

  idServicio?: number;

  idAlojamiento?: number;
}

// ======================================
// 5. RESPUESTA DEL ALOJAMIENTO
// Esquema Alojamiento
// ======================================

export interface AlojamientoRespuesta
  extends DatosAlojamiento {

  idAlojamiento: number;

  estado: EstadoAlojamiento;

  idAnfitrion: number;

  nombreAnfitrion?: string;

  correoAnfitrion?: string;

  nombrePolitica?: string;

  /** URLs de las imágenes, la principal primero. */
  imagenes?: string[];

  detalleImagenes?: ImagenAlojamiento[];

  serviciosAdicionales?: ServicioAdicional[];

  promedioCalificacion?: number | null;

  cantidadResenas?: number;

  cantidadReservas?: number;

  fechaCreacion?: string;

  fechaActualizacion?: string;
}

// ======================================
// 5.1 PÁGINA DE MIS ALOJAMIENTOS (HU-19)
// GET /alojamientos/mios devuelve un PaginaDTO
// ======================================

export interface PaginaAlojamientos {
  contenido: AlojamientoRespuesta[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

// ======================================
// 6. POLÍTICAS DE CANCELACIÓN
// Esquema PoliticaCancelacion
// ======================================

export interface PoliticaCancelacion {

  idPolitica: number;

  nombre: string;

  condiciones?: string;

  diasReembolsoTotal?: number;

  diasReembolsoParcial?: number;

  porcentajeParcial?: number;
}

// ======================================
// 7. MENSAJE DEL SERVIDOR
// Esquema Mensaje
// ======================================

export interface MensajeServidor {

  message: string;

  /** Solo en los errores de seguridad (401 y 403). */
  status?: number;
}
