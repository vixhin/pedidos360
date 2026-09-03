# 🚀 Guía Completa de Microservicios Backend - Pedidos360

Esta documentación contiene el **paso a paso detallado** para levantar la infraestructura con **Docker/PostgreSQL/pgAdmin**, ejecutar los microservicios y consumir la API REST de cada uno de los 6 microservicios con **JWT** y ejemplos de **JSON copy-pasteable**.

---

## 📑 Tabla de Contenidos
1. [Mapeo General de Microservicios](#1-mapeo-general-de-microservicios)
2. [Paso 1: Levantar la Base de Datos y pgAdmin](#paso-1-levantar-la-base-de-datos-y-pgadmin)
3. [Paso 2: Conectar pgAdmin 4 a PostgreSQL](#paso-2-conectar-pgadmin-4-a-postgresql)
4. [Paso 3: Arrancar los Microservicios](#paso-3-arrancar-los-microservicios)
5. [Paso 4: Guía Paso a Paso por Microservicio](#paso-4-guía-paso-a-paso-por-microservicio)
   - [1. Microservicio Usuario (8081) - JWT & Auth](#1-microservicio-usuario-puerto-8081---autenticación--jwt)
   - [2. Microservicio Pedidos (8082)](#2-microservicio-pedidos-puerto-8082)
   - [3. Microservicio Carrito (8083)](#3-microservicio-carrito-puerto-8083)
   - [4. Microservicio Analítica (8084)](#4-microservicio-anal%C3%ADtica-puerto-8084)
   - [5. Microservicio Productos (8085)](#5-microservicio-productos-puerto-8085)
   - [6. Microservicio Notificación (8086)](#6-microservicio-notificaci%C3%B3n-puerto-8086)

---

## 1. Mapeo General de Microservicios

| Microservicio | Puerto | Base de Datos PostgreSQL | Tabla Principal | Autenticación |
| :--- | :--- | :--- | :--- | :--- |
| **`usuario`** | `8081` | `usuario_db` | `usuarios` | JWT / Public Login & Register |
| **`pedidos`** | `8082` | `pedidos_db` | `pedidos` | Requerida / Endpoint REST |
| **`carrito`** | `8083` | `carrito_db` | `carrito_items` | Requerida / Endpoint REST |
| **`analitica`** | `8084` | `analitica_db` | `analitica_eventos` | Requerida / Endpoint REST |
| **`productos`** | `8085` | `productos_db` | `productos` | Requerida / Endpoint REST |
| **`notificacion`** | `8086` | `notificacion_db` | `notificaciones` | Requerida / Endpoint REST |

---

## Paso 1: Levantar la Base de Datos y pgAdmin

Abre una terminal en la carpeta `Backend/` y ejecuta:

```bash
docker compose up -d
```

Esto levantará los contenedores de **PostgreSQL (Puerto 5432)** y **pgAdmin 4 (Puerto 5050)**.

---

## Paso 2: Conectar pgAdmin 4 a PostgreSQL

1. Abre tu navegador e ingresa a **`http://localhost:5050`**
2. Inicia sesión en pgAdmin:
   - **Email**: `admin@pedidos360.cl`
   - **Password**: `admin`
3. Registra el Servidor en pgAdmin:
   - Haz clic derecho sobre **Servers** ➔ **Register** ➔ **Server...**
   - En pestaña **General** ➔ Name: `Pedidos360 Local`
   - En pestaña **Connection**:
     - **Host name/address**: `postgres` *(o `host.docker.internal`)*
     - **Port**: `5432`
     - **Maintenance database**: `postgres`
     - **Username**: `postgres`
     - **Password**: `postgres`
   - Marca **Save password?** y haz clic en **Save**.

¡Verás creadas automáticamente las 6 bases de datos (`usuario_db`, `pedidos_db`, `carrito_db`, `analitica_db`, `productos_db`, `notificacion_db`)!

---

## Paso 3: Arrancar los Microservicios

Para iniciar todos los microservicios en ventanas o procesos en segundo plano:

```powershell
# En PowerShell desde la carpeta Backend:
.\run-all.ps1
```

O si prefieres arrancar un microservicio individualmente:
```bash
cd Backend/usuario
./mvnw spring-boot:run
```

---

## Paso 4: Guía Paso a Paso por Microservicio

---

### 1. Microservicio Usuario (Puerto `8081`) - Autenticación & JWT

#### A. Iniciar Sesión (Login Admin)
- **Método**: `POST`
- **URL**: `http://localhost:8081/api/auth/login`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "email": "admin@pedidos360.cl",
  "password": "chupalovixo"
}
```
- **Respuesta (`200 OK`)**: Te entregará el `token`. Copia ese token.

#### B. Registrar un Nuevo Usuario
- **Método**: `POST`
- **URL**: `http://localhost:8081/api/auth/register`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "nombre": "Juan Pérez",
  "email": "juan.perez@pedidos360.cl",
  "password": "miPasswordSeguro123",
  "rol": "CLIENTE"
}
```

#### C. Listar Usuarios (Requiere JWT)
- **Método**: `GET`
- **URL**: `http://localhost:8081/api/usuario`
- **Pestaña Authorization en Postman**:
  - Type: **Bearer Token**
  - Token: *(Pega el JWT obtenido en el login)*

---

### 2. Microservicio Pedidos (Puerto `8082`)

#### A. Crear un Nuevo Pedido
- **Método**: `POST`
- **URL**: `http://localhost:8082/api/pedidos`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "usuarioId": 1,
  "total": 35990.00,
  "estado": "PENDIENTE"
}
```

#### B. Obtener Todos los Pedidos
- **Método**: `GET`
- **URL**: `http://localhost:8082/api/pedidos`

#### C. Obtener Pedidos de un Usuario Específico
- **Método**: `GET`
- **URL**: `http://localhost:8082/api/pedidos/usuario/1`

#### D. Eliminar un Pedido por ID
- **Método**: `DELETE`
- **URL**: `http://localhost:8082/api/pedidos/1`

---

### 3. Microservicio Carrito (Puerto `8083`)

#### A. Agregar un Producto al Carrito
- **Método**: `POST`
- **URL**: `http://localhost:8083/api/carrito`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "usuarioId": 1,
  "productoId": 101,
  "cantidad": 2,
  "precioUnitario": 12990.00
}
```

#### B. Obtener el Carrito de un Usuario
- **Método**: `GET`
- **URL**: `http://localhost:8083/api/carrito/usuario/1`

#### C. Eliminar un Ítem del Carrito
- **Método**: `DELETE`
- **URL**: `http://localhost:8083/api/carrito/1`

#### D. Vaciar Todo el Carrito de un Usuario
- **Método**: `DELETE`
- **URL**: `http://localhost:8083/api/carrito/usuario/1`

---

### 4. Microservicio Analítica (Puerto `8084`)

#### A. Registrar un Evento de Analítica
- **Método**: `POST`
- **URL**: `http://localhost:8084/api/analitica`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "tipoEvento": "COMPRA_SUCCESS",
  "descripcion": "El usuario ID 1 realizó una compra por $35.990"
}
```

#### B. Listar Todos los Eventos
- **Método**: `GET`
- **URL**: `http://localhost:8084/api/analitica`

#### C. Filtrar Eventos por Tipo
- **Método**: `GET`
- **URL**: `http://localhost:8084/api/analitica/tipo/COMPRA_SUCCESS`

---

### 5. Microservicio Productos (Puerto `8085`)

#### A. Crear un Nuevo Producto en el Catálogo
- **Método**: `POST`
- **URL**: `http://localhost:8085/api/productos`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "nombre": "Teclado Mecánico RGB",
  "descripcion": "Teclado gamer switches blue",
  "precio": 45990.00,
  "stock": 30
}
```

#### B. Listar Todos los Productos
- **Método**: `GET`
- **URL**: `http://localhost:8085/api/productos`

#### C. Buscar Productos por Nombre
- **Método**: `GET`
- **URL**: `http://localhost:8085/api/productos/buscar?nombre=Teclado`

#### D. Obtener Producto por ID
- **Método**: `GET`
- **URL**: `http://localhost:8085/api/productos/1`

---

### 6. Microservicio Notificación (Puerto `8086`)

#### A. Enviar una Notificación
- **Método**: `POST`
- **URL**: `http://localhost:8086/api/notificacion`
- **Header**: `Content-Type: application/json`
- **Body JSON**:
```json
{
  "usuarioId": 1,
  "mensaje": "Tu pedido #1 ha sido despachado exitosamente.",
  "canal": "EMAIL"
}
```

#### B. Listar Todas las Notificaciones
- **Método**: `GET`
- **URL**: `http://localhost:8086/api/notificacion`

#### C. Obtener Notificaciones de un Usuario
- **Método**: `GET`
- **URL**: `http://localhost:8086/api/notificacion/usuario/1`
