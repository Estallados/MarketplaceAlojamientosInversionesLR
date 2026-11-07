# OpenAPI

Contrato de la API REST del Marketplace. Lo usan el frontend para generar su cliente (`api:gen`) y para levantar un servidor mock (`api:mock`), de modo que el front pueda trabajar mientras el backend se termina.

| Archivo | Contenido |
|---|---|
| [openapi.yaml](openapi.yaml) | Contrato OpenAPI 3.0.3: 86 operaciones en 13 etiquetas (un controlador por etiqueta) y 19 esquemas de DTO |

## Origen del contrato

El contrato se genera a partir de los controladores (`@RequestMapping`, `@GetMapping`, etc.) y de los DTO del backend. Las rutas, los parámetros de ruta y de consulta, los cuerpos de solicitud y la seguridad (`bearerAuth`) salen del código.

Limitaciones de esta primera versión:

- Las respuestas son genéricas (`object`), porque los controladores devuelven `ResponseEntity<Object>`. Solo `Pago/configuracion`, `Usuario/listar` y `Alojamiento/tipos` tienen respuesta tipada.
- La seguridad se infiere del parámetro `Authentication`. `Alojamiento/detalle` acepta autenticación opcional y aquí se marca como requerida.
- Los endpoints de `/admin` están marcados como de rol ADMIN en la descripción; el contrato no valida el rol.

## Uso

Desde `marketplaceInversionesLRFront`:

```bash
npm run api:gen   # genera el cliente Angular en src/app/api
npm run api:mock  # sirve el contrato como mock en http://localhost:4010
```

## Procedimiento

1. Si cambia un endpoint o un DTO del backend, regenera `openapi.yaml` en el mismo PR.
2. Vuelve a ejecutar `npm run api:gen` y confirma el cliente generado junto con el contrato.
