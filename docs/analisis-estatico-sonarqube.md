# Análisis estático con SonarQube – Pedidos 360

> Sección para incorporar al informe (EP3) y apoyar la defensa (EP4).
> Cubre IL 2.3 / IE6: aplicar análisis estático y comparar sus resultados con criterios legales y normativos.
> Ejecución inicial: 28-10-2026 · Re-análisis y correcciones: 17-11-2026 (ver sección 3.1).
> SonarQube Community Build 26.10 (local, Docker), perfil de reglas "Sonar way".

---

## 1. Herramienta y justificación

SonarQube es una plataforma de **análisis estático de código (SAST)**: revisa el código fuente sin ejecutarlo y detecta defectos de confiabilidad, vulnerabilidades de seguridad, problemas de mantenibilidad, duplicación y falta de cobertura de pruebas. Se eligió porque:

- Analiza **todo el stack de Pedidos 360 en un solo proyecto**: Java/Spring Boot (8 microservicios), TypeScript/Angular, HTML/CSS, Dockerfiles y Terraform (infraestructura como código).
- Clasifica cada hallazgo con **estándares reconocidos** (CWE, OWASP Top 10, CERT, WCAG), lo que permite relacionarlos con la normativa.
- Reutiliza la cobertura de **JaCoCo** que ya generan las pruebas JUnit 5 + Mockito del plan de pruebas, integrando caja blanca y análisis estático.
- Es gratuita en su edición Community y se puede integrar al pipeline de CI existente (GitHub Actions).
- Su modelo de calidad (Confiabilidad, Seguridad, Mantenibilidad) se corresponde con características de **ISO/IEC 25010**.

Complementa al resto de herramientas del plan: JUnit/JaCoCo (caja blanca), Playwright (caja negra), JMeter (carga) y Lighthouse (rendimiento). SonarQube aporta la mirada **preventiva**: encuentra problemas en el código antes de que se manifiesten en ejecución.

## 2. Procedimiento de ejecución

1. Levantar SonarQube en Docker (`sonar/docker-compose.yml`) → `http://localhost:9000`.
2. Compilar y ejecutar las pruebas de los 8 microservicios con Maven; JaCoCo genera `target/site/jacoco/jacoco.xml` por servicio.
3. Ejecutar SonarScanner (imagen Docker oficial) con la configuración de `sonar-project.properties`.
4. Revisar el dashboard, clasificar los hallazgos y comparar con la normativa.

