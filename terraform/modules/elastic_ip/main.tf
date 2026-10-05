resource "aws_eip" "this" {
  domain = "vpc"

  tags = {
    Name        = "pedidos360-eip-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_eip_association" "eip_assoc" {
  instance_id   = var.instance_id
  allocation_id = aws_eip.this.id
}
