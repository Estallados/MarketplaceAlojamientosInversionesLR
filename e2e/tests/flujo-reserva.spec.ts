import { test, expect, type Browser, type Page } from '@playwright/test';
import path from 'node:path';

/*
 * Flujo completo del enunciado con los usuarios de la demostración (DEMO_POBLAR=true):
 *   1. La anfitriona publica un alojamiento (4 pasos, foto, periodo disponible) y lo envía a revisión.
 *   2. El administrador lo aprueba.
 *   3. El huésped lo busca, consulta disponibilidad, cotiza, reserva y paga con la tarjeta de prueba.
 * Cada paso usa su propio navegador, como tres personas distintas.
 */

const CONTRASENIA = process.env.DEMO_CONTRASENIA ?? 'Demo#2026';
const ANFITRION = 'lauragomez@gmail.com';
const ADMIN = 'andrescastillo@gmail.com';
const HUESPED = 'anatorres@gmail.com';

const sufijo = Date.now().toString().slice(-6);
const TITULO = `Casa E2E Playwright ${sufijo}`;
const CIUDAD = 'Villa de Leyva';
const FOTO = path.join(__dirname, '..', 'fixtures', 'alojamiento.jpg');

/** Fecha local AAAA-MM-DD a n días de hoy. */
function dentroDe(dias: number): string {
  const f = new Date();
  f.setDate(f.getDate() + dias);
  return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`;
}

async function iniciarSesion(browser: Browser, correo: string): Promise<Page> {
  const contexto = await browser.newContext();
  const page = await contexto.newPage();
  await page.goto('/login');
  await page.locator('#correo').fill(correo);
  await page.locator('#contrasena').fill(CONTRASENIA);
  await page.locator('form button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login/);
  return page;
}

let idAlojamiento = '';

test.describe.serial('Flujo publicar, buscar, disponibilidad, cotizar y reservar', () => {
  test('1. La anfitriona publica el alojamiento y lo envía a revisión', async ({ browser }) => {
    const page = await iniciarSesion(browser, ANFITRION);
    await page.goto('/host/alojamientos/crear');

    // Paso 1: información del alojamiento
    await page.locator('#titulo').fill(TITULO);
    await page.locator('#ciudad').fill(CIUDAD);
    await page.locator('#descripcion').fill('Casa campestre de prueba creada por Playwright.');
    await page.locator('#ubicacion').fill('Carrera 9 # 12-30');
    await page.locator('#tipo').selectOption('Casa');
    await page.locator('#capacidad').fill('4');
    await page.locator('#habitaciones').fill('2');
    await page.locator('#camas').fill('2');
    await page.locator('#banos').fill('1');
    await page.locator('#precioNoche').fill('200000');
    await page.locator('[data-siguiente]').click();

    // Paso 2: horarios y reglas
    await page.locator('#horaEntrada').fill('15:00');
    await page.locator('#horaSalida').fill('11:00');
    await page.locator('#reglas').fill('No se permiten fiestas.');
    await page.locator('#idPolitica').selectOption({ index: 1 });
    await page.locator('[data-siguiente]').click();

    // Paso 3: servicios incluidos
    await page.getByLabel('Wifi', { exact: true }).check();
    await page.locator('[data-siguiente]').click();

    // Paso 4: fotografía y guardar el borrador
    await page.locator('#imagenes').setInputFiles(FOTO);
    await page.locator('[data-guardar-borrador]').click();
    const borrador = page.locator('[data-borrador]');
    await expect(borrador).toContainText(/Borrador #\d+/);
    idAlojamiento = (await borrador.textContent())!.match(/Borrador #(\d+)/)![1];

    // Periodo disponible: de mañana a 60 días
    await page.goto(`/host/alojamientos/${idAlojamiento}/disponibilidad`);
    await page.getByRole('button', { name: 'Agregar disponibilidad' }).click();
    await page.locator('input[name="fechaInicio"]').fill(dentroDe(1));
    await page.locator('input[name="fechaFin"]').fill(dentroDe(60));
    await page.getByRole('button', { name: 'Guardar período' }).click();
    await expect(page.getByText('Período registrado correctamente.')).toBeVisible();

    // Enviar a revisión
    await page.goto(`/host/alojamientos/${idAlojamiento}/editar`);
    await page.locator('[data-enviar-revision]').click();
    await expect(page.locator('[data-estado][data-valor="PENDIENTE_REVISION"]').first()).toBeVisible();
    await page.context().close();
  });

  test('2. El administrador aprueba la publicación', async ({ browser }) => {
    const page = await iniciarSesion(browser, ADMIN);
    await page.goto('/admin/alojamientos');
    await expect(page.getByText(TITULO)).toBeVisible();

    await page.goto(`/admin/alojamientos/${idAlojamiento}`);
    await page.getByRole('button', { name: 'Aprobar publicación' }).click();
    await page.getByRole('button', { name: 'Confirmar aprobación' }).click();
    await expect(page.getByText('Activa', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Aprobar publicación' })).toBeHidden();
    await page.context().close();
  });

  test('3. El huésped busca, consulta disponibilidad, cotiza y reserva', async ({ browser }) => {
    const page = await iniciarSesion(browser, HUESPED);
    const llegada = dentroDe(10);
    const salida = dentroDe(13);

    // Buscar por ciudad desde el inicio
    await page.goto('/home');
    await page.locator('[data-ciudad]').fill(CIUDAD);
    await page.locator('[data-buscar]').click();
    await expect(page).toHaveURL(/\/explorar/);
    await page.getByRole('heading', { name: TITULO }).click();
    await expect(page).toHaveURL(new RegExp(`/alojamientos/${idAlojamiento}$`));

    // Consultar disponibilidad
    await page.getByRole('link', { name: 'Consultar disponibilidad' }).first().click();
    await page.locator('#llegada').fill(llegada);
    await page.locator('#salida').fill(salida);
    await page.locator('[data-consultar]').click();
    await expect(page.locator('[data-disponible]')).toBeVisible();
    await page.locator('[data-continuar]').click();

    // Cotizar: 3 noches a 200.000
    await expect(page).toHaveURL(/\/reservar/);
    await expect(page.locator('[data-desglose]')).toContainText('3 noches');
    await page.locator('[data-confirmar]').click();

    // Pagar con la tarjeta de prueba Visa aprobada
    await page.locator('[data-numero]').fill('4242 4242 4242 4242');
    await page.locator('[data-vencimiento]').fill('12/30');
    await page.locator('[data-cvc]').fill('123');
    await page.locator('[data-pagar]').click();

    const reserva = page.locator('[data-reserva]');
    await expect(reserva).toHaveAttribute('data-estado', 'CONFIRMADA');
    await expect(page.locator('[data-estado-pago]')).toContainText('Aprobado');

    // Las fechas quedan ocupadas: la reserva aparece en Mis reservas
    await page.goto('/reservas');
    await expect(page.getByText(TITULO)).toBeVisible();
    await page.context().close();
  });
});
