package co.edu.unbosque.marketplaceInversionesLR.dto;

/**
 * Cantidad de reservas de una ciudad, para el ranking de ciudades con más
 * demanda del dashboard (HU-04).
 *
 * @param ciudad        nombre de la ciudad
 * @param totalReservas cantidad de reservas en esa ciudad
 */
public record DemandaCiudadDTO(String ciudad, Long totalReservas) {
}
