package co.edu.unbosque.marketplaceInversionesLR.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Página de reservas resumidas del administrador (HU-11). Solo documenta en
 * Swagger la forma de la respuesta de {@code GET /admin/reservas}, que es un
 * {@link PaginaDTO} de {@link ReservaAdminDTO}.
 */
@Schema(name = "PaginaReservasAdmin", description = "Página de reservas resumidas, las más recientes primero")
public class PaginaReservasAdminDTO extends PaginaDTO<ReservaAdminDTO> {
}
