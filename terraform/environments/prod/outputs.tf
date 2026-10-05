output "public_ip" {
  value       = module.elastic_ip.public_ip
  description = "Elastic IP public "
}

output "public_dns" {
  value       = module.ec2.public_dns
  description = "AWS public DNS"
}
