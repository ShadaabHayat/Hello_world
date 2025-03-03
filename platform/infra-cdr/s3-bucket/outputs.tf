output "bucket_access_role_arn" {
  description = "ARN of the IAM role for cross-account S3 bucket access"
  value       = module.s3_bucket.bucket_access_role_arn
}

output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = module.s3_bucket.bucket_name
}

output "external_id" {
  description = "External ID used for secure cross-account role assumption"
  value       = module.s3_bucket.external_id
  sensitive   = true
}

output "session_name" {
  description = "Name of the session"
  value       = module.s3_bucket.session_name
}
