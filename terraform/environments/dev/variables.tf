variable "aws_region" {
  type        = string
  description = "AWS Region to deploy resources"
  default     = "us-east-1"
}

variable "ami_id" {
  type        = string
  description = "AMI ID for Ubuntu 22.04 LTS. If empty, automatically resolves the latest official Ubuntu 22.04 LTS AMI."
  default     = ""
}

variable "instance_type" {
  type        = string
  description = "EC2 instance type (e.g. t3.medium or t2.micro depending on AWS Academy permissions)"
  default     = "t3.medium"
}

variable "key_name" {
  type        = string
  description = "Optional SSH key pair name"
  default     = ""
}

variable "allowed_ssh_cidr" {
  type        = string
  description = "Required CIDR block allowed for SSH access (e.g. 190.x.x.x/32). Must be specified explicitly."
}

variable "environment" {
  type        = string
  description = "Environment name"
  default     = "dev"
}

# ─── BASE DE DATOS AWS RDS POSTGRESQL ─────────────────────────────────────────

variable "enable_rds" {
  type        = bool
  description = "Whether to provision the independent AWS RDS PostgreSQL database"
  default     = false
}

variable "db_instance_class" {
  type        = string
  description = "RDS instance class (e.g. db.t3.micro for low cost in Learner Lab)"
  default     = "db.t3.micro"
}

variable "db_name" {
  type        = string
  description = "Initial default database name created in RDS"
  default     = "postgres"
}

variable "db_username" {
  type        = string
  description = "Master username for RDS PostgreSQL"
  default     = "postgres"
}

variable "db_password" {
  type        = string
  description = "Master password for RDS PostgreSQL"
  sensitive   = true
  default     = "Pedidos360Secure2026!"
}

variable "db_publicly_accessible" {
  type        = bool
  description = "Whether RDS is accessible from the internet (false recommended for VPC private security)"
  default     = false
}

variable "allowed_db_admin_cidr" {
  type        = string
  description = "Optional CIDR block allowed direct inbound access to port 5432 for administrative GUI tools"
  default     = ""
}
