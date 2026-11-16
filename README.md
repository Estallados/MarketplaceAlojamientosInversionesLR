# MarketplaceAlojamientosInversionesLR
Marketplace web de alojamientos temporales que conecta anfitriones y huéspedes: publicación, búsqueda, disponibilidad, cotización, reservas y cancelaciones. Arquitectura orientada a servicios (REST + SPA). Proyecto de Ingeniería de Software 1 — Universidad El Bosque.

## Pipeline de CI (GitHub Actions)

En cada Pull Request hacia `main` se ejecutan los workflows de `.github/workflows/`:

| Workflow | Check | Qué hace |
|---|---|---|
| `ci.yml` | `backend` | Java 17 + `mvn verify` (compila y corre tests) |
| `ci.yml` | `frontend` | `ng build` y `ng test` en modo headless |
| `ci.yml` | `sonar` | Análisis de calidad en SonarQube Cloud |
| `pr-format.yml` | `validar` | Valida el título del PR y el nombre de la rama |

Los cuatro checks son **requeridos** en `main`: el PR no se puede fusionar hasta que todos estén en verde.

### Convenciones

- **Título del PR:** `[SCRUM-n] Descripción` (ej. `[SCRUM-191] Pipeline GitHub Actions`)
- **Nombre de la rama:** `tipo/SCRUM-n-descripcion`, con tipo `feat`, `fix`, `chore`, `docs`, `refactor` o `test` (ej. `chore/SCRUM-191-pipeline-actions`)

### Ejecución local

Requisitos: Java 17, Maven, Node 22 y Google Chrome (para los tests del frontend).

**Backend**

```bash
cd marketplaceInversionesLRBack
mvn -B verify
```

No necesitas configurar `AES_SECRET_KEY`: el `pom.xml` la define para los tests. Las pruebas de
integración levantan un PostgreSQL temporal con Docker (Testcontainers); sin Docker se omiten.
El reporte de cobertura queda en `marketplaceInversionesLRBack/target/site/jacoco/jacoco.xml`.

**Frontend**

```bash
cd marketplaceInversionesLRFront
npm ci
npx ng build
npx ng test --watch=false --browsers=ChromeHeadless
```

**Análisis de Sonar (opcional)**

```bash
export SONAR_TOKEN=<tu_token>
cd marketplaceInversionesLRBack
mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.projectKey=Estallados_MarketplaceAlojamientosInversionesLR \
  -Dsonar.organization=estallados \
  -Dsonar.host.url=https://sonarcloud.io
```

### Configuración requerida en GitHub

- Secreto de repositorio `SONAR_TOKEN` (*Settings → Secrets and variables → Actions*).
- `GITHUB_TOKEN` lo genera GitHub automáticamente; no hay que crearlo.

## Manual de despliegue

### Arquitectura desplegada

| Pieza | Dónde corre | Dirección |
|---|---|---|
| Frontend (Angular servido por nginx) | Render, Web Service con Docker | https://marketplacealojamientosinversioneslr-2.onrender.com |
| Backend (Spring Boot, Java 17) | Render, Web Service con Docker | https://marketplacealojamientosinversioneslr.onrender.com |
| Base de datos (PostgreSQL) | Neon | Se configura con `DB_URL` |
| Imágenes de los alojamientos | Cloudinary | Se configura con `CLOUDINARY_URL` |
| Correo (verificación de cuenta, recuperación de contraseña) | SMTP de Brevo (`smtp-relay.brevo.com:2525`, STARTTLS) | Se configura con `MAIL_*` |

El navegador descarga el front desde Render y este llama al backend por HTTPS. El backend guarda los
datos en Neon, las imágenes en Cloudinary y envía los correos por Brevo. La documentación de la API
queda en `https://marketplacealojamientosinversioneslr.onrender.com/swagger-ui.html`.

### Requisitos

