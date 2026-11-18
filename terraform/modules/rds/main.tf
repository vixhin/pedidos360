resource "aws_db_subnet_group" "this" {
  name        = "pedidos360-db-subnet-group-${var.environment}"
  subnet_ids  = var.subnet_ids
  description = "Subnet group for Pedidos360 PostgreSQL RDS across multiple availability zones"

  tags = {
    Name        = "pedidos360-db-subnet-group-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_security_group" "db_sg" {
  name        = "pedidos360-rds-sg-${var.environment}"
  description = "Security Group for Pedidos360 PostgreSQL RDS - Port 5432 isolated to EC2"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL access strictly from EC2 microservices"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.ec2_security_group_id]
  }

  dynamic "ingress" {
    for_each = var.allowed_admin_cidr != null && var.allowed_admin_cidr != "" ? [var.allowed_admin_cidr] : []
    content {
      description = "Optional PostgreSQL access from administrator IP"
      from_port   = 5432
      to_port     = 5432
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }

  egress {
    description = "Allow all outbound traffic"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "pedidos360-rds-sg-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_db_instance" "this" {
  identifier                 = "pedidos360-db-${var.environment}"
  engine                     = "postgres"
  engine_version             = var.engine_version
  instance_class             = var.instance_class
  allocated_storage          = var.allocated_storage
  max_allocated_storage      = var.max_allocated_storage
  storage_type               = "gp3"
  db_name                    = var.db_name
  username                   = var.db_username
  password                   = var.db_password
  port                       = 5432
  db_subnet_group_name       = aws_db_subnet_group.this.name
  vpc_security_group_ids     = [aws_security_group.db_sg.id]
  publicly_accessible        = var.publicly_accessible
  skip_final_snapshot        = true
  deletion_protection        = false
  multi_az                   = false
  auto_minor_version_upgrade = true
  backup_retention_period    = var.backup_retention_period
  apply_immediately          = true

  tags = {
    Name        = "pedidos360-db-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}
