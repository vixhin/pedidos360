# Pedidos360 — Sistema de Gestión de Pedidos & Repartidor con Chat en Tiempo Real

Plataforma e-commerce y logística en tiempo real para gestión de pedidos, productos, carrito, analítica, **portal de repartidor** y **chat WebSocket en tiempo real**, con autenticación híbrida (**Microsoft Entra ID / Azure AD** y **JWT local DB**), arquitectura **BFF (Backend for Frontend)** sobre microservicios Spring Boot, RabbitMQ, PostgreSQL y despliegue automatizado con **Terraform IaC** y **Docker Compose** en **AWS EC2**.

---

##  Arquitectura General del Sistema

```
                               Terraform (IaC)
                                      │
           ┌──────────────────────────┼──────────────────────────┐
           │                          │                          │
           v                          v                          v
    AWS Networking & SG         API Gateway & EIP          EC2 Instance
  (VPC, Subnets, SG 80/443/22)  (ANY /api/bff/*)        (Docker Engine)
  
                                  INTERNET
                                     │
                        ┌────────────┴────────────┐
                        │                         │
                  Microsoft REST               WebSocket /
                   (Entra ID)                  login local
                        │                         │
                        v                         │
                 AWS API Gateway                  │
               (ANY /api/bff/*)                   │
                        │                         │
                        v                         v
                 Elastic IP / Caddy <─────────────┘
              (https://*.sslip.io)
                        │
               ┌────────┴─────────┐
               │                  │
          Angular/nginx          BFF :8090
                                  │
         ┌────────────────┬───────┼────────┬───────────────┬───────────────┐
         │                │       │        │               │               │
       usuario         pedidos  carrito productos      analitica    notificacion
       :8081            :8082    :8083    :8085          :8084          :8086
                          │
                        chat :8091 (/ws-chat)
                          │
         ┌────────────────┴───────────────┐
         │                                │
    PostgreSQL 16                      RabbitMQ
  (7 DBs Flyway)                    (Events & DLQ)
```

---

##  Autenticación Híbrida & Enrutamiento de Tráfico

Pedidos360 implementa un esquema de autenticación doble sin degradar la seguridad:

1. **Microsoft Entra ID (Azure AD):**
   - **Flujo:** Frontend Angular (MSAL) ──▶ AWS API Gateway (`/api/bff/*`) ──▶ BFF (`:8090`) ──▶ Microservicios Internos.
   - El BFF actúa como OAuth2 Resource Server validando el JWT emitido por Entra ID antes de delegar a los microservicios.
2. **Login Local (JWT DB Propio):**
   - **Flujo:** Frontend Angular ──▶ Caddy (HTTPS) ──▶ `usuario-service` (`:8081`) / Microservicios Directos.
   - El `authInterceptor` de Angular adjunta `Authorization: Bearer <token_local>` únicamente a las llamadas directas cuando la sesión proviene del proveedor local DB.

---

##  Funcionamiento Interno del Código por Componente

### 1. Frontend Angular (`pedidos360-frontend`)
- **`authInterceptor` (`auth.interceptor.ts`):** Intercepta peticiones HTTP REST salientes. Verifica si el proveedor de sesión activo es `db` (login local). Si es así, adjunta el encabezado `Authorization: Bearer <token_local>` en las peticiones directas a los microservicios, evitando interferir con las llamadas gestionadas por `MsalInterceptor`.
- **`ChatComponent` & `StompService`:** Gestionan la conexión WebSocket sobre STOMP al endpoint `/ws-chat`. Envían el token de autenticación en los frames `CONNECT` y `SUBSCRIBE`, permitiendo la suscripción dinámica a canales de conversación en tiempo real (`/topic/chat/{conversacionId}`).
- **`RepartidorPortalComponent`:** Tablero interactivo en tiempo real para repartidores. Permite visualizar pedidos disponibles, aceptar asignaciones, cambiar estados de entrega y comunicarse por chat directo con el cliente.

### 2. BFF Gateway (`bff-service`)
- **Gateway & Resource Server OAuth2:** Expone rutas `/api/bff/**` y actúa como punto de entrada para Microsoft Entra ID. Valida la firma del JWT de Azure AD mediante `jwk-set-uri` antes de redirigir las solicitudes a los microservicios backend.
- **`HealthController`:** Ofrece verificaciones de estado `/health` y `/ready`. La prueba de readiness realiza consultas internas directas a los endpoints de salud de los microservicios sin requerir token JWT de usuario.

