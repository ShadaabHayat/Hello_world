variable "topic_name" {
  description = "Name of the SNS topic"
  type        = string
}

variable "email_endpoints" {
  description = "List of email addresses to subscribe to the SNS topic"
  type        = list(string)
  default     = []
}
