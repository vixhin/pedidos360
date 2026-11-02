# Manual de Despliegue en Producción (Deployment Runbook) — Pedidos360

Este documento detalla el paso a paso exacto para aprovisionar y desplegar la plataforma Pedidos360 en **AWS Academy Learner Lab** sin exceder el crédito de $50 USD.

---

## 1. Prerrequisitos en la Máquina del Operador

1. **AWS CLI** instalado y configurado con credenciales activas del Learner Lab (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`).
2. **Terraform >= 1.5.0** instalado.
3. Llave SSH generada o par de claves existente en AWS (`pedidos360-key.pem`).
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

Al finalizar, Terraform mostrará los outputs:
- `elastic_ip`: Dirección IP estática (ej: `3.92.44.37`)
- `public_domain`: Dominio dinámico sslip.io (ej: `3-92-44-37.sslip.io`)
- `public_url`: URL HTTPS lista para producción (ej: `https://3-92-44-37.sslip.io`)

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

### Paso 4: Configurar los Secretos de Producción (`.env.production`)

Navegue a la carpeta de backend y copie la plantilla de producción:
```bash
cd /opt/pedidos360/Backend
cp .env.production.example .env.production
chmod 600 .env.production
```

Edite `.env.production` completando los valores de producción:
```bash
nano .env.production
```

Variables clave requeridas:
- `PUBLIC_DOMAIN`: `<ELASTIC_IP_CON_GUIONES>.sslip.io`
- `PUBLIC_URL`: `https://<ELASTIC_IP_CON_GUIONES>.sslip.io`
- `POSTGRES_USER` & `POSTGRES_PASSWORD`: Credenciales de PostgreSQL
- `RABBITMQ_USER` & `RABBITMQ_PASSWORD`: Credenciales del broker
- `JWT_SECRET`: Cadena aleatoria de mínimo 32 caracteres (generar con `openssl rand -hex 32`)
- `BFF_INTERNAL_KEY`: Cadena secreta S2S compartida de mínimo 32 caracteres
- `ADMIN_BOOTSTRAP_TOKEN`: Token para crear el primer administrador del sistema
- `TURNSTILE_SITE_KEY` & `TURNSTILE_SECRET_KEY`: Claves de Cloudflare

### Paso 5: Levantar la Pila de Contenedores con Docker Compose

```bash
docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
```

### Paso 6: Verificación de Estado y Salud del Sistema

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

3. **Verificar migraciones Flyway:**
   ```bash
   docker compose logs usuario | grep -i "flyway"
   docker compose logs pedidos | grep -i "flyway"
   ```

### Paso 7: Inicializar el Usuario Administrador (Bootstrap One-Shot)

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

