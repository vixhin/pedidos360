data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name        = "pedidos360-vpc-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = var.availability_zone
  map_public_ip_on_launch = true

  tags = {
    Name        = "pedidos360-public-subnet-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_subnet" "secondary" {
  vpc_id     = aws_vpc.main.id
  cidr_block = var.secondary_subnet_cidr
  availability_zone = var.secondary_availability_zone != null ? var.secondary_availability_zone : (
    length(data.aws_availability_zones.available.names) > 1 ? (
      data.aws_availability_zones.available.names[0] != aws_subnet.public.availability_zone ?
      data.aws_availability_zones.available.names[0] :
      data.aws_availability_zones.available.names[1]
    ) : null
  )
  map_public_ip_on_launch = true

  tags = {
    Name        = "pedidos360-secondary-subnet-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name        = "pedidos360-igw-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name        = "pedidos360-public-rt-${var.environment}"
    Project     = "Pedidos360"
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "secondary" {
  subnet_id      = aws_subnet.secondary.id
  route_table_id = aws_route_table.public.id
}
