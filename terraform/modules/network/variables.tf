# Variables for network module (using default VPC by default)
variable "vpc_id" {
  type        = string
  description = "Optional custom VPC ID. If empty, default VPC is used."
  default     = ""
}