- Cuenta en [Render](https://render.com) con acceso al repositorio de GitHub.
- Base de datos PostgreSQL en [Neon](https://neon.tech) con el esquema del proyecto creado (ver
  [Base de datos](#base-de-datos)).
- Cuenta de [Cloudinary](https://cloudinary.com) y cuenta de [Brevo](https://www.brevo.com) con un
  remitente verificado.
- Para probar en local: Docker.

### Variables de entorno del backend

Se configuran en Render (*Environment*) o, en local, en `marketplaceInversionesLRBack/.env`.
**Ningún valor se guarda en el repositorio:** `.gitignore` y `.dockerignore` excluyen los archivos
`.env`. Los valores reales los tiene el equipo y se comparten por un canal privado, nunca en Jira,
Confluence ni en el código.

| Variable | Obligatoria | Secreta | Para qué sirve |
|---|---|---|---|
| `DB_URL` | Sí | Sí | URL JDBC de Neon: `jdbc:postgresql://<host>/<base>?sslmode=require` |
| `DB_USERNAME` | Sí | Sí | Usuario de la base de datos |
| `DB_PASSWORD` | Sí | Sí | Contraseña de la base de datos |
| `AES_SECRET_KEY` | Sí | Sí | Clave en Base64 de 16, 24 o 32 bytes. Cifra datos personales (como el correo) y firma los tokens JWT. Generar con `openssl rand -base64 32`. **No se cambia en una base con datos:** con otra clave los datos cifrados ya no se pueden leer |
| `MAIL_USERNAME` | Sí | Sí | Usuario SMTP de Brevo (`xxx@smtp-brevo.com`) |
| `MAIL_PASSWORD` | Sí | Sí | Clave SMTP de Brevo |
| `MAIL_FROM` | Sí | No | Remitente verificado en Brevo; es el "De" de todos los correos |
| `CLOUDINARY_URL` | Sí | Sí | `cloudinary://<api_key>:<api_secret>@<cloud_name>`. Sin ella, subir imágenes responde 503 |
| `APP_CORS_ALLOWED_ORIGINS` | No | No | Orígenes del front separados por coma. Por defecto, el front de Render y `http://localhost:4200` |
| `CLOUDINARY_CARPETA` | No | No | Carpeta de Cloudinary; separa ambientes en la misma cuenta. Por defecto `marketplace` |
| `GOOGLE_CLIENT_ID` | No | No | Client ID de OAuth para iniciar sesión con Google. Es público; ya tiene un valor por defecto |
| `STRIPE_SECRET_KEY` / `STRIPE_PUBLISHABLE_KEY` | No | La secreta sí | Claves de Stripe en modo prueba (`sk_test_`, `pk_test_`). Sin ellas, los pagos usan el simulador local |
| `GEOCODING_HABILITADO` | No | No | `false` apaga la búsqueda de coordenadas en OpenStreetMap. Por defecto `true` |
| `JAVA_OPTS` | No | No | Opciones de la JVM. Por defecto `-XX:MaxRAMPercentage=75` |
| `PORT` | No | No | Puerto del servidor. Render lo asigna solo; en local es 8081 |

### Variables de entorno del frontend

| Variable | Obligatoria | Para qué sirve |
|---|---|---|
| `API_URL` | No | Dirección pública del backend, sin `/` al final. Por defecto, el backend de Render. Al arrancar, el contenedor la escribe en `config.js`, así la misma imagen sirve para cualquier ambiente |
| `PORT` | No | Puerto de nginx. Render lo asigna solo; por defecto 80 |

### Base de datos

El backend no crea ni modifica tablas (`spring.jpa.hibernate.ddl-auto=none`): usa el esquema que ya
existe en Neon. Para el MVP se usa la base del equipo, que ya tiene el esquema y los datos.

Para una base nueva y vacía, se puede arrancar el backend **una sola vez** con la variable
`SPRING_JPA_HIBERNATE_DDL_AUTO=update`, para que Hibernate cree las tablas a partir de las entidades, y
después quitarla. Ese esquema no incluye las reglas propias de Neon (por ejemplo, la restricción
`ex_reserva_sin_cruces` que impide reservas cruzadas en la base de datos); el backend igual revalida
la disponibilidad antes de confirmar cada reserva.

### Paso 1. Desplegar el backend en Render

1. En Render: **New → Web Service** y elegir el repositorio de GitHub.
2. Configurar:
   - **Runtime:** Docker.
   - **Branch:** `main`.
   - **Root Directory:** `marketplaceInversionesLRBack` (ahí está su `Dockerfile`).
   - **Health Check Path:** `/actuator/health`.
3. En **Environment**, agregar las variables obligatorias de la tabla del backend.
4. **Create Web Service.** Render construye la imagen (compila con Maven sin correr las pruebas, que
   ya corren en el CI) y arranca el contenedor como un usuario sin privilegios.
5. Esperar a que el log muestre `Started MarketplaceInversionesLrApplication` y anotar la dirección
   pública del servicio.

### Paso 2. Desplegar el frontend en Render

1. **New → Web Service** con el mismo repositorio.
2. Configurar:
   - **Runtime:** Docker.
   - **Branch:** `main`.
   - **Root Directory:** `marketplaceInversionesLRFront`.
3. En **Environment**, poner `API_URL` con la dirección del backend del paso 1 (sin `/` al final).
4. **Create Web Service.** La imagen compila Angular y lo sirve con nginx.
5. Si la dirección del front no es la de siempre, agregarla en el backend en
   `APP_CORS_ALLOWED_ORIGINS` (junto con `http://localhost:4200` si se sigue desarrollando en local) y
   reiniciar el backend. Sin eso, el navegador bloquea las llamadas por CORS.

### Paso 3. Verificar el despliegue

| Qué | Cómo | Resultado esperado |
|---|---|---|
| Backend vivo | Abrir `<backend>/actuator/health` | `{"status":"UP", ...}` |
| Base de datos | Abrir `<backend>/alojamientos?pagina=0&tamanio=5` | 200 con la lista de alojamientos |
| API documentada | Abrir `<backend>/swagger-ui.html` | Swagger con todos los endpoints |
| Front conectado | Abrir `<front>/config.js` | `apiUrl` con la dirección del backend |
| Flujo completo | Entrar al front, iniciar sesión y buscar alojamientos | Se ven los alojamientos con sus imágenes |

### Actualizaciones

Cada merge a `main` vuelve a desplegar los dos servicios automáticamente (*Auto-Deploy* de Render).
Antes de fusionar, el PR debe pasar los checks del CI. Para volver a una versión anterior: en Render,
**Events**, elegir un despliegue anterior y **Rollback**, o revertir el commit en `main`.

### Ejecutar con Docker en local

Backend (lee las variables de `marketplaceInversionesLRBack/.env`, que no se sube a git):

```bash
cd marketplaceInversionesLRBack
docker build -t inversioneslr-back .
docker run --env-file .env -p 8081:8081 inversioneslr-back
```

Frontend, apuntando al backend local:

```bash
cd marketplaceInversionesLRFront
docker build -t inversioneslr-front .
docker run -p 8080:80 -e API_URL=http://localhost:8081 inversioneslr-front
```

Luego abrir `http://localhost:8080`.
