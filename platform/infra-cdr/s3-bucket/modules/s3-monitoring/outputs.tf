output "dashboard_arn" {
  description = "ARN of the CloudWatch dashboard"
  value       = aws_cloudwatch_dashboard.s3_monitoring.dashboard_arn
}

output "alarm_arns" {
  description = "Map of alarm names to their ARNs"
  value = {
    "4xx_errors"           = aws_cloudwatch_metric_alarm.s3_4xx_errors.arn
    "5xx_errors"           = aws_cloudwatch_metric_alarm.s3_5xx_errors.arn
    "first_byte_latency"   = aws_cloudwatch_metric_alarm.first_byte_latency_alarm.arn
    "total_request_latency" = aws_cloudwatch_metric_alarm.total_request_latency_alarm.arn
  }
}
