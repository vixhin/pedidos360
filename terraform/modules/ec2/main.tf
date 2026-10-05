resource "aws_instance" "this" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  key_name               = var.key_name != "" ? var.key_name : null
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [var.security_group_id]

  root_block_device {
    volume_size           = var.root_volume_size
    volume_type           = var.root_volume_type
    delete_on_termination = true
  }

  user_data = <<-EOF
              #!/bin/bash
              set -e
              echo "=== Starting Pedidos360 Minimal Bootstrap ==="
              apt-get update -y
              apt-get install -y apt-transport-https ca-certificates curl software-properties-common git

              # Install Docker if not present
              if ! command -v docker &> /dev/null; then
                curl -fsSL https://get.docker.com -o get-docker.sh
                sh get-docker.sh
                usermod -aG docker ubuntu || true
              fi

              # Install Docker Compose Plugin
              apt-get install -y docker-compose-plugin || true

              # Prepare deployment directory
              mkdir -p /opt/pedidos360
              chown -R ubuntu:ubuntu /opt/pedidos360
              echo "=== Pedidos360 Bootstrap Complete ==="
              EOF

  tags = {
    Name        = "pedidos360-ec2-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
