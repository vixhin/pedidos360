output "db_instance_id" {
  value       = aws_db_instance.this.id
  description = "ID of the RDS database instance"
}

output "db_endpoint" {
  value       = aws_db_instance.this.endpoint
  description = "Connection endpoint in the form host:port"
}

output "db_address" {
  value       = aws_db_instance.this.address
  description = "Host address of the RDS database (to configure DB_HOST in microservices)"
}

output "db_port" {
  value       = aws_db_instance.this.port
  description = "Port number of the RDS database"
}

output "db_name" {
  value       = aws_db_instance.this.db_name
  description = "Default database name created in RDS"
}

output "db_security_group_id" {
  value       = aws_security_group.db_sg.id
  description = "ID of the RDS security group"
}

