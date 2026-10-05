variable "public_domain" {
  type        = string
  description = "Public sslip.io domain of the Caddy/BFF deployment (e.g. 3-92-44-37.sslip.io)"
}

variable "environment" {
  type        = string
  description = "Environment name (dev, prod)"
  default     = "dev"
}

variable "throttling_burst_limit" {
  type        = number
  description = "Maximum burst limit for API Gateway requests"
  default     = 200
}

variable "throttling_rate_limit" {
  type        = number
  description = "Maximum steady-state rate limit for API Gateway requests per second"
  default     = 100
}
