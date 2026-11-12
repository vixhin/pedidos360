# Pedidos360 — Plataforma Cloud Native de E-Commerce, Logística, Streaming y Analítica

Plataforma integral de comercio electrónico y logística de última milla en tiempo real con arquitectura de microservicios sobre **Spring Boot / Java 21**, frontend moderno en **Angular 21**, autenticación híbrida (**Microsoft Entra ID / Azure AD** y **JWT local en base de datos**), protección anti-bot con **Cloudflare Turnstile**, mensajería transaccional asíncrona con **RabbitMQ (DLX/DLQ y administración dinámica de topología)**, procesamiento de eventos en streaming con **Apache Kafka (KRaft / ZooKeeper con Outbox Pattern y Dead Letter Topic)**, persistencia aislada en **PostgreSQL 16** con versionamiento **Flyway**, proxy inverso seguro con **Caddy (HTTPS automático)** e infraestructura como código con **Terraform en AWS**.

---

## Contenido

1. [Arquitectura General del Sistema](#arquitectura-general-del-sistema)
2. [Arquitectura de Uso y Flujos por Rol](#arquitectura-de-uso-y-flujos-por-rol)
3. [Integración de Mensajería y Streaming (RabbitMQ + Kafka)](#integracion-de-mensajeria-y-streaming-rabbitmq--kafka)
4. [Estructura del Repositorio](#estructura-del-repositorio)
5. [Autenticación Híbrida y Seguridad](#autenticacion-hibrida-y-seguridad)
6. [Microservicios y Puertos](#microservicios-y-puertos)
7. [APIs de Administración (RabbitMQ y Kafka)](#apis-de-administracion-rabbitmq-y-kafka)
8. [Opciones de Ejecución con Docker Compose](#opciones-de-ejecucion-con-docker-compose)
9. [Scripts de Infraestructura y Gestión](#scripts-de-infraestructura-y-gestion)
10. [Variables de Entorno y Manejo Seguro de Secretos](#variables-de-entorno-y-manejo-seguro-de-secretos)
11. [Infraestructura como Código (Terraform) y Despliegue en AWS](#infraestructura-como-codigo-terraform-y-despliegue-en-aws)
12. [Pruebas y Calidad de Código](#pruebas-y-calidad-de-codigo)

---

## Arquitectura General del Sistema

El sistema implementa el patrón **BFF (Backend for Frontend)** y **API Gateway** con aislamiento estricto de redes internas:

```
                                  USUARIO / CLIENTE
                                          │
                        ┌─────────────────┴─────────────────┐
                        │                                   │
                   (Navegador)                         (Navegador)
               Microsoft Entra ID                  Login Local + Captcha
             (OAuth2 / PKCE Tokens)               (Cloudflare Turnstile)
                        │                                   │
                        └─────────────────┬─────────────────┘
                                          │  HTTPS / WSS
                                          v
                                    CADDY PROXY
                         (TLS Automático / Reverse Proxy)
                                          │
                    ┌─────────────────────┴─────────────────────┐
                    │                                           │
                    v                                           v
         Angular 21 Frontend (SPA)                     BFF / API Gateway :8090
       (Portal Cliente, Vendedor,                     (Validador Resource Server
        Repartidor, Analítica, Staff)                  de Entra ID, Filtros RBAC)
                                                                │
     ┌──────────────────┬─────────────────┬─────────────────────┼─────────────────────┬──────────────────┐
     │                  │                 │                     │                     │                  │
     v                  v                 v                     v                     v                  v
usuario :8081     pedidos :8082     carrito :8083        productos :8085        analitica :8084     notificacion :8086
 (RBAC, Sesiones,  (Outbox Pattern,  (Expiración,          (Stock, Reservas,     (Kafka Consumer,    (Push/Inbox,
  Admin Rabbit)     Motor Precios)   Idempotencia)         Catálogo)             Admin Kafka)        Plantillas)
     │                  │                 │                     │                     │                  │
     │            chat :8091              │                     │                     │                  │
     │            (WebSocket/STOMP)       │                     │                     │                  │
     │                  │                 │                     │                     │                  │
     └──────────────────┴────────┬────────┴─────────────────────┴─────────────────────┴──────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
                 v                               v
          PostgreSQL 16                  RABBITMQ CLUSTER (AMQP)
     (7 Bases de Datos Aisladas           (Eventos Operacionales,
       con Migraciones Flyway)             ACK/NACK manual, DLX/DLQ)
                                                 │
                                 ┌───────────────┴───────────────┐
                                 v                               v
                       APACHE KAFKA CLUSTER              KAFKA UI :8089
                     (KRaft / ZooKeeper Brokers,     (Gestión Visual Web
                      Event Streaming, Outbox DLT)    de Brokers y Tópicos)
```

---

## Arquitectura de Uso y Flujos por Rol

Pedidos360 organiza sus funcionalidades según roles y casos de negocio:

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│     CLIENTE     │       │    VENDEDOR     │       │   REPARTIDOR    │       │  ADMINISTRADOR  │
├─────────────────┤       ├─────────────────┤       ├─────────────────┤       ├─────────────────┤
│• Catálogo/Stock │       │• Gestión Stock  │       │• Pedidos Libres │       │• Panel Métricas │
│• Carrito activo │       │• Reservas Stock │       │• Autoasignación │       │• Admin RabbitMQ │
│• Cupones/Reglas │       │• Lista Pedidos  │       │• Estado Entrega │       │• Admin Kafka    │
│• Seguimiento    │       │• Notificaciones │       │• Chat en Vivo   │       │• Gestión Users  │
│• Chat Repartidor│       │• Actualizaciones│       │• Historial Ruta │       │• Bootstrap Prod │
└─────────────────┘       └─────────────────┘       └─────────────────┘       └─────────────────┘
```

### 1. Flujo de Compra y Despacho (Cliente ➔ Vendedor ➔ Repartidor)
1. **Autenticación:** El usuario inicia sesión mediante Microsoft Entra ID o cuenta local en base de datos protegida con **Cloudflare Turnstile**.
2. **Catálogo y Carrito:** El cliente explora productos en `productos-service`. Al agregar artículos, `carrito-service` valida propiedad e interactúa con el stock.
3. **Cotización y Cupones:** `pedidos-service` evalúa reglas comerciales en `MotorComercialService` (tarifas comunales, umbrales de envío gratis y cupones de descuento).
4. **Confirmación con Outbox Pattern:** Al crearse el pedido, se almacena transaccionalmente en PostgreSQL y en la tabla de Outbox. El publicador asíncrono distribuye el evento a:
   * **RabbitMQ:** Para notificar a `carrito-service` (vaciar carrito) y `notificacion-service`.
   * **Apache Kafka (`pedidos360.pedidos.v1`):** Para alimentar analítica en tiempo real y registro histórico de eventos.
5. **Asignación y Entrega:** Los repartidores visualizan pedidos libres en el portal `/repartidor`. Al tomar un pedido, se habilita una sala de **Chat en Tiempo Real** mediante WebSocket/STOMP en `chat-service`.

---

## Integración de Mensajería y Streaming (RabbitMQ + Kafka)

El diseño arquitectónico desacopla deliberadamente los propósitos de mensajería:

| Característica | RabbitMQ (Event-Driven Microservices) | Apache Kafka (Event Streaming & Analytics) |
| :--- | :--- | :--- |
| **Propósito Principal** | Coordinación operativa síncrona/asíncrona entre microservicios de negocio. | Streaming continuo de eventos, auditoría inmutable y analítica en tiempo real. |
| **Garantía de Entrega** | Confirmaciones manuales `basicAck` / `basicNack` con Dead Letter Exchange (`pedidos360.dlx`). | Outbox Pattern transaccional, `acks=all`, productor idempotente y Dead Letter Topic (`pedidos360.pedidos.dlt.v1`). |
| **Retención** | Efímera (el mensaje se elimina al confirmarse el consumo). | Persistente y configurable (retención de 24h para datos y 7 días para DLT con política de limpieza). |
| **Administración** | API REST dinámica en `usuario-service` (`/api/admin/rabbit/**`). | API REST y cliente administrativo en `analitica-service` (`/api/admin/kafka/**`) y CLI scripts. |

---

## Estructura del Repositorio

```
pedidos360/
├── Backend/
│   ├── usuario/                     # Auth, JWT, RBAC, Sessions, Turnstile, RabbitMQ Admin
│   ├── pedidos/                     # Pedidos, Outbox Pattern, Motor Comercial, Cupones
│   ├── productos/                   # Catálogo, Stock, Reservas temporales
│   ├── carrito/                     # Carrito de compra, expiración programada
│   ├── notificacion/                # Notificaciones por usuario, plantillas de evento
│   ├── analitica/                   # Métricas, consumidores Kafka/Rabbit, Kafka Admin API
│   ├── chat/                        # WebSocket / STOMP, conversaciones pedido-repartidor
│   ├── bff/                         # Backend for Frontend, Resource Server Entra ID
│   ├── docker-compose.yml           # Stack estándar para desarrollo local
│   ├── docker-compose.kafka.yml     # Overlay Kafka KRaft + Kafka UI
│   ├── docker-compose.kafka-zookeeper.yml # Clúster Kafka con ZooKeeper y 2 brokers
│   ├── docker-compose.rabbitmq-cluster.yml # Clúster RabbitMQ 3 nodos alta disponibilidad
│   ├── docker-compose.ec2.yml       # Overlay para producción EC2 con Caddy HTTPS
│   ├── Caddyfile                    # Configuración de proxy inverso y TLS automático
│   ├── init-dbs.sql                 # Inicialización de bases de datos PostgreSQL
│   └── .env.example                 # Plantilla de variables de entorno para desarrollo
├── pedidos360-frontend/             # SPA en Angular 21 (vistas de cliente, staff, admin, courier)
├── terraform/                       # Módulos Terraform (network, security_group, ec2, api_gateway)
├── scripts/
│   ├── kafka-admin.sh / .ps1        # Scripts de administración e inspección de tópicos y grupos Kafka
│   ├── backup-postgres.sh           # Respaldo automatizado con compresión gzip
│   ├── restore-postgres.sh          # Restauración controlada de base de datos
│   └── deploy-ec2.sh / .ps1         # Despliegue automatizado idempotente por SSH
└── docs/                            # Documentación de arquitectura, costos AWS y runbooks
```

---

## Autenticación Híbrida y Seguridad

Pedidos360 cuenta con dos vías seguras de autenticación:

1. **Microsoft Entra ID (Azure AD):**
   * El frontend utiliza `@azure/msal-browser` y `@azure/msal-angular` para autenticar usuarios mediante Authorization Code Flow con PKCE.
   * Las peticiones se dirigen a `/api/bff/**`. El BFF valida el token JWT contra los endpoints públicos JWKS de Microsoft, verificando firma, `issuer`, `audience` y scopes antes de comunicarse con los microservicios internos.
2. **Autenticación Local (Base de Datos + JWT):**
   * Endpoint de autenticación propio en `usuario-service` con contraseñas encriptadas mediante BCrypt (cost factor 12).
   * Validado en frontend y backend mediante **Cloudflare Turnstile** para mitigar ataques de fuerza bruta y bots.
   * Emisión de Access Token JWT (HMAC-SHA256 con secreto de longitud criptográfica) y Refresh Tokens rotativos con persistencia de sesiones en tabla `user_sessions`.

---

## Microservicios y Puertos

| Servicio | Puerto Local | Responsabilidad | Base de Datos |
| :--- | :---: | :--- | :--- |
| **Caddy** | `80, 443` | Reverse proxy HTTPS y terminación TLS automática (en despliegue EC2). | — |
| **Frontend** | `4200` | Aplicación Angular 21 (servida vía `npm start` en local o Nginx en contenedor). | — |
| **bff** | `8090` | OAuth2 Resource Server para Entra ID y Gateway para el frontend. | — |
| **usuario** | `8081` | Gestión de usuarios, perfiles, sesiones, Turnstile y Admin RabbitMQ. | `usuario_db` |
| **pedidos** | `8082` | Gestión de pedidos, motor comercial, Outbox Publisher a Kafka y RabbitMQ. | `pedidos_db` |
| **carrito** | `8083` | Carrito de compras y control de expiración. | `carrito_db` |
| **productos** | `8085` | Catálogo, inventario y reservas de stock. | `productos_db` |
| **analitica** | `8084` | Procesamiento analítico de streaming, métricas y Admin API de Kafka. | `analitica_db` |
| **notificacion**| `8086` | Notificaciones en tiempo real derivadas de eventos. | `notificacion_db` |
| **chat** | `8091` | WebSocket / STOMP (`/ws-chat`) para comunicación cliente-repartidor. | `chat_db` |
| **PostgreSQL 16**| `5432` | Motor de base de datos relacional para los microservicios. | Multi-DB |
| **RabbitMQ** | `5672 / 15672`| Broker de mensajería AMQP y consola web de gestión. | — |
| **Apache Kafka**| `9092 / 9094`| Broker de streaming de eventos distribuido. | — |
| **Kafka UI** | `8089` | Interfaz gráfica web para administración de Kafka. | — |

---

## APIs de Administración (RabbitMQ y Kafka)

### 1. Administración de RabbitMQ (`/api/admin/rabbit/**`)
Disponible en `usuario-service` y ruteado a través del BFF (`/api/bff/admin/rabbit/**`):
* `GET /queues`, `POST /queues`, `PUT /queues/{id}`, `DELETE /queues/{id}`: Gestión dinámica de colas y parámetros DLX.
* `GET /exchanges`, `POST /exchanges`, `PUT /exchanges/{id}`, `DELETE /exchanges/{id}`: Gestión de exchanges.
* `GET /bindings`, `POST /bindings`, `DELETE /bindings/{id}`: Enrutamiento dinámico de mensajes.
* `POST /sync`: Sincronización forzada en caliente entre base de datos y broker RabbitMQ.
* *Protección de Recursos:* Los elementos nucleares poseen la marca `protected_resource = true` impidiendo su eliminación accidental (HTTP 409 Conflict).

### 2. Administración de Kafka (`/api/admin/kafka/**`)
Disponible en `analitica-service` y ruteado a través del BFF (`/api/bff/admin/kafka/**`):
* `GET /topics`: Listar todos los tópicos del clúster con sus particiones, réplicas y configuraciones activas.
* `GET /topics/{name}`: Detalle e inspección de un tópico específico.
* `POST /topics`: Creación dinámica de tópicos con particiones, factor de replicación, retención (`retention.ms`) y cleanup policy.
* `PUT /topics/{name}`: Modificación de configuraciones o adición de particiones.
* `DELETE /topics/{name}`: Eliminación controlada de tópicos.
* `GET /consumer-groups`: Listar todos los Consumer Groups activos en el clúster.
* `GET /consumer-groups/{groupId}`: Inspección del estado, miembros asignados y coordinador del grupo.

---

## Opciones de Ejecución con Docker Compose

El repositorio provee entornos reproducibles para diferentes escenarios:

### Opción 1: Desarrollo Local Estándar (Base + Kafka KRaft + Kafka UI)
Inicia los 8 microservicios, PostgreSQL, RabbitMQ, Apache Kafka en modo KRaft y Kafka UI:
```bash
cd Backend
docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d --build
```
* **Frontend Angular:**
  ```bash
  cd pedidos360-frontend
  npm install
  npm start
  ```
  Acceso en `http://localhost:4200`.
* **Consolas Administrativas:**
  * Kafka UI: `http://localhost:8089`
  * RabbitMQ Management: `http://localhost:15672`
  * pgAdmin (opcional): `http://localhost:5050`

### Opción 2: Clúster de RabbitMQ (3 Nodos en Alta Disponibilidad)
Para evaluar resiliencia y clustering de RabbitMQ:
```bash
docker compose -f Backend/docker-compose.rabbitmq-cluster.yml up -d
```
Verificar estado del clúster:
```bash
docker exec -it pedidos360-rabbit1 rabbitmqctl cluster_status
```

### Opción 3: Clúster de Kafka con ZooKeeper y Múltiples Brokers
Para evaluar topología tradicional de streaming con ZooKeeper y 2 brokers Kafka:
```bash
docker compose -f Backend/docker-compose.kafka-zookeeper.yml up -d
```
Consola Kafka UI vinculada al clúster ZooKeeper disponible en `http://localhost:8089`.

---

## Scripts de Infraestructura y Gestión

Ubicados en la carpeta `scripts/`:

### 1. Administración de Kafka vía Terminal
* **Linux / macOS / WSL:** `scripts/kafka-admin.sh`
* **Windows PowerShell:** `scripts/kafka-admin.ps1`

Ejemplos de uso:
```bash
# Listar tópicos
./scripts/kafka-admin.sh list-topics

# Describir un tópico y ver particiones
./scripts/kafka-admin.sh describe-topic pedidos360.pedidos.v1

# Crear un nuevo tópico
./scripts/kafka-admin.sh create-topic mi-nuevo-topico 3 1

# Listar Consumer Groups e inspeccionar lag
./scripts/kafka-admin.sh list-groups
./scripts/kafka-admin.sh describe-group analitica-group
```

### 2. Respaldos y Restauración de Base de Datos
* **Respaldo con compresión gzip y verificación de integridad:**
  ```bash
  ./scripts/backup-postgres.sh
  ```
* **Restauración asistida desde respaldo comprimido:**
  ```bash
  ./scripts/restore-postgres.sh /ruta/al/backup_pedidos360_fecha.sql.gz
  ```

---

## Variables de Entorno y Manejo Seguro de Secretos

El proyecto cuenta con un validador estricto de secretos en el arranque (`StartupSecretsValidator`) que bloquea el inicio en entornos productivos si faltan variables críticas o si se detectan valores por defecto inseguros.

> [!IMPORTANT]
> **NUNCA** suba archivos `.env` reales con claves privadas al control de versiones. El archivo `.gitignore` raíz excluye automáticamente todos los patrones `.env*`.

Para configurar el entorno local, copie la plantilla base:
```bash
cp Backend/.env.example Backend/.env
```

Parámetros clave requeridos:

| Variable | Descripción |
| :--- | :--- |
| `BFF_INTERNAL_KEY` | Clave interna para comunicación S2S entre microservicios (mínimo 32 caracteres). |
| `JWT_SECRET` | Secreto criptográfico HMAC para generación y firma de tokens JWT locales. |
| `ADMIN_BOOTSTRAP_TOKEN` | Token secreto utilizado para el procedimiento de bootstrap inicial del usuario administrador. |
| `AZURE_TENANT_ID` | Identificador de inquilino de Microsoft Entra ID. |
| `AZURE_API_CLIENT_ID`| Identificador de aplicación de la API (BFF) en Entra ID. |
| `TURNSTILE_SITE_KEY` | Clave pública del widget de Cloudflare Turnstile. |
| `TURNSTILE_SECRET_KEY` | Clave secreta para la validación servidor a servidor de Turnstile. |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | Credenciales de conexión al motor de base de datos PostgreSQL. |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | Credenciales del broker de RabbitMQ. |

---

## Infraestructura como Código (Terraform) y Despliegue en AWS

La infraestructura está automatizada en `terraform/` con módulos desacoplados:
* `network`: VPC con subred pública, Internet Gateway y tablas de ruteo.
* `security_group`: Filtrado de puertos de mínimo privilegio (`80`, `443` y SSH `22` restringido a IP autorizada).
* `ec2`: Instancia Ubuntu 22.04 optimizada (t3.medium o t3.large, EBS gp3 cifrado e IMDSv2 obligatorio).
* `elastic_ip`: Dirección IP pública persistente asociada al dominio sslip.io.
* `api_gateway`: API Gateway HTTP para enrutamiento seguro hacia el BFF.

> [!NOTE]
> La arquitectura está diseñada específicamente para optimizar costos en **AWS Academy Learner Lab**, evitando servicios administrados de alto costo (RDS, EKS, ALB o NAT Gateway) y manteniendo un gasto mensual estimado inferior al límite de créditos asignados.

---

## Pruebas y Calidad de Código

El repositorio incluye suites exhaustivas de pruebas automatizadas:
* **Pruebas Unitarias y de Integración Backend:** JUnit 5, Mockito y Spring Boot Starter Test en los 8 microservicios con reporte de cobertura generado por JaCoCo.
* **Pruebas E2E de Frontend:** Playwright (`pedidos360-frontend/e2e/`) que valida flujos completos de inicio de sesión, compra, carrito y navegación entre perfiles.
* **Pipeline de Integración Continua (GitHub Actions):** Automatiza la compilación de microservicios, ejecución de pruebas unitarias, validación de sintaxis de Terraform (`terraform fmt -check`), build del frontend y verificación de configuraciones de Docker Compose en cada Pull Request.

Para ejecutar las pruebas en un microservicio:
```bash
cd Backend/<servicio>
./mvnw test          # En Windows PowerShell: .\mvnw test
```
