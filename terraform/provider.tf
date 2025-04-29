# Supports Terraform 0.13+
# PROVIDER
terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = ">= 5.81"
    }
  }
}


# CONFIGURATION OPTIONS
provider "aws" {
  region  = "us-east-2"
}