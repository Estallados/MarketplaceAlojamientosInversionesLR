# Pruebas end-to-end del flujo de reserva (SCRUM-205 / T17)

Prueba en el navegador, con Playwright, el flujo completo del Marketplace: **publicar → buscar → disponibilidad →
cotizar → reservar**, con el backend y el frontend reales y los usuarios de la demostración.

## Criterios de T17

| Criterio | Cómo se cumple | Estado |
|---|---|---|
| Prueba E2E (Cypress o Playwright) con datos semilla | `tests/flujo-reserva.spec.ts` con Playwright; usa los usuarios, políticas y tarifas que crea `DEMO_POBLAR=true` | Cumple |
| Ejecutada en el pipeline o documentada con evidencia | Job `flujo-reserva` de `.github/workflows/e2e.yml` en cada PR y push a `main`; el artefacto `evidencia-e2e` trae el reporte HTML, el video, las capturas y la traza de cada paso | Cumple |
| Guion de demo de 5 minutos | `demo/guion-5-minutos.md` | Cumple |

## Qué hace la prueba

Cada paso abre un navegador nuevo, como tres personas distintas.

| Paso | Usuario | Acciones y verificaciones |
|---|---|---|
| 1. Publicar | Laura Gómez (anfitriona) | Crea "Casa E2E Playwright NNNNNN" en Villa de Leyva en los 4 pasos del asistente, sube una foto y guarda el borrador; registra un periodo disponible de mañana a 60 días y la envía a revisión. Verifica el estado **Pendiente de revisión** |
| 2. Aprobar | Andrés Castillo (administrador) | La encuentra en Revisar publicaciones, la aprueba y verifica el estado **Activa** |
| 3. Reservar | Ana Torres (huésped) | Busca "Villa de Leyva" desde el inicio, abre el alojamiento, consulta disponibilidad para 3 noches (dentro de 10 a 13 días), revisa la cotización, confirma y paga con la tarjeta de prueba 4242 4242 4242 4242. Verifica la reserva **CONFIRMADA**, el pago **Aprobado** y que aparezca en Mis reservas |

El título lleva un número distinto en cada corrida, así la prueba se puede repetir sobre la misma base de datos.

## Archivos

| Archivo | Contenido |
|---|---|
| `tests/flujo-reserva.spec.ts` | La prueba |
| `playwright.config.ts` | Navegador Chromium, un solo worker, video, captura y traza siempre activos, reporte HTML |
| `fixtures/alojamiento.jpg` | Foto que se sube al publicar |
| `cloudinary-falso.mjs` | Servidor que imita la API de Cloudinary (puerto 9000) para subir fotos sin cuenta real |

## En el CI

El job `flujo-reserva` de `.github/workflows/e2e.yml`:

1. Levanta PostgreSQL 16 y arranca `cloudinary-falso.mjs`.
2. Compila y arranca el backend con `SPRING_JPA_HIBERNATE_DDL_AUTO=create`, `DEMO_POBLAR=true` y
   `CLOUDINARY_URL=cloudinary://clave:secreto@e2e?upload_prefix=http://localhost:9000`, que manda las subidas de
   fotos al servidor falso.
3. Construye la imagen Docker del frontend y la arranca en `http://localhost:4200` apuntando al backend.
4. Instala Playwright y Chromium y ejecuta la prueba.
5. Sube el artefacto **evidencia-e2e** (también si falla): `playwright-report/` (abrir `index.html`),
   `test-results/` con el video y la traza de cada paso, y los logs del backend, del frontend y de Cloudinary.

Para ver la evidencia: pestaña **Actions** → corrida **E2E** → sección **Artifacts** → `evidencia-e2e`. La traza
se abre arrastrando el `trace.zip` a https://trace.playwright.dev.

## Ejecución local

Necesita el backend en `http://localhost:8081` con `DEMO_POBLAR=true` y el frontend en `http://localhost:4200`.
Para subir la foto el backend necesita una cuenta de Cloudinary (`CLOUDINARY_URL`) o el servidor falso:

```bash
# Terminal 1: Cloudinary falso (opcional si ya tienes CLOUDINARY_URL real)
node e2e/cloudinary-falso.mjs

# Terminal 2: backend con DEMO_POBLAR=true y, si usas el servidor falso,
# CLOUDINARY_URL=cloudinary://clave:secreto@e2e?upload_prefix=http://localhost:9000

# Terminal 3: frontend
cd marketplaceInversionesLRFront && npx ng serve

# Terminal 4: la prueba
cd e2e
npm install --no-save --no-package-lock @playwright/test@1.49.1
npx playwright install chromium
npx playwright test            # sin ventana
npx playwright test --headed   # viendo el navegador
npx playwright show-report     # reporte HTML con videos y capturas
```

`node_modules`, `test-results` y `playwright-report` están en `.gitignore`: no se suben al repositorio.

La prueba crea una publicación y una reserva. No la ejecutes contra producción.
