output "api_gateway_id" {
  value       = aws_apigatewayv2_api.this.id
  description = "ID of the API Gateway"
}

output "api_gateway_endpoint" {
  value       = aws_apigatewayv2_api.this.api_endpoint
  description = "Public HTTP endpoint of the API Gateway"
}
