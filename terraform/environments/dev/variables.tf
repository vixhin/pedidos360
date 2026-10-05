variable "aws_region" {
  type        = string
  description = "AWS Region to deploy resources"
  default     = "us-east-1"
}

variable "ami_id" {
  type        = string
  description = "AMI ID for Ubuntu 22.04 LTS"
  default     = "ami-0c7217cdde317cfec" # Example Ubuntu 22.04 LTS us-east-1
}

variable "instance_type" {
  type        = string
  description = "EC2 instance type"
  default     = "t3.medium"
}

variable "key_name" {
  type        = string
  description = "Optional SSH key pair name"
  default     = ""
}

variable "allowed_ssh_cidr" {
  type        = string
  description = "CIDR block allowed for SSH access"
  default     = "0.0.0.0/0"
}

variable "environment" {
  type        = string
  description = "Environment name"
  default     = "dev"
}
