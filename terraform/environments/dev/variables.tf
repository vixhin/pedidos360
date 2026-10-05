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