Todo el proceso está automatizado en `scripts/sonar-analysis.ps1`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/sonar-analysis.ps1
```

Alcance: `Backend/` (código Java y Dockerfiles), `pedidos360-frontend/src` y `terraform/`. Se excluyen `node_modules`, `target` y archivos generados. Para la cobertura no se consideran DTO, entidades, eventos ni clases de arranque (no contienen lógica de negocio).

## 3. Resumen de resultados

| Métrica | Resultado | Lectura |
|---|---|---|
| Líneas de código analizadas | 20.079 (Java 8.754 · TS 3.285 · HTML 2.601 · CSS 3.706 · Terraform 511 · Docker 111) | Monorepo completo |
| Pruebas unitarias ejecutadas | 197 (0 fallidas) | Base de la cobertura |
| Quality Gate | **Passed** (ver nota) | — |
| Seguridad | **16 issues · Rating E** | Hay 3 issues Blocker en Terraform y 6 High en Java |
| Confiabilidad | 10 issues · Rating C | NPE potenciales, promesas sin manejo |
| Mantenibilidad | 4 issues · Rating A (deuda técnica 27 min) | Bajo |
| Cobertura (backend) | **53,9 %** (982 de 2.561 líneas sin cubrir) | Bajo la meta ≥ 80 % del plan |
| Duplicación | **14,1 %** | Alta: código repetido entre microservicios |
| Security Hotspots pendientes | 0 | En esta versión se reportan como issues |

> **Nota sobre el Quality Gate:** en el primer análisis SonarQube evalúa sólo el *código nuevo* (no hay período previo), por lo que el gate aparece "Passed" aunque el código existente tiene un rating de seguridad E. **No debe interpretarse como que el proyecto está libre de problemas.** En los próximos análisis, el gate "Sonar way" bloqueará código nuevo con issues, cobertura < 80 % o duplicación > 3 %.

### 3.1 Seguimiento 17-11-2026: antes y después de las correcciones

Entre octubre y noviembre el código creció (nuevas funcionalidades, Kafka, chat), por lo que se volvió a analizar el proyecto completo y luego se aplicaron correcciones.

| Métrica | 28-10-2026 | 17-11 (antes de corregir) | 17-11 (después de corregir) |
|---|---|---|---|
| Líneas de código | 20.079 | 30.649 | 30.775 |
| Pruebas unitarias (0 fallidas) | 197 | 312 | 312 |
| Issues de seguridad | 16 | 11 | **5** |
| Issues de confiabilidad | 10 | 36 | 36 |
| Issues de mantenibilidad | 4 | 8 | 8 |
| Cobertura global | 53,9 % (backend) | 44,6 % | 44,6 % |
| Duplicación | 14,1 % | 13,1 % | 13,0 % |
| Quality Gate | Passed (primer análisis) | **Failed** | **Failed** |

**Correcciones aplicadas:**

1. **SQ-04 resuelto (6 issues `java:S4684`)**: los endpoints `POST /api/carrito`, `PUT /api/carrito/{id}`, `POST /api/pedidos`, `POST /api/pedidos/cupones`, `POST /api/notificacion` y `POST /api/analitica` ahora reciben **DTO de entrada** (`record`) en vez de entidades JPA. Los DTO sólo aceptan los campos que el cliente debe controlar; `id`, `estado`, datos del repartidor, fechas, `usosActuales` e `idempotencyKey` los asigna el servidor.
   *Verificación:* se envió a `POST /api/pedidos` un cuerpo con `id=1`, `estado=ENTREGADO`, `repartidorId=7`, `usuarioId=999` y fecha de 2020. El servidor respondió 200 creando un pedido nuevo con `estado=PENDIENTE`, sin repartidor, con fecha actual y con el `usuarioId` del token. Los campos ajenos se ignoran, por lo que el frontend sigue funcionando sin cambios.
2. **SQ-05 revisado (2 issues `java:S4502`)**: se corrigió la justificación anterior (ver tabla 4.1) y se documentó en el código. Queda como *riesgo aceptado* y se debe marcar como "Accepted" en SonarQube.
3. **Flyway** (no detectado por SonarQube): en Spring Boot 4, `flyway-core` ya no ejecuta migraciones por sí solo. Se reemplazó por `spring-boot-starter-flyway` en 6 servicios; antes de esto las tablas las creaba Hibernate y los datos iniciales nunca se cargaban.

**Por qué el Quality Gate sigue en "Failed":** el gate "Sonar way" evalúa el *código nuevo* desde el análisis de referencia (octubre). Ese código nuevo tiene 37,3 % de cobertura (meta 80 %), 9,9 % de duplicación (máximo 3 %) y 20 issues (máximo 0). Es una brecha real que se debe cerrar con pruebas y refactorización, no con cambios en la configuración del gate.

### Cobertura por microservicio

| Servicio | Cobertura | Duplicación | Comentario |
|---|---|---|---|
| carrito | 75,0 % | 25,0 % | Mejor cubierto |
| notificacion | 70,8 % | 42,3 % | |
| analitica | 69,9 % | 43,5 % | Mayor duplicación |
| usuario | 65,5 % | 28,1 % | Servicio más grande (2.940 LOC) |
| productos | 50,9 % | 31,2 % | |
| pedidos | 50,7 % | 31,1 % | Flujo crítico con baja cobertura |
| bff | 45,4 % | 0,0 % | Puerta de entrada de seguridad |
| chat | 34,8 % | 17,5 % | Menor cobertura (JaCoCo agregado en esta etapa) |

Ningún servicio alcanza el 80 % definido en los criterios de aceptación (sección 3.3 del plan). La brecha queda documentada como incidente de calidad.

## 4. Hallazgos clasificados

Severidad según SonarQube (Blocker > High > Medium > Low). "Estado" es el resultado de la revisión manual del equipo.

### 4.1 Seguridad

| ID | Regla | Severidad | Ubicación | Descripción | Estándar | Estado |
|---|---|---|---|---|---|---|
| SQ-01 | terraform:S6333 | Blocker | `terraform/modules/api_gateway/main.tf:32` | API Gateway creada como pública sin autorizador a nivel de infraestructura | CWE-284 · OWASP A01 | Confirmado – mitigado parcialmente (el BFF valida JWT), pero falta control en el borde |
| SQ-02 | terraform:S6329 | Blocker | `terraform/modules/ec2/main.tf:16` | La instancia EC2 recibe IP pública: expuesta a Internet | CWE-668 · OWASP A05 | Confirmado – necesario para el despliegue académico; en producción debe ir detrás de un balanceador/subred privada |
| SQ-03 | terraform:S6258 | Medium | `terraform/modules/api_gateway/main.tf:38` | API Gateway sin `access_log_settings`: registros incompletos | CWE-778 · OWASP A09 | Confirmado – afecta la trazabilidad de incidentes |
| SQ-04 | java:S4684 | High | `PedidoController.java:91`, `CarritoController.java:26`, `NotificacionController.java:31`, `AnaliticaController.java:31` | Los endpoints reciben **entidades JPA** directamente en `@RequestBody` (ej. `crear(@RequestBody Pedido pedido)`): permite *mass assignment* (el cliente puede enviar campos que no debería controlar, como estado, ids o totales) | CWE-915 · OWASP API3:2023 | **Corregido 17-11-2026** con DTO de entrada (ver 3.1) |
| SQ-05 | java:S4502 | High | `bff/.../SecurityConfig.java:68`, `usuario/.../SecurityConfig.java:39` | CSRF deshabilitado en Spring Security | CWE-352 · OWASP A01 | **Revisado – riesgo aceptado.** *Corrección 17-11-2026: la justificación original era imprecisa.* El **BFF** sí es sólo `Bearer` (Entra ID), sin cookies, por lo que CSRF no aplica. El login local **sí usa cookies** (`p360_session` HttpOnly, `SameSite=Lax`), pero cada servicio (incluido `usuario` en `JwtAuthenticationFilter`) implementa su propia protección *double-submit*: las peticiones mutantes autenticadas por cookie deben enviar `X-XSRF-TOKEN` igual a la cookie `XSRF-TOKEN`. Por eso se desactiva el CSRF de Spring. Se recomienda verificarlo en el pentesting |
| SQ-06 | docker:S6471 | Low (x7) | `Dockerfile` de usuario, productos, carrito, pedidos, notificacion, analitica, chat | Los contenedores se ejecutan como **root** | CWE-250 · OWASP A05 | Confirmado – agregar usuario no privilegiado (`USER`) |

### 4.2 Confiabilidad

| ID | Regla | Severidad | Ubicación | Descripción | Estándar |
|---|---|---|---|---|---|
| SQ-07 | java:S2259 | Medium (x3) | `chat/.../WebSocketConfig.java:165`, `chat/.../ChatController.java:181`, `pedidos/.../PedidoController.java:279` | `getBody()` puede ser `null` → posible `NullPointerException` (error 500) al consumir otro servicio | CWE-476 · CERT |
| SQ-08 | java:S2142 | Medium | `chat/.../JwtUtil.java:203` | `InterruptedException` capturada sin re-interrumpir el hilo | Manejo de errores / concurrencia |
| SQ-09 | java:S6863 | Medium | `pedidos/.../PedidoController.java:71` | Código HTTP de respuesta no refleja la operación | Buena práctica REST |
| SQ-10 | typescript:S9383 | Medium | `chat.service.ts:70` | Promesa sin manejo de rechazo: un fallo de conexión del chat quedaría silencioso | Manejo de errores |
| SQ-11 | Web:InputWithoutLabelCheck | Medium (x3) | `header.html:46`, `cart.html:117`, `chat.component.html:65` | Campos de entrada sin `<label>` asociado | **WCAG 2.1 nivel A** (accesibilidad) |
| SQ-12 | typescript:S7773 | Medium | `analytics.ts:255` | Usar `Number.parseInt` | Convención |

### 4.3 Mantenibilidad

| ID | Regla | Severidad | Ubicación | Descripción |
|---|---|---|---|---|
| SQ-13 | typescript:S2699 | Blocker (x2) | `e2e/auth-blackbox.spec.ts:94`, `e2e/full-app-blackbox.spec.ts:74` | Casos de prueba Playwright **sin aserciones**: pasan siempre y no verifican nada (falso positivo de calidad en la evidencia de caja negra) |
| SQ-14 | typescript:S7651 | Blocker | `map-modal.ts:23` | Output de Angular con nombre de evento DOM estándar (puede disparar el handler dos veces) |
| SQ-15 | Duplicación | — | Backend | 14,1 % de líneas duplicadas: `ApiResponse`, `CorsConfig`, `GlobalExceptionHandler` y eventos se copian en cada microservicio. Una corrección de seguridad debe repetirse en 8 lugares |

## 5. Comparación con criterios legales y normativos

| Marco | Requisito relevante | Hallazgos relacionados | Análisis |
|---|---|---|---|
| **Ley N.º 21.663** – Marco de Ciberseguridad | Medidas para prevenir, contener y responder a incidentes; gestión de riesgos; capacidad de detectar y reportar incidentes | SQ-01, SQ-02, SQ-03, SQ-06 | La exposición pública de EC2/API Gateway y los contenedores como root amplían la superficie de ataque. La falta de logs de acceso (SQ-03) dificulta detectar y reportar un incidente dentro de los plazos que la ley exige a los sujetos obligados. Aunque Pedidos 360 no sea necesariamente sujeto obligado, estos hallazgos son brechas frente a las buenas prácticas que la ley promueve |
| **Ley N.º 21.719** – Protección de Datos Personales (vigencia 01-12-2026) | Principios de **seguridad**, **finalidad**, **proporcionalidad/minimización**; deber de adoptar medidas técnicas y organizativas; notificación de vulneraciones | SQ-04, SQ-01, SQ-03, SQ-07 | Exponer entidades JPA (SQ-04) puede devolver o permitir modificar más datos personales de los necesarios (correo, dirección, datos de pedidos), contrario a la minimización. Un *mass assignment* afectaría la **integridad** de datos personales. Sin logs (SQ-03) no se puede reconstruir una vulneración para notificarla a la Agencia |
| **ISO/IEC 25010:2023** | Seguridad (confidencialidad, integridad, responsabilidad), Confiabilidad (madurez, tolerancia a fallos), Mantenibilidad (modularidad, capacidad de prueba), Usabilidad (accesibilidad) | Seguridad: SQ-01 a SQ-06 · Confiabilidad: SQ-07 a SQ-10 · Mantenibilidad: SQ-13 a SQ-15, cobertura 53,9 % · Usabilidad: SQ-11 | El rating de Seguridad E y Confiabilidad C muestran que el producto aún no alcanza el nivel esperado. La duplicación y la cobertura bajo 80 % afectan la *capacidad de prueba* y *modificabilidad* |
| **OWASP Top 10 (2021) / API Security Top 10 (2023)** | A01 Control de acceso, A05 Configuración insegura, A09 Fallas de registro y monitoreo, API3 Autorización a nivel de propiedad | SQ-01/02/05 (A01), SQ-02/06 (A05), SQ-03 (A09), SQ-04 (API3) | Los hallazgos se concentran en configuración e infraestructura más que en inyección o criptografía, lo que es coherente con una arquitectura de microservicios con autenticación JWT ya implementada |
| **WCAG 2.1** (accesibilidad) | 1.3.1 / 4.1.2: los controles de formulario deben tener nombre accesible | SQ-11 | Afecta a usuarios de lectores de pantalla; relacionado con el criterio de usabilidad del plan |

### Análisis ético

- Los hallazgos de seguridad no son sólo técnicos: un *mass assignment* o una exposición innecesaria afectan directamente a **personas** (clientes, repartidores) cuyos datos maneja la plataforma.
- El equipo **no oculta** el rating E ni la cobertura bajo la meta, y documenta como "riesgo aceptado" sólo lo que puede justificar técnicamente (CSRF), aplicando el principio de transparencia.
- Que un Quality Gate aparezca "Passed" en el primer análisis no debe usarse como evidencia de calidad: hacerlo sería informar de forma engañosa.

## 6. Recomendaciones (priorizadas)

| Prioridad | Recomendación | Resuelve | Fundamento |
|---|---|---|---|
| 1 | ~~Reemplazar entidades JPA por DTO de entrada~~ **Hecho 17-11-2026.** Pendiente: DTO de **salida** y validación (`@Valid`), y **recalcular precios y totales en el servidor**: hoy el servidor sigue confiando en `precioUnitario` y `total` enviados por el cliente | SQ-04 | Integridad y minimización de datos (Ley 21.719, OWASP API3) |
| 2 | Habilitar **access logs** en API Gateway y centralizar logs | SQ-03 | Detección y reporte de incidentes (Ley 21.663, OWASP A09) |
| 3 | Agregar autorizador (JWT/Cognito) en API Gateway y ubicar EC2 en subred privada detrás de un balanceador | SQ-01, SQ-02 | Defensa en profundidad (Ley 21.663, OWASP A01/A05) |
| 4 | Ejecutar contenedores con usuario no root (`RUN adduser ...` + `USER app`) | SQ-06 | Mínimo privilegio (CWE-250) |
| 5 | Validar `null` en respuestas entre servicios y manejar promesas/interrupciones | SQ-07, SQ-08, SQ-10 | Confiabilidad (ISO 25010) |
| 6 | Agregar aserciones a las pruebas E2E sin `expect` | SQ-13 | Validez de la evidencia del plan de pruebas |
| 7 | Subir cobertura a ≥ 80 % priorizando **pedidos, bff y chat** | Cobertura | Criterio de aceptación del plan |
| 8 | Extraer clases comunes a una librería compartida | SQ-15 | Mantenibilidad; corrección de seguridad en un solo lugar |
| 9 | Asociar `<label>` a los inputs | SQ-11 | WCAG 2.1 A |
| 10 | Incorporar SonarQube al pipeline de CI (GitHub Actions) con Quality Gate obligatorio | Todos | Mejora continua |

## 7. Evidencias para el anexo

Capturas sugeridas desde `http://localhost:9000/dashboard?id=pedidos360`:

1. **Overview** del proyecto (ratings, cobertura, duplicación, LOC).
2. **Issues** filtrado por *Software Quality = Security* (muestra los 16).
3. Detalle de **SQ-04** (`PedidoController.java`) con la explicación de la regla.
4. Detalle de **SQ-01/SQ-02** (Terraform).
5. Pestaña **Measures → Coverage** con el desglose por microservicio.
6. Pestaña **Measures → Duplications**.
7. Terminal con `EXECUTION SUCCESS` del scanner.
