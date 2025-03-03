output "bucket_access_role_arn" {
  description = "ARN of the IAM role for cross-account S3 bucket access"
  value       = aws_iam_role.bucket_access_role.arn
}

output "bucket_name" {
  description = "Name of the created S3 bucket"
  value       = aws_s3_bucket.ingestion_engine_data.id
}

output "metric_name" {
  description = "Name of the S3 bucket metric filter"
  value       = aws_s3_bucket_metric.ingestion_engine_data.name
}

output "external_id" {
  description = "External ID used for secure cross-account role assumption"
  value       = var.external_id
  sensitive   = true
}

output "session_name" {
  description = "Name of the session"
  value       = var.sts_session_name
}
