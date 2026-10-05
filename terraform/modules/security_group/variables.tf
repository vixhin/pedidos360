variable "vpc_id" {
  type        = string
  description = "VPC ID where the Security Group will be created"
}

variable "allowed_ssh_cidr" {
  type        = string
  description = "CIDR block allowed for SSH access (e.g. 190.x.x.x/32 or 0.0.0.0/0 for dev)"
  default     = "0.0.0.0/0"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, prod)"
  default     = "dev"
}
