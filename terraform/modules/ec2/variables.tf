variable "ami_id" {
  type        = string
  description = "AMI ID for Ubuntu 22.04 LTS or compatible Linux. If empty, the latest official Ubuntu 22.04 LTS AMI is resolved automatically."
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

variable "subnet_id" {
  type        = string
  description = "Subnet ID where instance will be launched"
}

variable "security_group_id" {
  type        = string
  description = "Security Group ID for the instance"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, prod)"
  default     = "dev"
}

variable "root_volume_size" {
  type        = number
  description = "Size of the root EBS volume in GB"
  default     = 30
}

variable "root_volume_type" {
  type        = string
  description = "Type of the root EBS volume"
  default     = "gp3"
}
