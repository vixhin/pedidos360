# Pedidos360 — Sistema de Gestión de Pedidos & Repartidor con Chat en Tiempo Real

Plataforma e-commerce y logística en tiempo real para gestión de pedidos, productos, carrito, analítica, **portal de repartidor** y **chat WebSocket en tiempo real**, con autenticación híbrida (**Microsoft Entra ID / Azure AD** y **JWT local DB**), arquitectura **BFF (Backend for Frontend)** sobre microservicios Spring Boot, RabbitMQ, PostgreSQL y automatización IaC con **Terraform** y **Docker Compose** en **AWS EC2**.

---

## 🏗️ Arquitectura General del Sistema

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

## 🔐 Autenticación Híbrida & Enrutamiento de Tráfico

Pedidos360 implementa un esquema de autenticación doble sin degradar la seguridad:

1. **Microsoft Entra ID (Azure AD):**
   - **Flujo:** Frontend Angular (MSAL) ──▶ AWS API Gateway (`/api/bff/*`) ──▶ BFF (`:8090`) ──▶ Microservicios Internos.
   - El BFF actúa como OAuth2 Resource Server validando el JWT emitido por Entra ID antes de delegar a los microservicios.
2. **Login Local (JWT DB Propio):**
   - **Flujo:** Frontend Angular ──▶ Caddy (HTTPS) ──▶ `usuario-service` (`:8081`) / Microservicios Directos.
   - El `authInterceptor` de Angular adjunta `Authorization: Bearer <token_local>` únicamente a las llamadas directas cuando la sesión proviene del proveedor local DB.

---

## 💻 Funcionamiento Interno del Código por Componente

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
- **`InternalPedidoController` (`/api/internal/pedidos/{id}`):** Endpoint exclusivo para comunicación Servicio a Servicio (S2S). Protegido mediante `InternalServiceKeyInterceptor`, valida el encabezado `X-Internal-Service-Key` para responder a solicitudes internas (ej. desde `chat-service`) con un DTO sintético (`InternalPedidoResponseDTO`) sin requerir un JWT de usuario.
- **`PedidoEventPublisher`:** Publica eventos de dominio a RabbitMQ (`pedido.creado`, `pedido.estado.actualizado`, `pedido.repartidor.asignado`).

### 5. Microservicio de Chat (`chat-service`)
- **`ChatWebSocketController`:** Atiende comandos STOMP en `/app/chat.sendMessage` y `/app/chat.join`.
- **`WebSocketSecurityInterceptor`:** Intercepta comandos de conexión y suscripción STOMP, extrayendo y validando el token JWT desde el encabezado `Authorization`.
- **`ChatService`:** Implementa la lógica de negocio de conversaciones. Verifica la participación de los usuarios (`clienteId` o `repartidorId`) y ejecuta peticiones S2S a `/api/internal/pedidos/{id}` en `pedidos-service` para verificar el estado del pedido.
- **`PedidoEventListener`:** Consume el evento `pedido.estado.actualizado` de RabbitMQ. Si el nuevo estado es `ENTREGADO`, marca automáticamente la conversación como `CERRADO` bloqueando nuevos mensajes.
- **`RetentionService`:** Ejecuta una tarea programada (`@Scheduled(cron = "0 0 3 * * ?")`) para purgar de la base de datos las conversaciones cerradas con antigüedad superior al límite configurado (`CHAT_RETENTION_DAYS`).

---

## ☁️ Infraestructura como Código (Terraform en AWS)

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

- **`modules/network`**: Creación de VPC, Subnet pública, Internet Gateway y tablas de ruteo.
- **`modules/security-group`**: Enfoque de mínimo privilegio:
  - **Puertos Públicos Abiertos (0.0.0.0/0):** `80` (HTTP) y `443` (HTTPS).
  - **Puerto SSH:** `22` (restringido obligatoriamente vía `allowed_ssh_cidr`).
  - **Puertos Bloqueados al Exterior:** PostgreSQL (`5432`), RabbitMQ (`5672/15672`), BFF (`8090`), Chat (`8091`) y Microservicios (`8081-8086`) son inaccesibles desde Internet y solo se comunican internamente en la red de Docker.
- **`modules/ec2`**: Instancia EC2 Linux configurada con Docker Engine y Docker Compose Plugin. Resoluble dinámicamente con la AMI oficial de Ubuntu 22.04 LTS.
- **`modules/elastic-ip`**: Elastic IP estática asociada explícitamente (`aws_eip_association`) para garantizar la persistencia de la IP pública ante reinicios de la máquina.
- **`modules/api-gateway`**: HTTP API Gateway v2 integrado vía proxy directo HTTP hacia Caddy/BFF (`ANY /api/bff/{proxy+}`).
- **DNS Dinámico (sslip.io):** Generación automática del hostname (ej. `https://3-92-44-37.sslip.io`) a partir de la Elastic IP para SSL/TLS transparente gestionado por Caddy.

---

## 🐳 Microservicios & Contenedores (Docker Compose)

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

## 🗄️ Migraciones de Base de Datos (Flyway)

Todas las bases de datos utilizan `ddl-auto=validate` en producción y aplican versionamiento estricto con scripts SQL Flyway:

- **`usuario-service` (`usuario_db`):** `V1__init_schema.sql`, `V2__agregar_rol_repartidor.sql`
- **`pedidos-service` (`pedidos_db`):** `V1__init_schema.sql`, `V2__agregar_columna_repartidor.sql`
- **`chat-service` (`chat_db`):** `V1__init_chat_schema.sql`

---

## 🚀 Integración Continua & Infraestructura (CI/CD - GitHub Actions)

- **Workflow CI (`.github/workflows/ci.yml`):**
  - Otorga permisos de ejecución a todos los `mvnw` wrappers (`chmod +x`).
  - Ejecuta `mvn test` en los 7 microservicios y el BFF (157 unit/integration tests).
  - Verifica la compilación de Angular (`npm run build`).
  - Formatea y valida la sintaxis de Terraform (`terraform fmt`, `terraform validate`).
  - Sintetiza la configuración de Docker Compose (`docker compose config`).
- **Workflow CD (`.github/workflows/cd.yml`):**
  - Ejecuta `terraform plan` de forma no interactiva para validar cambios en AWS (sin `terraform apply` automático mientras el estado sea local).

---

## ⚠️ Estado Actual & Mejoras Pendientes para Etapa 2

- **Autorización Módulos Secundarios:** Refinar políticas finas de autorización a nivel de microservicio en `carrito-service`, `productos-service`, `notificacion-service` y `analitica-service`.
- **Concurrencia Repartidor:** Implementación de bloqueo optimista (`@Version`) o actualización condicional SQL cuando múltiples repartidores intentan tomar el mismo pedido en simultáneo.
- **Backend Remoto Terraform:** Migración del archivo `terraform.tfstate` desde estado local a un backend S3 remoto con bloqueo DynamoDB.
- **Conexión Frontend a API Gateway:** Integración completa de las peticiones REST del frontend a través del endpoint de API Gateway.

---

## 🛠️ Ejecución Local Rápida

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
