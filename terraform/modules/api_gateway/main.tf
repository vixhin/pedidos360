resource "aws_apigatewayv2_api" "this" {
  name          = "pedidos360-api-gateway-${var.environment}"
  protocol_type = "HTTP"
  description   = "API Gateway integration for Pedidos360 BFF (${var.environment})"

  tags = {
    Name        = "pedidos360-api-gateway-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_apigatewayv2_integration" "bff_proxy" {
  api_id                 = aws_apigatewayv2_api.this.id
  integration_type       = "HTTP_PROXY"
  integration_uri        = "https://${var.public_domain}/api/bff/{proxy}"
  integration_method     = "ANY"
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "bff_route" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/bff/{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.bff_proxy.id}"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true
}
