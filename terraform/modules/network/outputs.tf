output "vpc_id" {
  value       = data.aws_vpc.default.id
  description = "ID of the target VPC"
}

output "subnet_ids" {
  value       = data.aws_subnets.default.ids
  description = "List of public subnet IDs in the VPC"
}
