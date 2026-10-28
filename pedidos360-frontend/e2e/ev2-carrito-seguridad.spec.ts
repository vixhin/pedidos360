import { test, expect, Page, APIRequestContext } from '@playwright/test';

/**
 * Pedidos 360 - Evaluación 2 (ISY1102)
 * Pruebas nuevas de caja negra (UI) y de seguridad (API) que cierran las brechas
 * de trazabilidad detectadas en la EV1:
 *   - CAR-01 / CAR-02: carrito real (en la EV1 se había evidenciado con favoritos).
 *   - ROL-03: usuario autenticado con rol insuficiente (nuevo escenario).
 *   - REG-02: registro con correo ya existente.
 *   - SEC-01..SEC-05: hallazgos del análisis estático verificados contra el sitio desplegado.
 *
 * Ejecución (desde pedidos360-frontend/, copiando este archivo en e2e/):
 *   $env:PLAYWRIGHT_BASE_URL="https://<ip-del-lab>.sslip.io"
 *   npx playwright test e2e/ev2-carrito-seguridad.spec.ts --config=playwright.config.ec2.ts
 *
 * SIN LABORATORIO AWS (todo en un PC con Docker):
 *   cd Backend;  docker compose up -d --build          (levanta los 8 microservicios en localhost)
 *   cd ..\pedidos360-frontend
 *   $env:PEDIDOS_LOCAL="1"
 *   npx playwright test e2e/ev2-carrito-seguridad.spec.ts --config=playwright.config.ts
 *   (usa http://localhost:4200 para la interfaz y los puertos 8081-8086 para la API;
 *    SEC-05 no aplica en local porque el servidor de desarrollo no es el de producción)
 *
 * No se escriben credenciales en el archivo: cada ejecución registra un usuario
 * CLIENTE de prueba con correo y clave generados al vuelo.
 *
 * IMPORTANTE: en las pruebas SEC, el "resultado esperado" es el comportamiento SEGURO.
 * Si una prueba SEC falla, la falla ES el hallazgo (vulnerabilidad confirmada) y debe
 * registrarse como incidente con su criticidad, no "arreglarse" cambiando la aserción.
 */

const stamp = Date.now();
const cliente = {
  nombre: 'Cliente QA EV2',
  email: `qa.ev2.cliente.${stamp}@pedidos360.test`,
  password: `Qa-${stamp}-Ev2!`,
};

// En el sitio desplegado, Caddy enruta /api/* desde el mismo dominio. En local, cada
// microservicio escucha en su propio puerto (docker-compose.yml).
const LOCAL = process.env['PEDIDOS_LOCAL'] === '1';
const PUERTOS: Record<string, number> = {
  '/api/auth': 8081, '/api/usuario': 8081, '/api/pedidos': 8082, '/api/carrito': 8083,
  '/api/analitica': 8084, '/api/productos': 8085, '/api/notificacion': 8086,
};
function api(ruta: string): string {
  if (!LOCAL) return ruta;
  const prefijo = Object.keys(PUERTOS).find((p) => ruta.startsWith(p));
  return prefijo ? `http://localhost:${PUERTOS[prefijo]}${ruta}` : ruta;
}

async function registrarPorApi(request: APIRequestContext, body: Record<string, unknown>) {
  return request.post(api('/api/auth/register'), { data: body });
}

async function loginUi(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('input[name="loginEmail"]').fill(email);
  await page.locator('input[name="loginPassword"]').fill(password);
  await page.locator('button[type="submit"]', { hasText: 'Iniciar sesión' }).click();
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15000 });
}

// Sin modo "serial": se espera que algunas pruebas SEC fallen (son hallazgos) y eso
// no debe impedir que el resto se ejecute. Cada prueba prepara su propio estado.

// Precondición común: un CLIENTE de prueba recién creado (lo usan CAR, ROL, REG y SEC-01).
test.beforeAll(async ({ request }) => {
  const res = await registrarPorApi(request, {
    nombre: cliente.nombre, email: cliente.email, password: cliente.password,
  });
  expect(res.status(), 'Precondición: registrar cliente de prueba').toBe(201);
});

