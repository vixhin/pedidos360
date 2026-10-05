output "instance_id" {
  value       = aws_instance.this.id
  description = "ID of the created EC2 instance"
}

output "public_dns" {
  value       = aws_instance.this.public_dns
  description = "AWS native public DNS name of the EC2 instance"
}

output "private_ip" {
  value       = aws_instance.this.private_ip
  description = "Private IP address of the EC2 instance"
}
