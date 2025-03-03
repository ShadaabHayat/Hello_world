module "aws_secrets" {
  source = "./modules/aws-secrets"
  providers = {
    aws.aws_secrets = aws.aws_secrets
  }

  environment = var.environment

  # Resource DB credentials
  resource_db_username = var.resource_db_username
  resource_db_password = var.resource_db_password
  resource_db_host     = var.resource_db_host
  resource_db_name     = var.resource_db_name
  resource_db_port     = var.resource_db_port

  # Auth DB credentials
  auth_db_username = var.auth_db_username
  auth_db_password = var.auth_db_password
  auth_db_host     = var.auth_db_host
  auth_db_name     = var.auth_db_name
  auth_db_port     = var.auth_db_port

  # Role ARN (either from S3 bucket module or from variables)
  role_arn     = var.role_arn
  external_id  = var.external_id
  session_name = var.session_name
}
