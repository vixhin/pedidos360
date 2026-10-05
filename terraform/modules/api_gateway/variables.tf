variable "public_domain" {
  type        = string
  description = "Public sslip.io domain of the Caddy/BFF deployment (e.g. 3-92-44-37.sslip.io)"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, prod)"
  default     = "dev"
}
