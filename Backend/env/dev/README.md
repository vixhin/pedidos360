# Entorno de Desarrollo Local (DEV) - Pedidos360

Este directorio contiene las configuraciones necesarias para ejecutar los microservicios en **modo desarrollo**.

## Características del Perfil `dev`
- **Perfiles Spring Boot**: Todos los microservicios ejecutan por defecto con `spring.profiles.active=dev`.
- **Valores por Defecto**: Si no se inyectan variables de entorno, la aplicación usará valores locales seguros (ej. `localhost`, puertos locales, `ddl-auto=update`).
- **Base de Datos**: PostgreSQL local (`localhost:5432`).
- **RabbitMQ**: Instancia local en Docker (`localhost:5672` y UI en `http://localhost:15672`).

## Cómo Usar
Para cargar las variables de este entorno en tu sesión actual de terminal:
- **Bash / Git Bash**: `export $(cat env/dev/.env | xargs)`
- **PowerShell**: `Get-Content env/dev/.env | Foreach-Object { if ($_ -match '^([^=]+)=(.*)$') { [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }`
