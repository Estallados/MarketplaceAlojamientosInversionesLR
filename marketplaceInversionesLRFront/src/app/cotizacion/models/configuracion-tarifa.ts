// ======================================
// CONFIGURACIÓN DE TARIFAS (HU-24)
// Esquemas ConfiguracionTarifaEntrada y ConfiguracionTarifa
// de docs/openapi/administracion-reportes.yaml
// ======================================

/** Porcentajes que se envían al guardar (PUT /admin/tarifas). */
export interface ConfiguracionTarifaEntrada {

  /** Porcentaje sobre el precio base que se cobra por limpieza (0 a 100). */
  porcentajeLimpieza: number;

  /** Porcentaje sobre el precio base que se cobra como tarifa de servicio (0 a 100). */
  porcentajeServicio: number;
}

/** Porcentajes vigentes; si porDefecto es true, aún no hay configuración guardada. */
export interface ConfiguracionTarifa
  extends ConfiguracionTarifaEntrada {

  /** Identificador de la configuración guardada. */
  idConfiguracion?: number | null;

  /** Usuario que hizo el último cambio. */
  idUsuario?: number | null;

  /** Momento del último cambio (ISO date-time). */
  fecha?: string | null;

  /** Indica si son los valores por defecto del sistema. */
  porDefecto: boolean;
}
