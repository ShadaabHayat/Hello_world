resource "aws_cloudwatch_dashboard" "s3_monitoring" {
  dashboard_name = var.dashboard_name

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "BucketSizeBytes", "BucketName", var.bucket_name, "StorageType", "StandardStorage", { label = "Total Storage Size" }]
          ]
          period = 600 // collect this metric values every 10 minutes
          stat   = "Average"
          region = var.aws_region
          title  = "Bucket Size"
          view   = "timeSeries"
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "NumberOfObjects", "BucketName", var.bucket_name, "StorageType", "AllStorageTypes", { label = "Total No. of files" }]
          ]
          period = 600 // collect this metric values every 10 minutes
          stat   = "Average"
          region = var.aws_region
          title  = "Object Count"
          view   = "timeSeries"
          yAxis = {
            left = {
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "AllRequests", "BucketName", var.bucket_name, "FilterId", var.metric_name],
            [".", "GetRequests", ".", ".", "FilterId", var.metric_name],
            [".", "PutRequests", ".", ".", "FilterId", var.metric_name],
            [".", "DeleteRequests", ".", ".", "FilterId", var.metric_name],
            [".", "HeadRequests", ".", ".", "FilterId", var.metric_name],
            [".", "ListRequests", ".", ".", "FilterId", var.metric_name],
            [".", "PostRequests", ".", ".", "FilterId", var.metric_name]
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "Request Count by Type"
          view   = "timeSeries",
          yAxis = {
            left = {
              min = 0
            },
            right = {
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "4xxErrors", "BucketName", var.bucket_name, "FilterId", var.metric_name, { label = "4XX Errors" }],
            [".", "5xxErrors", ".", ".", "FilterId", var.metric_name, { label = "5XX Errors" }]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "Errors"
          view   = "timeSeries",
          yAxis = {
            left = {
              min = 0
            }
          }
          annotations = {
            horizontal : [
              {
                value = 5
                label = "4XX Error Threshold (5/min)"
                color = "#ff9900"
              },
              {
                value = 1
                label = "5XX Error Threshold (1/min)"
                color = "#ff0000"
              }
            ]
          }
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "BytesDownloaded", "BucketName", var.bucket_name, "FilterId", var.metric_name],
            [".", "BytesUploaded", ".", ".", "FilterId", var.metric_name]
          ]
          period = 300
          stat   = "Sum"
          region = var.aws_region
          title  = "Data Transfer"
          view   = "timeSeries",
          yAxis = {
            left = {
              min = 0
            },
            right = {
              min = 0
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "FirstByteLatency", "BucketName", var.bucket_name, "FilterId", var.metric_name, { label = "First Byte Latency" }]
          ]
          period = 600 // collect this metric values every 10 minutes
          stat   = "Average"
          region = var.aws_region
          title  = "S3 First Byte Latency"
          view   = "timeSeries"
          yAxis = {
            left = {
              min = 0
            }
          }
          annotations = {
            horizontal : [
              {
                value = 500
                label = "Warning Threshold (500ms)"
                color = "#ff0000"
              }
            ]
          }
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 18
        width  = 12
        height = 6
        properties = {
          metrics = [
            ["AWS/S3", "TotalRequestLatency", "BucketName", var.bucket_name, "FilterId", var.metric_name, { label = "Total Request Latency" }]
          ]
          period = 600 // collect this metric values every 10 minutes
          stat   = "Average"
          region = var.aws_region
          title  = "S3 Total Request Latency"
          view   = "timeSeries"
          yAxis = {
            left = {
              min = 0
            }
          }
          annotations = {
            horizontal : [
              {
                value = 2000
                label = "Warning Threshold (2000ms)"
                color = "#ff0000"
              }
            ]
          }
        }
      }
    ]
  })
}

resource "aws_cloudwatch_metric_alarm" "s3_4xx_errors" {
  alarm_name          = "${var.bucket_name}-4xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"  // Trigger if errors persist for 2 periods
  metric_name         = "4xxErrors"
  namespace           = "AWS/S3"
  period             = "300"  // Check every 5 minutes
  statistic          = "Sum"  // Use Sum for error counts
  threshold          = var.s3_4xx_errors_threshold
  alarm_description  = "This metric monitors S3 4XX errors and alerts when they exceed ${var.s3_4xx_errors_threshold / 5} per minute"
  treat_missing_data = "notBreaching"

  dimensions = {
    BucketName = var.bucket_name
    FilterId   = var.metric_name
  }

  alarm_actions = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "s3_5xx_errors" {
  alarm_name          = "${var.bucket_name}-5xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"  // Trigger immediately for 5xx errors
  metric_name         = "5xxErrors"
  namespace           = "AWS/S3"
  period             = "300"  // Check every 5 minutes
  statistic          = "Sum"  // Use Sum for error counts
  threshold          = var.s3_5xx_errors_threshold
  alarm_description  = "This metric monitors S3 5XX errors and alerts when they exceed ${var.s3_5xx_errors_threshold / 5} per minute"
  treat_missing_data = "notBreaching"

  dimensions = {
    BucketName = var.bucket_name
    FilterId   = var.metric_name
  }

  alarm_actions = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "first_byte_latency_alarm" {
  alarm_name          = "${var.bucket_name}-first-byte-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"  // Trigger if high latency persists for 3 periods
  metric_name         = "FirstByteLatency"
  namespace           = "AWS/S3"
  period             = "300"  // Check every 5 minutes
  statistic          = "Average"
  threshold          = var.first_byte_latency_threshold
  alarm_description  = "This metric monitors S3 first byte latency and alerts when it exceeds ${var.first_byte_latency_threshold}ms"
  treat_missing_data = "ignore"

  dimensions = {
    BucketName = var.bucket_name
    FilterId   = var.metric_name
  }

  alarm_actions = var.alarm_actions
}

resource "aws_cloudwatch_metric_alarm" "total_request_latency_alarm" {
  alarm_name          = "${var.bucket_name}-total-request-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"  // Trigger if high latency persists for 3 periods
  metric_name         = "TotalRequestLatency"
  namespace           = "AWS/S3"
  period             = "300"  // Check every 5 minutes
  statistic          = "Average"
  threshold          = var.total_request_latency_threshold
  alarm_description  = "This metric monitors S3 total request latency and alerts when it exceeds ${var.total_request_latency_threshold}ms"
  treat_missing_data = "ignore"

  dimensions = {
    BucketName = var.bucket_name
    FilterId   = var.metric_name
  }

  alarm_actions = var.alarm_actions
}