### 3. Microservicio de Usuarios (`usuario-service`)
- **`AuthController`:** Maneja el endpoint público POST `/api/usuarios/login` para autenticación con credenciales locales.
- **`JwtService`:** Genera y valida tokens JWT firmados con algoritmo HMAC-SHA256, incluyendo en los claims el ID de usuario, correo y rol (`CLIENTE`, `REPARTIDOR`, `VENDEDOR`, `ADMIN`).
- **Seguridad:** Encriptación de contraseñas con `BCryptPasswordEncoder`.

### 4. Microservicio de Pedidos (`pedidos-service`)
- **`PedidoController`:** Expone endpoints públicos REST `/api/pedidos/**` protegidos mediante `SecurityConfig` y resolución de identidad (`resolveIdentity()`). Aplica una matriz estricta de autorización por rol (`CLIENTE` solo interactúa con sus propios pedidos; `REPARTIDOR` administra sus asignaciones; `ADMIN` posee acceso global).
- **`InternalPedidoController` (`/api/internal/pedidos/{id}`):** Endpoint exclusivo para comunicación Servicio a Servicio (S2S). Protegido mediante `InternalServiceKeyInterceptor`, valida el encabezado `X-Internal-Service-Key` para responder a solicitudes internas (ej. desde `chat-service`) con un DTO sintético (`id`, `usuarioId`, `repartidorId`, `estado`, `total`) sin requerir un JWT de usuario.
- **`PedidoEventPublisher`:** Publica eventos de dominio a RabbitMQ (`pedido.creado`, `pedido.estado.actualizado`, `pedido.repartidor.asignado`).

### 5. Microservicio de Chat (`chat-service`)
- **`ChatWebSocketController`:** Atiende comandos STOMP en `/app/chat.sendMessage` y `/app/chat.join`.
- **`WebSocketSecurityInterceptor`:** Intercepta comandos de conexión y suscripción STOMP, extrayendo y validando el token JWT desde el encabezado `Authorization`.
- **`ChatService`:** Implementa la lógica de negocio de conversaciones. Verifica la participación de los usuarios (`clienteId` o `repartidorId`) y ejecuta peticiones S2S a `/api/internal/pedidos/{id}` en `pedidos-service` para verificar el estado del pedido.
- **`PedidoEventListener`:** Consume el evento `pedido.estado.actualizado` de RabbitMQ. Si el nuevo estado es `ENTREGADO`, marca automáticamente la conversación como `CERRADO` bloqueando nuevos mensajes.
- **`RetentionService`:** Ejecuta una tarea programada (`@Scheduled(cron = "0 0 3 * * ?")`) para purgar de la base de datos las conversaciones cerradas con antigüedad superior al límite configurado (`CHAT_RETENTION_DAYS`).

### 6. Microservicio de Carrito (`carrito-service`)
- **`CarritoController`:** Administra el carrito de compras asegurando que un usuario solo pueda consultar, modificar o vaciar sus propios ítems mediante la verificación del `usuarioId` autenticado.
- **`PedidoEventListener`:** Escucha el evento `pedido.creado` de RabbitMQ para vaciar automáticamente el carrito del usuario tras confirmar la compra.

### 7. Microservicio de Analítica (`analitica-service`)
- **`AnaliticaEventListener`:** Registra métricas comerciales y financieras consumiendo de manera asíncrona los eventos de RabbitMQ (`pedido.estado.actualizado`).
- **`AnaliticaController`:** Endpoints REST de consulta de reportes restringidos exclusivamente a usuarios con rol `ADMIN`.

### 8. Microservicio de Productos (`productos-service`)
- **`ProductoController`:** Permite lectura pública GET `/api/productos`. Operaciones de modificación (POST, PUT, DELETE) protegidas para roles `VENDEDOR` y `ADMIN`.

### 9. Microservicio de Notificaciones (`notificacion-service`)
- **`NotificacionEventListener`:** Escucha eventos de RabbitMQ (`pedido.creado`, `pedido.repartidor.asignado`) para generar alertas asíncronas destinadas a los usuarios involucrados.
- **`NotificacionController`:** Entrega notificaciones filtradas estrictamente por el `usuarioId` autenticado.

