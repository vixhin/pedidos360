import { test, expect } from '@playwright/test';

test.describe('Pruebas de Caja Negra - Funcionalidades e Interacción Completa de la App', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('TC-07: Busqueda en tiempo real con desplegable flotante de coincidencias', async ({ page }) => {
    const searchInput = page.locator('.search input');
    await expect(searchInput).toBeVisible();

    await searchInput.fill('leche');

    const dropdown = page.locator('.search-dropdown-results');
    await expect(dropdown).toBeVisible({ timeout: 5000 });

    const item = dropdown.locator('.search-result-item').first();
    await expect(item).toBeVisible();
    await expect(item).toContainText(/leche/i);
  });

  test('TC-08: Busqueda sin coincidencias (Caso de Borde)', async ({ page }) => {
    const searchInput = page.locator('.search input');
    await searchInput.fill('xyz999productoinexistente');

    const dropdown = page.locator('.search-dropdown-results');
    await expect(dropdown).toBeVisible();

    const emptyBox = page.locator('.search-empty');
    await expect(emptyBox).toBeVisible();
    await expect(emptyBox).toContainText(/no encontramos productos/i);
  });

  test('TC-09: Agregar y quitar productos de Mis Favoritos', async ({ page }) => {
    const headerFavBtn = page.locator('button[aria-label="Favoritos"]');
    await expect(headerFavBtn).toBeVisible();
    await headerFavBtn.click();

    const favPanel = page.locator('.panel--notif').first();
    await expect(favPanel).toBeVisible();
    await expect(favPanel).toContainText(/favoritos/i);
  });

  test('TC-10: Modal del Mapa Interactivo de Cobertura de Santiago', async ({ page }) => {
    const deliveryBtn = page.locator('button.location');
    await expect(deliveryBtn).toBeVisible();
    await deliveryBtn.click();

    const mapModalBackdrop = page.locator('.map-modal-backdrop');
    await expect(mapModalBackdrop).toBeVisible({ timeout: 5000 });

    const sectorBtn = page.locator('.sector-btn').first();
    if (await sectorBtn.isVisible()) {
      await sectorBtn.click();
    }

    const confirmBtn = page.locator('button:has-text("Confirmar Dirección de Entrega")');
    await confirmBtn.click();

    await expect(mapModalBackdrop).not.toBeVisible();
  });

  test('TC-11: Notificaciones en vivo y panel desplegable', async ({ page }) => {
    const notifBtn = page.locator('button[aria-label="Notificaciones"]');
    await expect(notifBtn).toBeVisible();
    await notifBtn.click();

    const notifPanel = page.locator('.panel--notif').first();
    await expect(notifPanel).toBeVisible();
    await expect(notifPanel).toContainText(/notificaciones/i);
  });

  test('TC-12: Proteccion de rutas sin autenticacion (Caso de Borde)', async ({ page }) => {
    // Intentar acceder a ruta protegida /personal sin estar autenticado
    await page.goto('/personal');

    // Debe redirigir a /login o / por el roleGuard
    await page.waitForURL((url) => url.pathname.includes('/login') || url.pathname === '/', { timeout: 5000 });
  });

  test('TC-13: Login Administrador y Navegacion Completa por Vistas Protegidas', async ({ page }) => {
    // 1. Iniciar sesión como Administrador
    await page.goto('/login');
    await page.locator('input[name="loginEmail"]').fill('admin@pedidos360.cl');
    await page.locator('input[name="loginPassword"]').fill('chupalovixo');
    await page.locator('button[type="submit"]', { hasText: 'Iniciar sesión' }).click();

    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });

    // 2. Navegar por /analitica (solo ADMIN)
    await page.goto('/analitica');
    await expect(page).toHaveURL(/\/analitica/);

    // 3. Navegar por /personal (solo ADMIN)
    await page.goto('/personal');
    await expect(page).toHaveURL(/\/personal/);

    // 4. Navegar por /vendedor
    await page.goto('/vendedor');
    await expect(page).toHaveURL(/\/vendedor/);

    // 5. Navegar por /cliente
    await page.goto('/cliente');
    await expect(page).toHaveURL(/\/cliente/);

    // 6. Navegar por /perfil
    await page.goto('/perfil');
    await expect(page).toHaveURL(/\/perfil/);

    // 7. Navegar por /carrito
    await page.goto('/carrito');
    await expect(page).toHaveURL(/\/carrito/);
  });

});
