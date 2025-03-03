resource "aws_sns_topic" "s3_monitoring" {
  name = var.topic_name
}

resource "aws_sns_topic_policy" "s3_monitoring" {
  arn = aws_sns_topic.s3_monitoring.arn

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudWatchAlarms"
        Effect = "Allow"
        Principal = {
          Service = "cloudwatch.amazonaws.com"
        }
        Action   = "SNS:Publish"
        Resource = aws_sns_topic.s3_monitoring.arn
      }
    ]
  })
}

# Optional: Add email subscription if email is provided
resource "aws_sns_topic_subscription" "email" {
  count     = length(var.email_endpoints)
  topic_arn = aws_sns_topic.s3_monitoring.arn
  protocol  = "email"
  endpoint  = var.email_endpoints[count.index]
}
