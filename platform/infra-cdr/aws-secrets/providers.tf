terraform {
  backend "s3" {}
}

# Provider for state management (Account 1)
provider "aws" {
  region = var.state_region
}

# Provider for secrets management (Account 2)
provider "aws" {
  region = var.secrets_region
  alias  = "aws_secrets"

  assume_role {
    role_arn = var.secrets_role_arn
  }
}
