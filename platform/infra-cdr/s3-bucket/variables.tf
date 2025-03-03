variable "bucket_name" {
  description = "Name of the S3 bucket"
  type        = string
  default     = "uztna-data"
}

variable "trusted_account_ids" {
  description = "List of AWS account IDs that are allowed to assume the role"
  type        = list(string)
}

variable "external_id" {
  description = "External ID for secure cross-account role assumption"
  type        = string
  sensitive   = true
}

variable "notification_emails" {
  description = "List of email addresses to receive S3 monitoring alerts"
  type        = list(string)
  default     = []
}

variable "dashboard_name" {
  description = "Name of the CloudWatch dashboard"
  type        = string
  default     = "IngestionEngineS3Dashboard"
}
