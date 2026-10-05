variable "aws_region" {
  type        = string
  description = "AWS Region to deploy production resources"
  default     = "us-east-1"
}

variable "ami_id" {
  type        = string
  description = "AMI ID for Ubuntu 22.04 LTS"
  default     = "ami-0c7217cdde317cfec"
}

variable "instance_type" {
  type        = string
  description = "EC2 instance type for production"
  default     = "t3.medium"
}

variable "key_name" {
  type        = string
  description = "Optional SSH key pair name"
  default     = ""
}

variable "allowed_ssh_cidr" {
  type        = string
  description = "Restricted CIDR block allowed for SSH access"
  default     = "190.0.0.1/32"
}

variable "environment" {
  type        = string
  description = "Environment name"
  default     = "prod"
}