test.describe('EV2 - Caja negra: carrito, roles y registro', () => {

  test('TC-14 (CAR-01): Agregar producto disponible al carrito', async ({ page }) => {
    await loginUi(page, cliente.email, cliente.password);

    await page.locator('.search input').fill('leche');
    const primerResultado = page.locator('.search-dropdown-results .search-result-item').first();
    await expect(primerResultado).toBeVisible({ timeout: 8000 });
    const nombreProducto = (await primerResultado.textContent())?.trim() ?? '';
    await primerResultado.click();

    await expect(page).toHaveURL(/\/carrito/);
    const lineas = page.locator('.cart-line');
    await expect(lineas).toHaveCount(1);
    await expect(lineas.first()).toContainText(/leche/i);
    await expect(page.locator('.cart-btn .badge')).toHaveText('1');
    await expect(page.locator('.summary-card')).toBeVisible();
    test.info().annotations.push({ type: 'producto', description: nombreProducto });
  });

  test('TC-15 (CAR-02): Quitar producto mantiene el carrito consistente', async ({ page }) => {
    await loginUi(page, cliente.email, cliente.password);
    await page.goto('/carrito');

    const lineas = page.locator('.cart-line');
    if ((await lineas.count()) === 0) {
      // Si el carrito no persistió entre sesiones, se agrega de nuevo (se registra como observación).
      test.info().annotations.push({ type: 'observación', description: 'El carrito no persistió entre sesiones' });
      await page.locator('.search input').fill('leche');
      await page.locator('.search-dropdown-results .search-result-item').first().click();
      await expect(lineas).toHaveCount(1);
    }

    await lineas.first().locator('.cart-line__remove').click();
    await expect(lineas).toHaveCount(0);
    await expect(page.locator('.empty-cart')).toContainText(/carrito vacío/i);
    await expect(page.locator('.cart-btn .badge')).toHaveCount(0);
    await expect(page.locator('.summary-card')).toHaveCount(0);
  });

  test('TC-16 (ROL-03): Cliente autenticado no accede a vistas de ADMIN', async ({ page }) => {
    await loginUi(page, cliente.email, cliente.password);
    for (const ruta of ['/analitica', '/personal', '/vendedor']) {
      await page.goto(ruta);
      await page.waitForLoadState('networkidle');
      await expect(page, `Un CLIENTE no debe permanecer en ${ruta}`).not.toHaveURL(new RegExp(`${ruta}$`));
    }
  });

  test('TC-17 (REG-02): Registro con correo ya existente es rechazado', async ({ page, request }) => {
    // Nivel API: el servicio debe responder 409 sin crear un duplicado.
    const res = await registrarPorApi(request, {
      nombre: 'Duplicado', email: cliente.email, password: cliente.password,
    });
    expect(res.status()).toBe(409);
    expect((await res.json()).message).toMatch(/registrado/i);

    // Nivel UI: el usuario ve un mensaje de validación.
    await page.goto('/login');
    await page.locator('.login-card__switch button', { hasText: 'Regístrate' }).click();
    await page.locator('input[name="registerNombre"]').fill('Duplicado');
    await page.locator('input[name="registerEmail"]').fill(cliente.email);
    await page.locator('input[name="registerPassword"]').fill(cliente.password);
    await page.locator('button[type="submit"]', { hasText: 'Crear cuenta' }).click();
    await expect(page.locator('.form-error')).toBeVisible({ timeout: 10000 });
  });
});

test.describe('EV2 - Seguridad (verificación dinámica de hallazgos del análisis estático)', () => {

  test('SEC-01: El login no revela si un correo existe (enumeración de usuarios)', async ({ request }) => {
    const inexistente = await request.post(api('/api/auth/login'), {
      data: { email: `no.existe.${stamp}@pedidos360.test`, password: 'ClaveErronea-123' },
    });
    const claveMala = await request.post(api('/api/auth/login'), {
      data: { email: cliente.email, password: 'ClaveErronea-123' },
    });
    const msgInexistente = (await inexistente.json()).message;
    const msgClaveMala = (await claveMala.json()).message;
    test.info().annotations.push(
      { type: 'correo inexistente', description: `${inexistente.status()} - ${msgInexistente}` },
      { type: 'clave incorrecta', description: `${claveMala.status()} - ${msgClaveMala}` },
    );
    expect(inexistente.status(), 'Mismo código HTTP en ambos casos').toBe(claveMala.status());
    expect(msgInexistente, 'Mismo mensaje en ambos casos').toBe(msgClaveMala);
  });

  test('SEC-02: El registro público no permite autoasignarse el rol ADMIN', async ({ request }) => {
    const email = `qa.ev2.escalada.${stamp}@pedidos360.test`;
    const res = await registrarPorApi(request, {
      nombre: 'QA Escalada (BORRAR)', email, password: cliente.password, rol: 'ADMIN',
    });
    const body = await res.json();
    test.info().annotations.push({ type: 'respuesta', description: `${res.status()} - rol=${body?.data?.rol}` });
    // Seguro = se rechaza (4xx) o se ignora el campo y queda como CLIENTE.
    if (res.ok()) {
      expect(body.data.rol, `Cuenta ${email} creada con rol elevado: eliminarla de la BD`).toBe('CLIENTE');
    } else {
      expect(res.status()).toBeGreaterThanOrEqual(400);
    }
  });

  for (const ruta of ['/api/analitica', '/api/notificacion', '/api/carrito/usuario/1']) {
    test(`SEC-03: ${ruta} exige autenticación`, async ({ request }) => {
      const res = await request.get(api(ruta));
      test.info().annotations.push({ type: 'respuesta anónima', description: String(res.status()) });
      expect([401, 403], `${ruta} respondió ${res.status()} sin token`).toContain(res.status());
    });
  }

  test('SEC-04: Modificar productos exige rol autorizado (sin modificar datos)', async ({ request }) => {
    // Caddy publica /api/productos/* (con algo después de la barra), por eso se usa PUT a un ID
    // inexistente con cuerpo vacío: no modifica nada. Si responde 400 (validación) en vez de
    // 401/403, la petición anónima llegó hasta la lógica de negocio sin control de acceso.
    const res = await request.put(api('/api/productos/999999'), { data: {} });
    test.info().annotations.push({ type: 'respuesta anónima', description: `${res.status()} - ${(await res.text()).slice(0, 200)}` });
    expect([401, 403], `PUT /api/productos/999999 respondió ${res.status()} sin token`).toContain(res.status());
  });

  test('SEC-05: El sitio envía cabeceras de seguridad HTTP', async ({ request }) => {
    test.skip(LOCAL, 'Solo aplica al sitio desplegado (Caddy/nginx de producción)');
    const res = await request.get('/');
    const h = res.headers();
    const faltantes = [
      'content-security-policy',
      'strict-transport-security',
      'x-content-type-options',
      'x-frame-options',
      'referrer-policy',
    ].filter((k) => !(k in h));
    test.info().annotations.push({ type: 'cabeceras faltantes', description: faltantes.join(', ') || 'ninguna' });
    expect(faltantes).toEqual([]);
  });
});
