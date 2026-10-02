# Pedidos360 — Sistema de Gestión de Pedidos & Repartidor con Chat en Tiempo Real

Plataforma de e-commerce y logística en tiempo real para gestión de pedidos, productos, carrito, analítica, **portal de repartidor** y **chat WebSocket en tiempo real**, con autenticación mediante **Microsoft Entra ID (Azure AD)** y arquitectura **BFF (Backend for Frontend)** sobre microservicios Spring Boot, RabbitMQ y PostgreSQL.

---

## Arquitectura del Sistema

```
Angular (localhost:4200)
  │  Authorization: Bearer <JWT Token>
  ├──▶ HTTP / REST ──▶ BFF (localhost:8090) ─── Proxy validación JWT
  │                      │
  │                      ├─▶ usuario-service      (localhost:8081) — DB: usuario_db
  │                      ├─▶ pedidos-service      (localhost:8082) — DB: pedidos_db
  │                      ├─▶ carrito-service      (localhost:8083) — DB: carrito_db
  │                      ├─▶ analitica-service    (localhost:8084) — DB: analitica_db
  │                      ├─▶ productos-service    (localhost:8085) — DB: productos_db
  │                      ├─▶ notificacion-service (localhost:8086) — DB: notificacion_db
  │                      └─▶ chat-service         (localhost:8091) — DB: chat_db
  │
  └──▶ WebSocket (STOMP) ─────────────────────────▶ chat-service (ws://localhost:8091/ws-chat)
```

**Arquitectura Orientada a Eventos:**
- **RabbitMQ (Topic Exchange `pedidos360.events`):**
  - `pedido.creado`: Vacía carrito automáticamente y genera notificaciones.
  - `pedido.estado.actualizado`: Actualiza analítica y cierra automáticamente las conversaciones de chat asociadas cuando el estado cambia a `ENTREGADO`.
  - `pedido.repartidor.asignado`: Notifica al cliente y habilita la interacción directa de reparto.
  - `chat.creado`, `chat.mensaje.enviado`, `chat.cerrado`: Eventos de auditoría y notificaciones offline.
- **Dead Letter Exchange (DLX / DLQ):**
  - Exchange: `chat.dlx` | Queue: `chat.dlq` (Manejo de reintentos fallidos sin ciclos infinitos).

---

## Roles del Sistema

| Rol | Alcance / Permisos | Acceso a Vistas |
| :--- | :--- | :--- |
| **ADMIN** | Control total del sistema, gestión de personal, analítica financiera y administración global. | `/analitica`, `/personal`, `/vendedor`, `/repartidor`, `/cliente` |
| **VENDEDOR** | Gestión de inventario, publicación de productos y actualización de pedidos entrantes. | `/vendedor` |
| **REPARTIDOR** | Visualización de pedidos disponibles, aceptación de asignaciones, actualización a entregado y chat directo con el cliente. | `/repartidor` |
| **CLIENTE** | Navegación de catálogo, carrito de compras, seguimiento en vivo de pedidos activos y chat con su repartidor. | `/`, `/carrito`, `/cliente` |

### Credenciales de prueba (login local)
| Usuario | Contraseña | Rol |
|---|---|---|
| `admin@pedidos360.cl` | `Password123!` | ADMIN |
| `vendedor@pedidos360.cl` | `Password123!` | VENDEDOR |
| `rodrigo.morales@pedidos360.cl` | `Password123!` | REPARTIDOR |
| `cliente@pedidos360.cl` | `Password123!` | CLIENTE |

---

## Migraciones de Base de Datos (Flyway)

Todas las modificaciones de la base de datos están estrictamente versionadas mediante scripts Flyway SQL en cada microservicio:

- **`usuario-service` (`usuario_db`):**
  - `V1__init_schema.sql`: Creación de la tabla `usuarios`.
  - `V2__agregar_rol_repartidor.sql`: Incorporación y actualización del rol `REPARTIDOR` para Rodrigo Morales.
- **`pedidos-service` (`pedidos_db`):**
  - `V1__init_schema.sql`: Creación de la tabla `pedidos`.
  - `V2__agregar_columna_repartidor.sql`: Inserción de la columna `repartidor_id` e índice `idx_pedidos_repartidor_id`.
- **`chat-service` (`chat_db`):**
  - `V1__init_chat_schema.sql`: Creación de `chat_conversacion` y `chat_mensaje` con restricciones e índices.

---

## Política de Retención y Borrado de Chats

1. **Cierre Automático:** Al entregar un pedido (`pedido.estado.actualizado` con `nuevoEstado=ENTREGADO`), la conversación se marca como `CERRADO`. No se permiten nuevos mensajes.
2. **Borrado Lógico por Participante:** Cliente o Repartidor pueden ocultar el chat de su vista (`deleted_for_client_at`, `deleted_for_courier_at`).
3. **Retención Programada (`RetentionService`):** Tarea programada diaria (`@Scheduled`) que purga automáticamente de la BD las conversaciones cerradas con más de `CHAT_RETENTION_DAYS` (configurable, por defecto 30 días).

---

## Puertos y Servicios

| Servicio | Puerto | Descripción |
| :--- | :--- | :--- |
| Angular Frontend | `4200` | SPA Angular 19 Responsive (Desktop/Notebook/Tablet/Móvil) |
| BFF (Gateway) | `8090` | OAuth2 Resource Server & Proxy hacia microservicios |
| usuario-service | `8081` | Autenticación local, gestión de usuarios y JWT |
| pedidos-service | `8082` | Gestión de pedidos, asignación de repartidor y eventos |
| carrito-service | `8083` | Carrito de compras |
| analitica-service | `8084` | Analítica y reportes financieros |
| productos-service | `8085` | Catálogo y stock de productos |
| notificacion-service | `8086` | Notificaciones por usuario |
| **chat-service** | `8091` | **Conversaciones, mensajes REST, STOMP WebSocket y retención** |
| PostgreSQL | `5432` | Base de datos relacional (7 bases de datos independientes) |
| RabbitMQ | `5672 / 15672` | Broker de eventos y panel de administración |
| pgAdmin | `5050` | Administración de PostgreSQL |

---

## Inicio Rápido Local (Docker)

```bash
# 1. Clonar el repositorio y posicionarse en Backend
cd Backend

# 2. Iniciar todos los contenedores locales (Base de Datos, RabbitMQ y 7 Microservicios)
docker compose up --build -d

# 3. Iniciar el Frontend Angular
cd ../pedidos360-frontend
npm install
npm start
# Abrir en el navegador: http://localhost:4200
```
