# Pedidos360 — Infraestructura como Código (Terraform IaC)

Este directorio contiene la definición de infraestructura en AWS para el proyecto **Pedidos360**, utilizando **Terraform** organizado en módulos reutilizables y entornos desacoplados (`dev` y `prod`).

---

## 📋 Requisitos Previos

- **Terraform CLI**: `>= 1.5.0`
- **AWS CLI**: `>= 2.0`
- **Cuenta AWS / AWS Academy Learner Lab**

---

## 🔐 Configuración de Credenciales AWS

Para ejecutar comandos de Terraform de forma local o en AWS Academy, configure las credenciales de sesión en su terminal:

```bash
export AWS_ACCESS_KEY_ID="ASIA..."
export AWS_SECRET_ACCESS_KEY="wJalrXUtn..."
export AWS_SESSION_TOKEN="FwoGZXIvYXdz..."
export AWS_DEFAULT_REGION="us-east-1"
```

*En Windows PowerShell:*

```powershell
$env:AWS_ACCESS_KEY_ID="ASIA..."
$env:AWS_SECRET_ACCESS_KEY="wJalrXUtn..."
$env:AWS_SESSION_TOKEN="FwoGZXIvYXdz..."
$env:AWS_DEFAULT_REGION="us-east-1"
```

---

## 📁 Estructura del Proyecto

```
terraform/
├── environments/
│   ├── dev/                  # Entorno de Desarrollo (State local independiente)
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── terraform.tfvars.example
│   └── prod/                 # Entorno de Producción (State local independiente)
│       ├── main.tf
│       ├── variables.tf
│       ├── outputs.tf
│       └── terraform.tfvars.example
└── modules/
    ├── network/              # VPC, Subnet Pública, IGW, Route Table
    ├── security_group/       # Reglas de SG (HTTP 80, HTTPS 443, SSH 22 restringido)
    ├── ec2/                  # Instancia EC2 Linux (Ubuntu + Docker Engine)
    ├── elastic_ip/           # Elastic IP estática
    └── api_gateway/          # HTTP API Gateway v2 (Proxy ANY /api/bff/*, CORS & Throttling)
```

---

## 🛠️ Comandos de Aprovisionamiento (Terraform)

### Entorno de Desarrollo (`dev`)

```bash
# 1. Posicionarse en el entorno dev
cd terraform/environments/dev

# 2. Crear archivo de variables locales a partir del ejemplo
cp terraform.tfvars.example terraform.tfvars

# Editar terraform.tfvars e indicar tu IP pública en allowed_ssh_cidr:
# allowed_ssh_cidr = "190.x.x.x/32"

# 3. Inicializar módulos y proveedores
terraform init

# 4. Validar sintaxis y formato
terraform fmt
terraform validate

# 5. Generar plan de ejecución
terraform plan

# 6. Aplicar infraestructura (Localmente)
terraform apply
```

### Entorno de Producción (`prod`)

```bash
cd terraform/environments/prod
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform validate
terraform plan
terraform apply
```

---

## 🚀 ETAPA 2 — Proceso de Despliegue en EC2 (Deployment)

Una vez completado `terraform apply`, ejecute el script automatizado e idempotente de despliegue:

### En Linux / macOS / Bash:
```bash
./scripts/deploy-ec2.sh dev ~/.ssh/pedidos360-dev-key.pem
```

### En Windows PowerShell:
```powershell
.\scripts\deploy-ec2.ps1 -Environment dev -SshKeyPath "C:\Users\tu-usuario\.ssh\pedidos360-dev-key.pem"
```

### Pasos Internos del Script de Despliegue:
1. Lee `public_ip` y `public_dns` desde `terraform output`.
2. Genera dinámicamente `PUBLIC_DOMAIN` (ej. `3-92-44-37.sslip.io`) y `PUBLIC_URL` (`https://3-92-44-37.sslip.io`).
3. Resuelve el endpoint de API Gateway (`BFF_PUBLIC_URL`).
4. Se conecta vía SSH a la EC2 e instala/actualiza el código en `/opt/pedidos360/app`.
5. Genera el archivo `/opt/pedidos360/app/Backend/.env.production`.
6. Levanta los contenedores aislados usando Docker Compose:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
   ```
7. Verifica que Caddy obtenga el certificado HTTPS y que la aplicación responda.

---

## 🔐 Configuración de Microsoft Entra ID (Azure AD)

Tras desplegar en AWS, registre la URL pública generada en el portal de Azure AD:

- **Redirect URI (SPA):** `https://3-92-44-37.sslip.io`
- **Post-Logout Redirect URI:** `https://3-92-44-37.sslip.io`

---

## 🔄 Redeploy & Rollback

### Re-desplegar última versión:
```bash
./scripts/deploy-ec2.sh dev
```

### Rollback a un commit anterior:
```bash
ssh -i ~/.ssh/pedidos360-dev-key.pem ubuntu@<PUBLIC_IP>
cd /opt/pedidos360/app
git checkout <COMMIT_SHA_ANTERIOR>
cd Backend
docker compose -f docker-compose.yml -f docker-compose.ec2.yml --env-file .env.production up -d --build
```

---

## 💾 Gestión de Estado (Terraform State)

- Cada entorno (`dev` y `prod`) posee su propio archivo de estado local **`terraform.tfstate`**.
- Los archivos `*.tfstate` y `terraform.tfvars` están incluidos en `.gitignore` y **NUNCA** deben subirse al repositorio Git.
- **Importante:** Mientras el estado sea local, los comandos `terraform apply` y `terraform destroy` deben ejecutarse únicamente desde la máquina del desarrollador donde reside el archivo `terraform.tfstate`.

---

## 🛑 Detener EC2 vs Destruir Infraestructura (`stop` vs `destroy`)

### 1. Apagar Temporalmente la Instancia (`stop`)
Apaga la máquina virtual sin destruir la VPC, Security Group ni la Elastic IP. **La IP pública se mantiene estable al volver a iniciar.**

```bash
AWS_INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=tag:Name,Values=pedidos360-ec2-dev" --query "Reservations[*].Instances[*].InstanceId" --output text)

aws ec2 stop-instances --instance-ids $AWS_INSTANCE_ID
aws ec2 wait instance-stopped --instance-ids $AWS_INSTANCE_ID

# Re-iniciar
aws ec2 start-instances --instance-ids $AWS_INSTANCE_ID
aws ec2 wait instance-running --instance-ids $AWS_INSTANCE_ID
```

### 2. Destruir Toda la Infraestructura (`destroy`)
Elimina completamente todos los recursos AWS administrados por Terraform en el entorno:

```bash
cd terraform/environments/dev
terraform destroy
```

> **Advertencia:** Al ejecutar `terraform destroy` se elimina la instancia EC2 y los datos almacenados en su disco EBS local.
