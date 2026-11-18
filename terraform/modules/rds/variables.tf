variable "environment" {
  type        = string
  description = "Deployment environment (dev, prod)"
}

variable "vpc_id" {
  type        = string
  description = "ID of the VPC where RDS resources are provisioned"
}

variable "subnet_ids" {
  type        = list(string)
  description = "List of at least two subnet IDs in distinct AZs for the DB Subnet Group"
}

variable "ec2_security_group_id" {
  type        = string
  description = "Security group ID of the EC2 instance allowed to connect to RDS on port 5432"
}

variable "db_name" {
  type        = string
  description = "Initial default database name created in RDS"
  default     = "postgres"
}

variable "db_username" {
  type        = string
  description = "Master username for PostgreSQL"
  default     = "postgres"
}

variable "db_password" {
  type        = string
  description = "Master password for PostgreSQL"
  sensitive   = true
}

variable "engine_version" {
  type        = string
  description = "PostgreSQL engine version"
  default     = "16.3"
}

variable "instance_class" {
  type        = string
  description = "RDS instance class (e.g. db.t3.micro, cost-effective for Learner Lab)"
  default     = "db.t3.micro"
}

variable "allocated_storage" {
  type        = number
  description = "Allocated storage size in GiB"
  default     = 20
}

variable "max_allocated_storage" {
  type        = number
  description = "Maximum storage limit in GiB for autoscaling (20 disables autoscale cost increase)"
  default     = 20
}

variable "publicly_accessible" {
  type        = bool
  description = "Whether the RDS instance is accessible from the internet (false recommended for VPC security)"
  default     = false
}

variable "backup_retention_period" {
  type        = number
  description = "Days of automated backup retention (0 to avoid snapshot billing in labs)"
  default     = 0
}

variable "allowed_admin_cidr" {
  type        = string
  description = "Optional CIDR block allowed direct inbound access to port 5432 for administrative GUI tools"
  default     = ""
}

