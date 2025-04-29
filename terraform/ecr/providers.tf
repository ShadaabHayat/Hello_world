locals {
  default_tags = {
    Purpose    = "AICore Terraform Bootstrap Roles"
    ManagedBy  = "Terraform"
    Ticket     = "AICORE-397"
    GithubRepo = "aicore-infra"
  }

}
provider "aws" {
  region = "us-east-1"

  default_tags {
    tags = merge(local.default_tags, { Environment = "Deployment" })
  }
}

provider "aws" {
  region = "us-east-1"
  alias  = "deployment"

  default_tags {
    tags = merge(local.default_tags, { Environment = "Deployment" })
  }
}