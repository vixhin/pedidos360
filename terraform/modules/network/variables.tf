variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the custom VPC"
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr" {
  type        = string
  description = "CIDR block for the primary public subnet"
  default     = "10.0.1.0/24"
}

variable "secondary_subnet_cidr" {
  type        = string
  description = "CIDR block for the secondary public subnet (required for multi-AZ RDS subnet groups)"
  default     = "10.0.2.0/24"
}

variable "availability_zone" {
  type        = string
  description = "Optional availability zone for the primary subnet"
  default     = null
}

variable "secondary_availability_zone" {
  type        = string
  description = "Optional availability zone for the secondary subnet"
  default     = null
}

variable "environment" {
  type        = string
  description = "Deployment environment (dev, prod)"
  default     = "dev"
}
