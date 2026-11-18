# Manual de Despliegue en Producción (Deployment Runbook) — Pedidos360

Este documento detalla el paso a paso exacto para aprovisionar y desplegar la plataforma Pedidos360 en **AWS Academy Learner Lab** sin exceder el crédito de $50 USD, incluyendo la base de datos independiente administrada en **AWS RDS PostgreSQL** mediante Terraform.

---

## 1. Prerrequisitos en la Máquina del Operador

1. **AWS CLI** instalado y configurado con credenciales activas del Learner Lab (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`).
2. **Terraform >= 1.5.0** instalado.
3. Llave SSH generada o par de claves existente en AWS (`pedidos360-key.pem` o `vockey`).
4. IP pública del operador (para autorizar SSH en el Security Group).

---

## 2. Paso a Paso de Despliegue

### Paso 1: Configurar Variables de Terraform

Navegue al directorio de producción:
```bash
cd terraform/environments/prod
```

Copie la plantilla de variables:
```bash
cp terraform.tfvars.example terraform.tfvars
```

Edite `terraform.tfvars` con sus valores reales:
```hcl
aws_region           = "us-east-1"
instance_type        = "t3.medium"
key_name             = "pedidos360-key"
allowed_ssh_cidr     = "200.x.x.x/32"    # Sustituya por su IP pública real
iam_instance_profile = "LabInstanceProfile"

# Base de datos independiente en AWS RDS PostgreSQL
enable_rds             = true
db_instance_class      = "db.t3.micro"
db_name                = "postgres"
db_username            = "postgres"
db_password            = "Pedidos360Secure2026!"
db_publicly_accessible = false
allowed_db_admin_cidr  = ""
```

### Paso 2: Inicializar y Planificar Infraestructura

```bash
terraform init
terraform plan -out=tfplan
```

Revise el plan generado y aplíquelo manualmente cuando decida aprovisionar:
```bash
terraform apply tfplan
```

Al finalizar, Terraform mostrará los outputs de cómputo y base de datos:
- `public_ip`: Dirección Elastic IP estática (ej: `3.92.44.37`)
- `public_dns`: Hostname público de EC2 (ej: `ec2-3-92-44-37.compute-1.amazonaws.com`)
- `db_endpoint`: Endpoint de AWS RDS (ej: `pedidos360-db-prod.cxxxxxx.us-east-1.rds.amazonaws.com:5432`)
- `db_host`: Host de la base de datos RDS (ej: `pedidos360-db-prod.cxxxxxx.us-east-1.rds.amazonaws.com`)
- `db_port`: Puerto de la base de datos (5432)

### Paso 3: Clonar el Repositorio en la Instancia EC2

Conéctese vía SSH:
```bash
ssh -i pedidos360-key.pem ubuntu@<ELASTIC_IP>
```

Clone el repositorio en `/opt/pedidos360`:
```bash
cd /opt/pedidos360
git clone https://github.com/vixhin/pedidos360.git .
```

### Paso 4: Inicializar las Bases de Datos en AWS RDS (One-Time Setup)

Dado que AWS RDS PostgreSQL se crea como instancia limpia en la nube, ejecute el script automatizado e idempotente desde la EC2 para crear los esquemas aislados de los microservicios:

```bash
cd /opt/pedidos360
chmod +x scripts/init-rds-dbs.sh
DB_HOST="<VALOR_DE_DB_HOST_DE_TERRAFORM>" \
DB_USER="postgres" \
DB_PASSWORD="Pedidos360Secure2026!" \
./scripts/init-rds-dbs.sh
```

El script creará automáticamente las 7 bases de datos requeridas si no existen:
- `usuario_db`
- `pedidos_db`
- `carrito_db`
- `analitica_db`
- `productos_db`
- `notificacion_db`
- `chat_db`

### Paso 5: Configurar los Secretos de Producción (`.env.production`)

Navegue a la carpeta de backend y configure `.env.production`:
```bash
cd /opt/pedidos360/Backend
cp .env.production.example .env.production
chmod 600 .env.production
nano .env.production
```

Variables clave requeridas:
- `PUBLIC_DOMAIN`: `<ELASTIC_IP_CON_GUIONES>.sslip.io`
- `PUBLIC_URL`: `https://<ELASTIC_IP_CON_GUIONES>.sslip.io`
- `DB_HOST`: `<HOST_DE_AWS_RDS>` (obtenido de `terraform output db_host`)
- `DB_PORT`: `5432`
- `POSTGRES_USER` & `POSTGRES_PASSWORD`: Credenciales maestras de RDS
- `RABBITMQ_USER` & `RABBITMQ_PASSWORD`: Credenciales del broker
- `JWT_SECRET`: Cadena aleatoria de mínimo 32 caracteres (generar con `openssl rand -hex 32`)
- `BFF_INTERNAL_KEY`: Cadena secreta S2S compartida de mínimo 32 caracteres
- `ADMIN_BOOTSTRAP_TOKEN`: Token para crear el primer administrador del sistema
- `TURNSTILE_SITE_KEY` & `TURNSTILE_SECRET_KEY`: Claves de Cloudflare

*(Nota: Si utiliza `./scripts/deploy-ec2.sh` o `.\scripts\deploy-ec2.ps1`, este paso y el Paso 4 se ejecutan 100% de manera automática leyendo `terraform output`)*.

### Paso 6: Levantar la Pila de Contenedores con Docker Compose

```bash
docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
```

### Paso 7: Verificación de Estado y Salud del Sistema

1. **Verificar contenedores activos:**
   ```bash
   docker compose ps
   ```
   Todos los contenedores deben figurar en estado `Up` o `healthy`.

2. **Verificar logs del Reverse Proxy Caddy:**
   ```bash
   docker compose logs caddy | grep -i "certificate"
   ```
   Debe confirmar la emisión exitosa del certificado HTTPS Let's Encrypt.

3. **Verificar migraciones Flyway en AWS RDS:**
   ```bash
   docker compose logs usuario | grep -i "flyway"
   docker compose logs pedidos | grep -i "flyway"
   ```

### Paso 8: Inicializar el Usuario Administrador (Bootstrap One-Shot)

Una vez que el backend esté operativo, ejecute una llamada curl POST contra el endpoint de bootstrap:

```bash
curl -i -X POST https://<PUBLIC_DOMAIN>/api/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -H "X-Bootstrap-Token: <SU_ADMIN_BOOTSTRAP_TOKEN>" \
  -d '{
    "nombre": "Administrador Principal",
    "email": "admin@pedidos360.cl",
    "password": "PasswordSuperSegura2026!"
  }'
```

El sistema responderá `201 Created`. Una vez registrado, el endpoint queda deshabilitado para siempre (solo se permite un único administrador vía bootstrap).

---

## 3. Optimización de Costos en AWS Academy Learner Lab

Para no sobrepasar el límite de crédito de **$50 USD**:
1. **RDS Single-AZ `db.t3.micro` con 20GB gp3**: Cuesta aproximadamente ~$0.024/hora (~$17/mes).
2. **EC2 `t3.medium`**: Cuesta aproximadamente ~$0.0416/hora.
3. **Apagar recursos cuando no se usen:**
   - La instancia EC2 se puede pausar con `aws ec2 stop-instances`.
   - La instancia RDS se puede pausar temporalmente desde la consola de AWS RDS (Action -> "Temporarily stop database", se detiene por hasta 7 días sin costo de cómputo, solo pagando el almacenamiento mínimo de 20GB).
4. **Al finalizar el semestre/evaluación:**
   ```bash
   cd terraform/environments/prod
   terraform destroy
   ```
   Esto elimina la EC2, RDS, VPC y Elastic IP inmediatamente, liberando todo consumo de créditos.
