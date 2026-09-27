# Entorno de Producción (PROD) - Pedidos360

Este directorio contiene la guía y plantilla de variables de entorno para desplegar el Backend en **modo producción** (ej. AWS EC2, Docker Compose, Kubernetes).

## Características del Perfil `prod`
- **Perfiles Spring Boot**: `spring.profiles.active=prod`.
- **Validación Estricta**: No se permiten secretos o fallbacks por defecto inseguros.
- **Base de Datos**: Hibernate `ddl-auto=validate` o `none` (las migraciones se gestionan exclusivamente con Flyway).
- **Logging**: Desactiva logs detallados de SQL (`show-sql=false`) y oculta la traza de errores detallada a clientes externos por seguridad.

## Cómo Desplegar
1. Copiar la plantilla `.env.example` a `.env` en el servidor:
   ```bash
   cp env/prod/.env.example env/prod/.env
   ```
2. Completar las credenciales reales de producción (base de datos, Azure AD, RabbitMQ, BFF_INTERNAL_KEY).
3. Levantar los servicios inyectando este archivo en Docker:
   ```bash
   docker compose --env-file env/prod/.env -f docker-compose.ec2.yml up -d --build
   ```
