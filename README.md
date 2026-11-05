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
cd backend
mvn -B verify
```

No necesitas configurar `AES_SECRET_KEY`: el `pom.xml` la define para los tests.
El reporte de cobertura queda en `backend/target/site/jacoco/jacoco.xml`.

**Frontend**

```bash
cd frontend
npm ci
npx ng build
npx ng test --watch=false --browsers=ChromeHeadless
```

**Análisis de Sonar (opcional)**

```bash
export SONAR_TOKEN=<tu_token>
cd backend
mvn -B verify org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dsonar.projectKey=Estallados_MarketplaceAlojamientosInversionesLR \
  -Dsonar.organization=estallados \
  -Dsonar.host.url=https://sonarcloud.io
```

### Configuración requerida en GitHub

- Secreto de repositorio `SONAR_TOKEN` (*Settings → Secrets and variables → Actions*).
- `GITHUB_TOKEN` lo genera GitHub automáticamente; no hay que crearlo.
