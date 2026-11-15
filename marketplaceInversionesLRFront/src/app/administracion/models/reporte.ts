/** Tipos de reporte que acepta GET /admin/reportes/{tipo}. */
export type TipoReporte = 'usuarios' | 'alojamientos' | 'reservas' | 'ingresos' | 'demanda';

/** Formatos de exportación. */
export type FormatoReporte = 'pdf' | 'excel';

/** Registro del historial de exportaciones (RegistroExportacionDTO). */
export interface RegistroExportacion {
  idRegistro: number;
  tipoReporte: string;
  formato: string;
  cantidadRegistros?: number;
  idUsuario?: number;
  /** Administrador que pidió el reporte. */
  nombreUsuario?: string;
  /** Fecha y hora de la exportación (ISO). */
  fecha: string;
}

/** Página del historial. */
export interface PaginaExportaciones {
  contenido: RegistroExportacion[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Archivo descargado. */
export interface ArchivoReporte {
  blob: Blob;
  nombre: string;
}

/** Tipos de reporte con el texto que se muestra (valor = parámetro {tipo} del backend). */
export const TIPOS_REPORTE: { valor: TipoReporte; texto: string }[] = [
  { valor: 'usuarios', texto: 'Usuarios' },
  { valor: 'alojamientos', texto: 'Publicaciones' },
  { valor: 'reservas', texto: 'Reservas' },
  { valor: 'ingresos', texto: 'Ingresos estimados' },
  { valor: 'demanda', texto: 'Demanda' },
];
