output "public_ip" {
  value       = aws_eip.this.public_ip
  description = "Public Elastic IP address"
}

output "eip_allocation_id" {
  value       = aws_eip.this.id
  description = "Allocation ID of the Elastic IP"
}