### 10. Infraestructura como Código (`terraform/`)
- **`modules/network`**: Crea VPC, subredes públicas/privadas, Internet Gateway y tablas de ruteo.
- **`modules/security-group`**: Define reglas de entrada (80/443 públicas, 22 SSH restringido) y salida. Bloquea puertos de bases de datos y microservicios al exterior.
- **`modules/ec2`**: Aprovisiona la instancia EC2 con script de arranque `user_data` que instala Docker y Docker Compose Plugin.
- **`modules/elastic-ip`**: Crea y asocia de forma estática la Elastic IP a la EC2.
- **`modules/api-gateway`**: Crea HTTP API Gateway v2 en AWS configurando la ruta `ANY /api/bff/{proxy+}` hacia `https://<PUBLIC_DOMAIN>/api/bff/{proxy}`.

---

## Infraestructura como Código (Terraform en AWS)

La infraestructura en AWS es aprovisionada mediante **Terraform (HCL)** organizada en módulos reutilizables y entornos independientes (`dev` y `prod`):

```
Terraform (IaC)
   │
   ├──▶ AWS Networking (VPC, Subnets, Route Tables, IGW)
   ├──▶ Security Group (Ingress: 80, 443, SSH 22 restringido)
   ├──▶ Elastic IP (Asociación estática con EC2)
   ├──▶ EC2 Instance (Ubuntu / Docker Engine & Compose Plugin)
   └──▶ API Gateway (v2 HTTP API → Proxy HTTP a Caddy/BFF)
          │
          v
      EC2 Host
          │
          └──▶ Docker Compose (Contenedores de Pedidos360)
```

- **`modules/network`**: Creación de VPC, Subnets, Internet Gateway y tablas de ruteo.
- **`modules/security-group`**: Enfoque de mínimo privilegio:
  - **Puertos Públicos Abiertos (0.0.0.0/0):** `80` (HTTP) y `443` (HTTPS).
  - **Puerto SSH:** `22` (restringido vía `allowed_ssh_cidr`).
  - **Puertos Bloqueados al Exterior:** PostgreSQL (`5432`), RabbitMQ (`5672/15672`), BFF (`8090`), Chat (`8091`) y Microservicios (`8081-8086`) son inaccesibles desde Internet y solo se comunican internamente en la red de Docker.
- **`modules/ec2`**: Instancia EC2 Linux configurada con Docker Engine y Docker Compose Plugin.
- **`modules/elastic-ip`**: Elastic IP estática asociada explícitamente (`aws_eip_association`) para garantizar la persistencia de la IP pública ante reinicios de la máquina.
- **`modules/api-gateway`**: HTTP API Gateway v2 integrado vía proxy directo HTTP hacia Caddy/BFF.
- **DNS Dinámico (sslip.io):** Generación automática del hostname (ej. `https://3-92-44-37.sslip.io`) a partir de la Elastic IP para SSL/TLS transparente gestionado por Caddy.

---

##  Microservicios & Contenedores (Docker Compose)

Todos los componentes de la aplicación corren aislados mediante Docker Compose en la instancia EC2:

