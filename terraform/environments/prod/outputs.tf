output "public_ip" {
  value       = module.elastic_ip.public_ip
  description = "Elastic IP public address"
}

output "public_dns" {
  value       = module.ec2.public_dns
  description = "AWS public DNS hostname"
}

output "db_endpoint" {
  value       = var.enable_rds ? module.rds[0].db_endpoint : null
  description = "RDS PostgreSQL endpoint (host:port)"
}

output "db_host" {
  value       = var.enable_rds ? module.rds[0].db_address : null
  description = "RDS PostgreSQL host address (configure DB_HOST in .env.production)"
}

output "db_port" {
  value       = var.enable_rds ? module.rds[0].db_port : null
  description = "RDS PostgreSQL port"
}

output "db_name" {
  value       = var.enable_rds ? module.rds[0].db_name : null
  description = "Default database name created in RDS"
}
