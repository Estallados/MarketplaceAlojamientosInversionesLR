/** Conversación tal como la devuelve GET /conversaciones (ConversacionDTO). */
export interface ConversacionApi {
  idConversacion: number;
  fechaCreacion?: string;
  estado?: string;
  /** Huésped que inició la conversación. */
  idUsuario: number;
  idAnfitrion: number;
  idAlojamiento: number;
  /** Primer mensaje, solo al iniciar la conversación. */
  mensaje?: string | null;
  nombreUsuario?: string;
  nombreAnfitrion?: string;
}

/** Mensaje tal como lo devuelve GET /conversaciones/{id}/mensajes (MensajeDTO). */
export interface MensajeApi {
  idMensaje: number;
  contenido: string;
  fechaEnvio: string;
  idConversacion?: number;
  /** Quién lo envió (CA-165). */
  idUsuario: number;
  nombreUsuario?: string;
}

/** Conversación lista para la pantalla. */
export interface Conversacion {
  id: number;
  /** El otro participante: el anfitrión si soy huésped, el huésped si soy anfitrión. */
  contacto: string;
  iniciales: string;
  /** Mi id dentro de la conversación, para saber qué mensajes son míos. */
  miId: number;
  idAlojamiento: number;
  /** Título del alojamiento; vacío mientras no se conozca. */
  alojamiento: string;
  fechaCreacion: string;
}

/** Mensaje listo para la pantalla. */
export interface Mensaje {
  id: number;
  texto: string;
  fecha: string;
  /** Nombre de quien lo envió. */
  remitente: string;
  /** true si lo escribió el usuario de la sesión. */
  mio: boolean;
}

/** Largo máximo de un mensaje (igual que en el backend). */
export const MAX_MENSAJE = 2000;
