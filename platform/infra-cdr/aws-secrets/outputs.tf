# AWS Secrets outputs
output "resource_db_secret_name" {
  description = "Name of the resource database credentials secret"
  value       = module.aws_secrets.resource_db_secret_name
}

output "auth_db_secret_name" {
  description = "Name of the auth database credentials secret"
  value       = module.aws_secrets.auth_db_secret_name
}

output "role_arn_secret_name" {
  description = "Name of the role ARN secret"
  value       = module.aws_secrets.role_arn_secret_name
}
