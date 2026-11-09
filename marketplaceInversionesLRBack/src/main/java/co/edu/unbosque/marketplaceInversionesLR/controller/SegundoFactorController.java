package co.edu.unbosque.marketplaceInversionesLR.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.marketplaceInversionesLR.dto.SegundoFactorDTO;
import co.edu.unbosque.marketplaceInversionesLR.service.SegundoFactorService;
import co.edu.unbosque.marketplaceInversionesLR.util.RespuestaUtil;
import co.edu.unbosque.marketplaceInversionesLR.util.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Segundo factor de autenticación con app autenticadora (HU-30). Ruta base:
 * {@code /usuarios/segundofactor}. Contrato: {@code docs/openapi/identidad.yaml}.
 * <p>
 * Activarlo tiene dos pasos: {@code activar} genera el secreto y la URI para el
 * código QR, y {@code confirmar} lo habilita con un código de la app. Todas
 * las rutas usan el usuario del token, así la configuración queda asociada
 * solo a su cuenta (CA-178). Con el segundo factor habilitado, el inicio de
 * sesión pide el código de la app (CA-174); sin él, sigue el mecanismo normal
 * (CA-177).
 * </p>
 */
@RestController
@RequestMapping("/usuarios/segundofactor")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Segundo factor", description = "Autenticación en dos pasos con app autenticadora")
public class SegundoFactorController {

	/** Mensaje cuando se habilita. */
	public static final String HABILITADO = "Segundo factor habilitado.";

	/** Mensaje cuando se deshabilita. */
	public static final String DESHABILITADO = "Segundo factor deshabilitado.";

	/** Servicio del segundo factor. */
	private final SegundoFactorService segundoFactorService;

	/**
	 * Crea el controlador.
	 *
	 * @param segundoFactorService servicio del segundo factor
	 */
	public SegundoFactorController(SegundoFactorService segundoFactorService) {
		this.segundoFactorService = segundoFactorService;
	}

	/**
	 * Genera el secreto TOTP del usuario (paso 1). Queda pendiente hasta que lo
	 * confirme. Ruta: {@code POST /usuarios/segundofactor/activar}.
	 *
	 * @param authentication usuario autenticado
	 * @return 200 con el secreto y la URI otpauth; 409 si ya está habilitado
	 */
	@Operation(summary = "Generar el secreto TOTP (paso 1)", description = "Devuelve el secreto y la URI otpauth "
			+ "para registrarlo en la app autenticadora. Queda pendiente hasta confirmarlo con un código.")
	@ApiResponse(responseCode = "200", description = "{secreto, uri}: la URI otpauth se muestra como código QR")
	@ApiResponse(responseCode = "404", description = SegundoFactorService.NO_ENCONTRADO)
	@ApiResponse(responseCode = "409", description = SegundoFactorService.YA_HABILITADO)
	@PostMapping("/activar")
	public ResponseEntity<Object> activar(Authentication authentication) {
		return RespuestaUtil.desde(segundoFactorService.activar(RespuestaUtil.idUsuario(authentication)));
	}

	/**
	 * Confirma la activación con un código de la app (paso 2). Ruta:
	 * {@code POST /usuarios/segundofactor/confirmar}.
	 *
	 * @param datos          código de 6 dígitos
	 * @param authentication usuario autenticado
	 * @return 200 si queda habilitado; 400 si el código no es válido; 409 si no
	 *         se pidió la activación
	 */
	@Operation(summary = "Confirmar la activación con un código (paso 2)", description = "Desde ahora el inicio de "
			+ "sesión de esta cuenta pide el código de la app autenticadora.")
	@ApiResponse(responseCode = "200", description = HABILITADO)
	@ApiResponse(responseCode = "400", description = SegundoFactorService.CODIGO_INVALIDO)
	@ApiResponse(responseCode = "404", description = SegundoFactorService.NO_ENCONTRADO)
	@ApiResponse(responseCode = "409", description = SegundoFactorService.SIN_ACTIVACION)
	@PostMapping("/confirmar")
	public ResponseEntity<Object> confirmar(@RequestBody SegundoFactorDTO datos, Authentication authentication) {
		Resultado<Void> resultado = segundoFactorService.confirmar(RespuestaUtil.idUsuario(authentication),
				datos.getCodigo());
		return resultado.esExito() ? RespuestaUtil.mensaje(HttpStatus.OK, HABILITADO) : RespuestaUtil.desde(resultado);
	}

	/**
	 * Deshabilita el segundo factor con un código actual. Ruta:
	 * {@code POST /usuarios/segundofactor/desactivar}.
	 *
	 * @param datos          código de 6 dígitos
	 * @param authentication usuario autenticado
	 * @return 200 si queda deshabilitado; 400 si el código no es válido; 409 si
	 *         no estaba habilitado
	 */
	@Operation(summary = "Deshabilitar el segundo factor con un código actual", description = "Después, el inicio de "
			+ "sesión sigue el mecanismo normal.")
	@ApiResponse(responseCode = "200", description = DESHABILITADO)
	@ApiResponse(responseCode = "400", description = SegundoFactorService.CODIGO_INVALIDO)
	@ApiResponse(responseCode = "404", description = SegundoFactorService.NO_ENCONTRADO)
	@ApiResponse(responseCode = "409", description = SegundoFactorService.NO_HABILITADO)
	@PostMapping("/desactivar")
	public ResponseEntity<Object> desactivar(@RequestBody SegundoFactorDTO datos, Authentication authentication) {
		Resultado<Void> resultado = segundoFactorService.desactivar(RespuestaUtil.idUsuario(authentication),
				datos.getCodigo());
		return resultado.esExito() ? RespuestaUtil.mensaje(HttpStatus.OK, DESHABILITADO)
				: RespuestaUtil.desde(resultado);
	}
}
