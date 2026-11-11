# MarketplaceInversionesLRFront

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.6.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Estructura de carpetas

El código de `src/app` se organiza por funcionalidad. Cada carpeta agrupa sus componentes, servicios, modelos y pruebas:

```
src/app/
├── core/                 Piezas compartidas por toda la aplicación
│   ├── services/         api-url (URL base) y session.service (sesión y roles)
│   ├── interceptors/     auth.interceptor (agrega el token JWT)
│   ├── guards/           role.guard (acceso por rol)
│   └── layout/navbar/    barra de navegación por rol (solo enlaces a rutas que ya existen)
├── auth/                 Registro, inicio de sesión y recuperación de contraseña
│   ├── services/
│   ├── login/
│   ├── recover/
│   └── register/
├── perfil/               Consultar y actualizar el perfil (HU-09)
├── alojamientos/         Publicaciones del anfitrión
│   ├── crear-publicacion/       HU-15
│   ├── editar-publicacion/      HU-16
│   ├── servicios-adicionales/   HU-16
│   ├── fotografias-publicacion/ HU-16 (subir, marcar principal y eliminar imágenes)
│   ├── gestion-estado/          HU-17 (enviar a revisión, pausar y reanudar)
│   ├── insignia-estado/         HU-17 (insignia del estado de la publicación)
│   ├── availability/            HU-20
│   ├── mis-alojamientos/        HU-19 (publicaciones del anfitrión con su estado)
│   ├── models/
│   ├── services/                publicacion-host.service (único servicio HTTP de alojamientos)
│   └── validaciones/
├── disponibilidad/       Consultar disponibilidad para fechas seleccionadas (HU-21)
│   ├── consultar-disponibilidad/
│   ├── models/
│   ├── services/                disponibilidad.service (consulta pública, S04)
│   └── validaciones/            fechas de la estadía (CA-082)
├── cotizacion/           Porcentajes de tarifas (HU-24) y cotización de la estancia
│   ├── tarifas/
│   ├── models/
│   └── services/                tarifas.service y cotizacion.service (POST /cotizaciones, S06)
├── reservas/             Crear reserva con bloqueo automático de fechas (HU-22), cotización con servicios (HU-31) y pago de prueba (HU-27)
│   ├── confirmar-reserva/       huéspedes y servicios adicionales, desglose de la cotización y confirmación inmediata
│   ├── models/
│   ├── pago-reserva/            modal "Confirma y paga" con tarjetas de prueba (HU-27)
│   ├── services/                reserva.service (POST /reservas con Idempotency-Key, S07) y pago.service (GET /pagos/configuracion)
│   └── validaciones/            interpretación de los rechazos del contrato y tarjetas de prueba (HU-27)
├── favoritos/            Gestionar alojamientos favoritos (HU-29)
│   ├── mis-favoritos/           lista del huésped y quitar con el corazón
│   ├── models/
│   └── services/                favorito.service (GET, POST y DELETE /favoritos, S13)
└── api/                  Cliente generado desde OpenAPI (no editar a mano)
```

Reglas:

- Una funcionalidad nueva va en su carpeta (o en una nueva), no en carpetas genéricas como `pages/` o `services/`.
- Lo que usan varias funcionalidades va en `core/`.
- Cada componente vive en su carpeta con el `.ts`, el `.html` y el `.spec.ts` del mismo nombre.

## Cliente de API y mock (OpenAPI)

El cliente Angular de la API se genera desde el contrato [`docs/openapi/openapi.yaml`](../docs/openapi/openapi.yaml) del repositorio, para que el front pueda trabajar en paralelo al backend.

Requisitos: Java 11 o superior (el generador corre sobre Java). Instala las dependencias con `npm ci` antes de usar los scripts.

Generar el cliente en `src/app/api`:

```bash
npm run api:gen
```

Levantar un mock de la API en `http://localhost:4010` a partir del contrato:

```bash
npm run api:mock
```

El cliente generado se revisa y se confirma en el repositorio; no se edita a mano. Para actualizarlo, regenera el contrato y vuelve a ejecutar `npm run api:gen`.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
