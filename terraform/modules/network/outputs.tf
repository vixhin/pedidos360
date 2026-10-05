output "vpc_id" {
  value       = aws_vpc.main.id
  description = "ID of the created VPC"
}

output "subnet_id" {
  value       = aws_subnet.public.id
  description = "ID of the created public subnet"
}

output "subnet_ids" {
  value       = [aws_subnet.public.id]
  description = "List of public subnet IDs in the VPC"
}
