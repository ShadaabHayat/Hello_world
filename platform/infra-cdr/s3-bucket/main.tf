terraform {
}

module "s3_bucket" {
  source = "./modules/s3-bucket"
  bucket_name        = var.bucket_name
  trusted_account_ids = var.trusted_account_ids
  external_id        = var.external_id
}

# Get current AWS region
data "aws_region" "current" {}

module "sns_topic" {
  source = "./modules/sns-topic"

  topic_name      = "${var.bucket_name}-monitoring"
  email_endpoints = var.notification_emails
}

module "s3_monitoring" {
  source = "./modules/s3-monitoring"

  aws_region     = data.aws_region.current.name
  bucket_name    = module.s3_bucket.bucket_name
  dashboard_name = var.dashboard_name
  metric_name    = module.s3_bucket.metric_name

  # Add SNS topic ARN for notifications
  alarm_actions = [module.sns_topic.topic_arn]
}
