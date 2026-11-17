import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas E2E del flujo publicar → buscar → disponibilidad → cotizar → reservar (SCRUM-205).
 * Necesitan el frontend en E2E_BASE_URL (por defecto http://localhost:4200) conectado a un
 * backend con DEMO_POBLAR=true. Ver e2e/README.md.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:4200',
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    viewport: { width: 1280, height: 860 },
    // Evidencia de cada corrida: video, capturas y traza
    video: 'on',
    screenshot: 'on',
    trace: 'on',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 860 } } }],
});