| Servicio | Puerto Host | Descripción & Responsabilidad | Base de Datos |
| :--- | :--- | :--- | :--- |
| **Caddy** | `80, 443` | Reverse Proxy HTTPS, TLS automático (Let's Encrypt / sslip.io), WebSockets `/ws-chat`. | N/A |
| **Angular Frontend** | Nginx | SPA Angular 19 Responsive (Clientes, Repartidores, Vendedores, Admin). | N/A |
| **BFF (Gateway)** | `8090` | Gateway para Microsoft Entra ID, agregación de respuestas y validación de seguridad. | N/A |
| **usuario-service** | `8081` | Login DB local, emisión/validación JWT local, gestión de usuarios y roles. | `usuario_db` |
| **pedidos-service** | `8082` | Gestión de pedidos, matriz de autorización (CLIENTE, REPARTIDOR, ADMIN) y comunicación S2S. | `pedidos_db` |
| **carrito-service** | `8083` | Carrito de compras con validación estricta de propiedad de usuario. | `carrito_db` |
| **analitica-service**| `8084` | Métricas y reportes financieros activados por eventos de RabbitMQ. | `analitica_db` |
| **productos-service**| `8085` | Catálogo de productos, stock y precios. | `productos_db` |
| **notificacion-service**|`8086`| Notificaciones por usuario e historial de alertas. | `notificacion_db` |
| **chat-service** | `8091` | **STOMP WebSockets (`/ws-chat`), conversaciones, purga programada y eventos RabbitMQ.** | `chat_db` |
| **PostgreSQL 16** | `5432` | Base de datos relacional independiente para cada servicio (7 esquemas gestionados por **Flyway**). | Multi-DB |
| **RabbitMQ** | `5672 / 15672` | Broker de eventos asíncronos (`pedidos360.events`) y Dead Letter Queue (`chat.dlq`). | N/A |

---

## 📡 Comunicación Entre Microservicios (S2S) & Seguridad Interna

Para evitar brechas de seguridad donde peticiones internas requieran tokens de usuario navegando por Internet, el sistema implementa **Rutas Internas Protegidas (`/api/internal/**`)**:

- **Ejemplo (`chat-service` ──▶ `pedidos-service`):**
  - Al iniciar o enviar un mensaje en un chat, `chat-service` consulta `/api/internal/pedidos/{id}` enviando el encabezado `X-Internal-Service-Key`.
  - `pedidos-service` valida la clave de servicio interna sin exigir un JWT de usuario, respondiendo con los datos necesarios (`usuarioId`, `repartidorId`, `estado`) para garantizar que la conversación sea válida y cerrada si el pedido está `ENTREGADO`.

---

##  Arquitectura Orientada a Eventos (RabbitMQ)

- **Topic Exchange (`pedidos360.events`):**
  - `pedido.creado`: Limpia automáticamente el carrito del usuario y dispara notificaciones.
  - `pedido.estado.actualizado`: Actualiza analíticas y cierra conversaciones en `chat-service` al pasar a `ENTREGADO`.
  - `pedido.repartidor.asignado`: Notifica al cliente y habilita el canal de chat con el repartidor.
  - `chat.creado`, `chat.mensaje.enviado`: Auditoría y alertas.
- **Dead Letter Exchange & Queue (DLX / DLQ):**
  - Exchange: `chat.dlx` | Queue: `chat.dlq` (Resiliencia ante fallos de procesamiento de eventos).

---

## 👥 Roles del Sistema & Permisos

| Rol | Permisos Principales | Acceso a Vistas |
| :--- | :--- | :--- |
| **ADMIN** | Control total, analítica global, gestión de productos, pedidos y usuarios. | `/analitica`, `/personal`, `/vendedor`, `/repartidor`, `/cliente` |
| **VENDEDOR** | Gestión de productos y actualización de pedidos recibidos. | `/vendedor` |
| **REPARTIDOR** | Visualización de pedidos disponibles, toma de pedidos, cambio a entregado y chat con cliente. | `/repartidor` |
| **CLIENTE** | Catálogo, carrito, creación de pedidos, seguimiento y chat con repartidor asignado. | `/`, `/carrito`, `/cliente` |

### Credenciales de Prueba (Login Local DB)
| Usuario | Contraseña | Rol |
|---|---|---|
| `admin@pedidos360.cl` | `Password123!` | ADMIN |
| `vendedor@pedidos360.cl` | `Password123!` | VENDEDOR |
| `rodrigo.morales@pedidos360.cl` | `Password123!` | REPARTIDOR |
| `cliente@pedidos360.cl` | `Password123!` | CLIENTE |

---

##  Migraciones de Base de Datos (Flyway)

Todas las bases de datos utilizan `ddl-auto=validate` en producción y aplican versionamiento estricto con scripts SQL Flyway:

- **`usuario-service` (`usuario_db`):** `V1__init_schema.sql`, `V2__agregar_rol_repartidor.sql`
- **`pedidos-service` (`pedidos_db`):** `V1__init_schema.sql`, `V2__agregar_columna_repartidor.sql`
- **`chat-service` (`chat_db`):** `V1__init_chat_schema.sql`

---

##  Integración Continua (CI/CD - GitHub Actions)

- **Workflow CI (`.github/workflows/ci.yml`):**
  - Ejecuta `mvn test` en los 7 microservicios y el BFF.
  - Verifica la compilación de Angular (`npm run build`).
  - Formatea y valida los archivos de Terraform (`terraform fmt`, `terraform validate`).
  - Sintetiza la configuración de Docker (`docker compose config`).
- **Workflow CD (`.github/workflows/cd.yml`):**
  - Despliega la infraestructura en AWS Academy usando secretos seguros (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`).

---

##  Ejecución Local Rápida

```bash
# 1. Clonar e ingresar a Backend
git clone https://github.com/vixhin/pedidos360.git
cd pedidos360/Backend

# 2. Levantar la pila de contenedores local (7 microservicios + BFF + PostgreSQL + RabbitMQ)
docker compose up --build -d

# 3. Levantar el Frontend Angular
cd ../pedidos360-frontend
npm install
npm start

# Acceder en el navegador: http://localhost:4200
```
