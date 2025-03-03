variable "bucket_name" {
  description = "Name of the S3 bucket"
  type        = string
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

variable "sts_session_name" {
  description = "Name of the STS session"
  type        = string
  default     = "aicore_session"
}
