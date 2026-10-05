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
    └── api_gateway/          # HTTP API Gateway v2 (Proxy ANY /api/bff/*)
```

---

## 🛠️ Comandos de Ejecución (Desarrollo & Producción)

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

## 💾 Gestión de Estado (Terraform State)

- Cada entorno (`dev` y `prod`) posee su propio archivo de estado local **`terraform.tfstate`**.
- Los archivos `*.tfstate` y `terraform.tfvars` están incluidos en `.gitignore` y **NUNCA** deben subirse al repositorio Git.
- **Importante:** Mientras el estado sea local, los comandos `terraform apply` y `terraform destroy` deben ejecutarse únicamente desde la máquina del desarrollador donde reside el archivo `terraform.tfstate`.

> **Mejora Futura:** Migración de estado a un Backend Remoto (Bucket S3 + Tabla DynamoDB para State Locking).

---

## 🛑 Detener EC2 vs Destruir Infraestructura (`stop` vs `destroy`)

Para administrar eficientemente los créditos en **AWS Academy**:

### 1. Apagar Temporalmente la Instancia (`stop`)
Apaga la máquina virtual sin destruir la VPC, Security Group ni la Elastic IP. **La IP pública se mantiene estable al volver a iniciar.**

```bash
# Obtener ID de instancia o usar tag
AWS_INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=tag:Name,Values=pedidos360-ec2-dev" --query "Reservations[*].Instances[*].InstanceId" --output text)

# Apagar instancia
aws ec2 stop-instances --instance-ids $AWS_INSTANCE_ID
aws ec2 wait instance-stopped --instance-ids $AWS_INSTANCE_ID

# Volver a iniciar instancia
aws ec2 start-instances --instance-ids $AWS_INSTANCE_ID
aws ec2 wait instance-running --instance-ids $AWS_INSTANCE_ID
```

### 2. Destruir Toda la Infraestructura (`destroy`)
Elimina completamente todos los recursos AWS administrados por Terraform en el entorno:

```bash
cd terraform/environments/dev
terraform destroy
```

---

## 🏗️ Desacoplamiento: Infraestructura vs Despliegue de Aplicación

- **Terraform:** Responsable **únicamente** de aprovisionar la infraestructura AWS (VPC, Subnet, IGW, Security Group, EC2, Elastic IP, API Gateway).
- **Docker Compose:** Responsable de compilar y desplegar los contenedores de la aplicación (Angular, Caddy, BFF, 7 Microservicios Spring Boot, PostgreSQL y RabbitMQ) dentro de la EC2.
