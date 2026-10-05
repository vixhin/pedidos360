resource "aws_security_group" "ec2_sg" {
  name        = "pedidos360-ec2-sg-${var.environment}"
  description = "Security Group for Pedidos360 EC2 instance - Only HTTP, HTTPS, and SSH allowed from Internet"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP web traffic"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS web traffic and Caddy TLS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Restricted SSH administration"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.allowed_ssh_cidr]
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "pedidos360-ec2-sg-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
