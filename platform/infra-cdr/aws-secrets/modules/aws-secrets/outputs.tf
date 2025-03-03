output "resource_db_secret_name" {
  description = "Name of the resource database credentials secret"
  value       = aws_secretsmanager_secret.resource_db_creds.name
}

output "auth_db_secret_name" {
  description = "Name of the auth database credentials secret"
  value       = aws_secretsmanager_secret.auth_db_creds.name
}

output "role_arn_secret_name" {
  description = "Name of the role ARN secret"
  value       = aws_secretsmanager_secret.role_arn.name
}
