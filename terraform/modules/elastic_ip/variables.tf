variable "instance_id" {
  type        = string
  description = "EC2 instance ID to attach the Elastic IP to"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, prod)"
  default     = "dev"
}
