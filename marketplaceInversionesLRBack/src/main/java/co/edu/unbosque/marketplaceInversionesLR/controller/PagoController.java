package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.ConfiguracionPagoDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controlador REST de pagos (HU-27). Ruta base: {@code /pagos}. El cobro se
 * hace al crear la reserva ({@code POST /reservas}); aquí el frontend solo
 * consulta cómo cobrar. Contrato: {@code docs/openapi/reservas-pagos.yaml}.
 */
@RestController
@RequestMapping("/pagos")
@Tag(name = "Pagos", description = "Pagos simulados con Stripe (modo prueba)")
public class PagoController {

	/** Servicio de pagos. */
	private final PagoService pagoService;

	/**
	 * Crea el controlador.
	 *
	 * @param pagoService servicio de pagos
	 */
	public PagoController(PagoService pagoService) {
		this.pagoService = pagoService;
	}

	/**
	 * Configuración de pagos para el frontend. Ruta pública:
	 * {@code GET /pagos/configuracion}. Dice si los cobros van al modo de
	 * prueba de Stripe o al simulador, la clave publicable para Stripe
	 * Elements, la moneda y los métodos de prueba con su resultado.
	 *
	 * @return 200 con la configuración de pagos
	 */
	@Operation(summary = "Configuración de pagos para el front (público)", description = "modo STRIPE_TEST (cobros en "
			+ "el modo de prueba de Stripe) o SIMULADO (sin clave de Stripe), clave publicable para Stripe Elements, "
			+ "moneda y métodos de prueba con su resultado. El metodoPago de POST /reservas es uno de esos métodos o el "
			+ "PaymentMethod (pm_...) que crea Stripe Elements con una tarjeta de prueba, como 4242 4242 4242 4242.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Configuración de pagos")
	@GetMapping("/configuracion")
	public ResponseEntity<ConfiguracionPagoDTO> configuracion() {
		return ResponseEntity.ok(pagoService.configuracion());
	}
}
