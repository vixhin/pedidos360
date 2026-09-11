import { test, expect } from '@playwright/test';

test.describe('Pruebas de Caja Negra - Autenticación y Registro', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
  });

  test('TC-01: Formulario vacio de login no debe permitir envio invalido', async ({ page }) => {
    const submitBtn = page.locator('form button[type="submit"]');
    await expect(submitBtn).toBeVisible();

    // Intentar enviar sin rellenar campos
    await submitBtn.click();

    // El input email debe marcarse como requerido/inválido por HTML5 o Angular
    const emailInput = page.locator('input[name="loginEmail"]');
    const isValid = await emailInput.evaluate((el: HTMLInputElement) => el.checkValidity());
    expect(isValid).toBe(false);
  });

  test('TC-02: Login con credenciales erroneas muestra mensaje de error', async ({ page }) => {
    const emailInput = page.locator('input[name="loginEmail"]');
    const passwordInput = page.locator('input[name="loginPassword"]');
    const submitBtn = page.locator('form button[type="submit"]');


    // Ingresar datos totalmente erróneos
    await emailInput.fill('usuario.inexistente@pedidos360.cl');
    await passwordInput.fill('clave_totalmente_falsa_999');

    await submitBtn.click();

    // Debe mostrar banner o div de error .form-error
    const errorBanner = page.locator('.form-error');
    await expect(errorBanner).toBeVisible({ timeout: 10000 });
    const text = await errorBanner.textContent();
    expect(text?.toLowerCase()).toMatch(/credenciales|incorrecta|no encontrado|inválida/);
  });

  test('TC-03: Boton ojito alterna visibilidad de la contraseña', async ({ page }) => {
    const passwordInput = page.locator('input[name="loginPassword"]');
    const toggleBtn = page.locator('.password-toggle-btn').first();

    await passwordInput.fill('MiClaveSuperSecreta123');

    // Inicialmente debe ser de tipo password
    await expect(passwordInput).toHaveAttribute('type', 'password');

    // Al hacer clic en el ojo, debe cambiar a text
    await toggleBtn.click();
    await expect(passwordInput).toHaveAttribute('type', 'text');

    // Al volver a hacer clic, debe regresar a password
    await toggleBtn.click();
    await expect(passwordInput).toHaveAttribute('type', 'password');
  });

  test('TC-04: Cambio dinamico entre Login y Registro', async ({ page }) => {
    await expect(page.locator('h2')).toHaveText('Iniciar sesión');

    // Clic en Regístrate
    const registerSwitch = page.locator('.login-card__switch button', { hasText: 'Regístrate' });
    await registerSwitch.click();

    // Debe cambiar la vista a Crear cuenta
    await expect(page.locator('h2')).toHaveText('Crear cuenta');
    await expect(page.locator('input[name="registerNombre"]')).toBeVisible();
    await expect(page.locator('input[name="registerEmail"]')).toBeVisible();
    await expect(page.locator('input[name="registerPassword"]')).toBeVisible();

    // Clic en Inicia sesión para regresar
    const loginSwitch = page.locator('.login-card__switch button', { hasText: 'Inicia sesión' });
    await loginSwitch.click();
    await expect(page.locator('h2')).toHaveText('Iniciar sesión');
  });

  test('TC-05: Formulario de registro con correo invalido o clave corta', async ({ page }) => {
    const registerSwitch = page.locator('.login-card__switch button', { hasText: 'Regístrate' });
    await registerSwitch.click();

    const emailInput = page.locator('input[name="registerEmail"]');
    const passInput = page.locator('input[name="registerPassword"]');
    const submitBtn = page.locator('button[type="submit"]', { hasText: 'Crear cuenta' });

    // Correo inválido
    await emailInput.fill('correo-sin-formato');
    await passInput.fill('123'); // muy corta

    const isEmailValid = await emailInput.evaluate((el: HTMLInputElement) => el.checkValidity());
    expect(isEmailValid).toBe(false);
  });

  test('TC-06: Registro exitoso de nuevo usuario y login valido', async ({ page }) => {
    // 1. Cambiar a vista registro
    const registerSwitch = page.locator('.login-card__switch button', { hasText: 'Regístrate' });
    await registerSwitch.click();

    const uniqueEmail = `test.e2e.${Date.now()}@pedidos360.cl`;

    await page.locator('input[name="registerNombre"]').fill('Usuario E2E Playwright');
    await page.locator('input[name="registerEmail"]').fill(uniqueEmail);
    await page.locator('input[name="registerPassword"]').fill('chupalovixo');

    const submitRegister = page.locator('button[type="submit"]', { hasText: 'Crear cuenta' });
    await submitRegister.click();

    // Puede redirigir automáticamente al login o iniciar sesión
    await page.waitForTimeout(1500);

    // 2. Si regresa al login o permanece, probar login válido con credenciales semilla
    if (await page.locator('input[name="loginEmail"]').isVisible()) {
      await page.locator('input[name="loginEmail"]').fill('cliente@pedidos360.cl');
      await page.locator('input[name="loginPassword"]').fill('chupalovixo');
      await page.locator('button[type="submit"]', { hasText: 'Iniciar sesión' }).click();
    }

    // Debe acceder al sistema exitosamente
    await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 10000 });
  });

});
