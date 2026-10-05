terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

module "network" {
  source      = "../../modules/network"
  environment = var.environment
}

module "security_group" {
  source           = "../../modules/security_group"
  vpc_id           = module.network.vpc_id
  allowed_ssh_cidr = var.allowed_ssh_cidr
  environment      = var.environment
}

module "ec2" {
  source            = "../../modules/ec2"
  ami_id            = var.ami_id
  instance_type     = var.instance_type
  key_name          = var.key_name
  subnet_id         = module.network.subnet_id
  security_group_id = module.security_group.security_group_id
  environment       = var.environment
}

module "elastic_ip" {
  source      = "../../modules/elastic_ip"
  instance_id = module.ec2.instance_id
  environment = var.environment
}

locals {
  public_domain = "${replace(module.elastic_ip.public_ip, ".", "-")}.sslip.io"
  public_url    = "https://${local.public_domain}"
}

module "api_gateway" {
  source        = "../../modules/api_gateway"
  public_domain = local.public_domain
  environment   = var.environment
}
