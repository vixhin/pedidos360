output "vpc_id" {
  value       = aws_vpc.main.id
  description = "ID of the created VPC"
}

output "subnet_id" {
  value       = aws_subnet.public.id
  description = "ID of the created primary public subnet"
}

output "secondary_subnet_id" {
  value       = aws_subnet.secondary.id
  description = "ID of the created secondary public subnet"
}

output "subnet_ids" {
  value       = [aws_subnet.public.id, aws_subnet.secondary.id]
  description = "List of public subnet IDs in the VPC"
}

output "db_subnet_ids" {
  value       = [aws_subnet.public.id, aws_subnet.secondary.id]
  description = "List of subnet IDs across distinct AZs for RDS Subnet Group"
}
