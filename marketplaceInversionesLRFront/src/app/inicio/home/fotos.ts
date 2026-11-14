/**
 * Fotos de referencia de la página de inicio (las mismas del proyecto base, alojadas en Unsplash).
 * Se usan para el encabezado y para los tipos que todavía no tienen un alojamiento con foto.
 */
export const FOTOS = {
  portada: 'https://images.unsplash.com/photo-1613036955981-1cd68c048d7b?auto=format&fit=crop&w=1800&q=80',
  casa: 'https://images.unsplash.com/photo-1684226910423-a4eb43a03257?auto=format&fit=crop&w=900&q=80',
  finca: 'https://images.unsplash.com/photo-1623298460174-371443cc45f0?auto=format&fit=crop&w=900&q=80',
  cabana: 'https://images.unsplash.com/photo-1589815402103-2f9e8d434cd3?auto=format&fit=crop&w=900&q=80',
  loft: 'https://images.unsplash.com/photo-1613013441633-785518cf90b3?auto=format&fit=crop&w=900&q=80',
  colonial: 'https://images.unsplash.com/photo-1790141057166-6b41e4aeabfb?auto=format&fit=crop&w=900&q=80',
  interior: 'https://images.unsplash.com/photo-1613013383640-6226419147d7?auto=format&fit=crop&w=900&q=80',
};

/** Foto de referencia según el nombre del tipo (no según su posición en la lista). */
export function fotoDeReferencia(tipo: string): string {
  const t = tipo
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
  if (t.includes('caba')) return FOTOS.cabana;
  if (t.includes('finca') || t.includes('granja') || t.includes('rural')) return FOTOS.finca;
  if (t.includes('casa') || t.includes('villa')) return FOTOS.casa;
  if (t.includes('apartamento') || t.includes('loft') || t.includes('estudio') || t.includes('aparta')) return FOTOS.loft;
  if (t.includes('hotel') || t.includes('hostal') || t.includes('colonial')) return FOTOS.colonial;
  return FOTOS.interior;
}
