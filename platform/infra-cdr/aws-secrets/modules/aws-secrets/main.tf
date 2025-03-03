# Resource DB Credentials Secret
resource "aws_secretsmanager_secret" "resource_db_creds" {
  provider = aws.aws_secrets
  name        = "${var.environment}/aicore/rds-creds-resource"
  description = "Credentials for Resource Database"
}

resource "aws_secretsmanager_secret_version" "resource_db_creds" {
  provider = aws.aws_secrets
  secret_id = aws_secretsmanager_secret.resource_db_creds.id
  secret_string = jsonencode({
    postgres_user     = var.resource_db_username
    postgres_password = var.resource_db_password
    postgres_host     = var.resource_db_host
    postgres_port     = tonumber(var.resource_db_port)
    postgres_db       = var.resource_db_name
  })
}

# Auth DB Credentials Secret
resource "aws_secretsmanager_secret" "auth_db_creds" {
  provider = aws.aws_secrets
  name        = "${var.environment}/aicore/rds-creds-auth"
  description = "Credentials for Authentication Database"
}

resource "aws_secretsmanager_secret_version" "auth_db_creds" {
  provider = aws.aws_secrets
  secret_id = aws_secretsmanager_secret.auth_db_creds.id
  secret_string = jsonencode({
    postgres_user     = var.auth_db_username
    postgres_password = var.auth_db_password
    postgres_host     = var.auth_db_host
    postgres_port     = tonumber(var.auth_db_port)
    postgres_db       = var.auth_db_name
  })
}

# Role ARN Secret
resource "aws_secretsmanager_secret" "role_arn" {
  provider = aws.aws_secrets
  name        = "${var.environment}/aicore/s3-raw-bucket-assume-role-creds"
  description = "Role ARN for cross-account access"
}

resource "aws_secretsmanager_secret_version" "role_arn" {
  provider = aws.aws_secrets
  secret_id = aws_secretsmanager_secret.role_arn.id
  secret_string = jsonencode({
    sts_role_arn     = var.role_arn
    sts_external_id  = var.external_id
    sts_session_name = var.session_name
  })
}
