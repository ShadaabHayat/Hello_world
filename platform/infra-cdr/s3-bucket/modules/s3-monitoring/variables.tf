variable "aws_region" {
  description = "AWS region where the S3 bucket is located"
  type        = string
}

variable "bucket_name" {
  description = "Name of the S3 bucket to monitor"
  type        = string
}

variable "dashboard_name" {
  description = "Name of the CloudWatch dashboard"
  type        = string
}

variable "alarm_actions" {
  description = "List of ARNs to notify when alarm triggers (SNS topics)"
  type        = list(string)
  default     = []
}

variable "metric_name" {
  description = "Name of the S3 bucket metric filter"
  type        = string
  default     = "EntireBucket"
}

variable "s3_4xx_errors_threshold" {
  description = "Threshold for S3 4XX errors per 5-minute period. Default is 25 (equivalent to 5 errors/minute)"
  type        = number
  default     = 25
}

variable "s3_5xx_errors_threshold" {
  description = "Threshold for S3 5XX errors per 5-minute period. Default is 5 (equivalent to 1 error/minute)"
  type        = number
  default     = 5
}

variable "first_byte_latency_threshold" {
  description = "Threshold for S3 first byte latency in milliseconds. Default is 500ms"
  type        = number
  default     = 500
}

variable "total_request_latency_threshold" {
  description = "Threshold for S3 total request latency in milliseconds. Default is 2000ms (2 seconds)"
  type        = number
  default     = 2000
}
