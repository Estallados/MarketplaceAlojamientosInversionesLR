package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.PeriodoDisponibilidadDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.CalendarioService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Disponibilidad de los alojamientos (HU-20, servicio S04). Ruta base:
 * {@code /alojamientos/{idAlojamiento}/disponibilidad}. Contrato:
 * {@code docs/openapi/disponibilidad-busqueda.yaml}.
 * <p>
 * El calendario es público si el alojamiento está ACTIVA. Registrar,
 * modificar y eliminar períodos exige ser el anfitrión propietario o un
 * administrador (RNF-004).
 * </p>
 */
@RestController
@RequestMapping("/alojamientos/{idAlojamiento}/disponibilidad")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Disponibilidad", description = "Períodos disponibles y fechas reservadas de un alojamiento")
public class DisponibilidadController {

	/** Mensaje del 400 por fechas inválidas. */
	private static final String FECHAS_INVALIDAS = "Debes indicar la fecha de inicio y la de fin del período. | Las "
			+ "fechas son inválidas: el fin del período debe ser posterior al inicio. | El período no puede terminar "
			+ "en el pasado.";

	/** Servicio del calendario. */
	private final CalendarioService calendarioService;

	/**
	 * Crea el controlador.
	 *
	 * @param calendarioService servicio del calendario
	 */
	public DisponibilidadController(CalendarioService calendarioService) {
		this.calendarioService = calendarioService;
	}

	/**
	 * Calendario: períodos disponibles y rangos reservados. Es público si el
	 * alojamiento está ACTIVA; en otro estado solo lo ven su anfitrión o un
	 * administrador. Ruta: {@code GET /alojamientos/{idAlojamiento}/disponibilidad}.
	 *
	 * @param idAlojamiento  alojamiento
	 * @param authentication usuario autenticado; puede ser {@code null}
	 * @return 200 con el calendario; 404 si no existe o no se puede ver
	 */
	@Operation(summary = "Calendario: períodos disponibles y rangos reservados", description = "Público si el "
			+ "alojamiento está ACTIVA; en otro estado solo para su anfitrión o un administrador.")
	@SecurityRequirements
	@ApiResponse(responseCode = "200", description = "Períodos ofrecidos y fechas reservadas")
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@GetMapping
	public ResponseEntity<Object> calendario(@PathVariable Long idAlojamiento, Authentication authentication) {
		return RespuestaUtil.desde(calendarioService.calendario(idAlojamiento, RespuestaUtil.idUsuario(authentication),
				RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Registra un período disponible (CA-115). Ruta:
	 * {@code POST /alojamientos/{idAlojamiento}/disponibilidad}.
	 *
	 * @param idAlojamiento  alojamiento
	 * @param datos          fechas del período
	 * @param authentication anfitrión propietario o administrador
	 * @return 201 con el período; 400 si las fechas no son válidas; 403 si no
	 *         es el dueño; 404 si no existe; 409 si se cruza con otro período
	 */
	@Operation(summary = "Registrar período disponible (propietario o ADMIN)", description = "Las dos fechas quedan "
			+ "incluidas en el período. No puede cruzarse con otro período del alojamiento.")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "201", description = "Período creado (CA-115)")
	@ApiResponse(responseCode = "400", description = FECHAS_INVALIDAS)
	@ApiResponse(responseCode = "403", description = CalendarioService.SIN_PERMISO)
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE)
	@ApiResponse(responseCode = "409", description = CalendarioService.CRUCE_AL_CREAR)
	@PostMapping
	public ResponseEntity<Object> agregar(@PathVariable Long idAlojamiento, @RequestBody PeriodoDisponibilidadDTO datos,
			Authentication authentication) {
		return RespuestaUtil.desde(calendarioService.agregarPeriodo(idAlojamiento, datos,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)), HttpStatus.CREATED);
	}

	/**
	 * Modifica un período; no puede dejar por fuera reservas existentes
	 * (CA-117, CA-118). Ruta:
	 * {@code PUT /alojamientos/{idAlojamiento}/disponibilidad/{idPeriodo}}.
	 *
	 * @param idAlojamiento  alojamiento
	 * @param idPeriodo      período
	 * @param datos          fechas nuevas
	 * @param authentication anfitrión propietario o administrador
	 * @return 200 con el período; 400, 403, 404 o 409 según el caso
	 */
	@Operation(summary = "Modificar período (no puede afectar reservas existentes)")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Período actualizado (CA-117)")
	@ApiResponse(responseCode = "400", description = FECHAS_INVALIDAS)
	@ApiResponse(responseCode = "403", description = CalendarioService.SIN_PERMISO)
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE + " | "
			+ CalendarioService.PERIODO_NO_EXISTE)
	@ApiResponse(responseCode = "409", description = CalendarioService.CRUCE_AL_MODIFICAR + " | No puedes modificar "
			+ "el período: afecta la reserva del 2026-12-01 al 2026-12-05.")
	@PutMapping("/{idPeriodo}")
	public ResponseEntity<Object> actualizar(@PathVariable Long idAlojamiento, @PathVariable Long idPeriodo,
			@RequestBody PeriodoDisponibilidadDTO datos, Authentication authentication) {
		return RespuestaUtil.desde(calendarioService.actualizarPeriodo(idAlojamiento, idPeriodo, datos,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication)));
	}

	/**
	 * Elimina un período sin reservas. Ruta:
	 * {@code DELETE /alojamientos/{idAlojamiento}/disponibilidad/{idPeriodo}}.
	 *
	 * @param idAlojamiento  alojamiento
	 * @param idPeriodo      período
	 * @param authentication anfitrión propietario o administrador
	 * @return 200 si se eliminó; 403 si no es el dueño; 404 si no existe; 409
	 *         si tiene reservas
	 */
	@Operation(summary = "Eliminar período sin reservas (propietario o ADMIN)")
	@PreAuthorize("hasAnyRole('ANFITRION','ADMIN')")
	@ApiResponse(responseCode = "200", description = "Período eliminado.")
	@ApiResponse(responseCode = "403", description = CalendarioService.SIN_PERMISO)
	@ApiResponse(responseCode = "404", description = CalendarioService.NO_EXISTE + " | "
			+ CalendarioService.PERIODO_NO_EXISTE)
	@ApiResponse(responseCode = "409", description = "No puedes eliminar el período: tiene reservas activas.")
	@DeleteMapping("/{idPeriodo}")
	public ResponseEntity<Object> eliminar(@PathVariable Long idAlojamiento, @PathVariable Long idPeriodo,
			Authentication authentication) {
		Resultado<Void> resultado = calendarioService.eliminarPeriodo(idAlojamiento, idPeriodo,
				RespuestaUtil.idUsuario(authentication), RespuestaUtil.esAdmin(authentication));
		return resultado.esExito() ? RespuestaUtil.mensaje(HttpStatus.OK, "Período eliminado.")
				: RespuestaUtil.desde(resultado);
	}
}
