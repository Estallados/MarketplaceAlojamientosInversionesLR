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
│   └── guards/           role.guard (acceso por rol)
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
│   ├── availability/            HU-20
│   ├── models/
│   ├── services/                publicacion-host.service (único servicio HTTP de alojamientos)
│   └── validaciones/
├── disponibilidad/       Consultar disponibilidad para fechas seleccionadas (HU-21)
│   ├── consultar-disponibilidad/
│   ├── models/
│   ├── services/                disponibilidad.service (consulta pública, S04)
│   └── validaciones/            fechas de la estadía (CA-082)
├── cotizacion/           Porcentajes de tarifas (HU-24)
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
