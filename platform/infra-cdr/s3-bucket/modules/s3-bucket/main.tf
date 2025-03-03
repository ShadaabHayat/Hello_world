resource "aws_s3_bucket" "ingestion_engine_data" {
  bucket = var.bucket_name
}

# Enable versioning
resource "aws_s3_bucket_versioning" "ingestion_engine_data" {
  bucket = aws_s3_bucket.ingestion_engine_data.id
  versioning_configuration {
    status = "Suspended"
  }
}

# Block all public access
resource "aws_s3_bucket_public_access_block" "ingestion_engine_data" {
  bucket = aws_s3_bucket.ingestion_engine_data.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true

  depends_on = [aws_s3_bucket.ingestion_engine_data]
}

# Enable server-side encryption by default
resource "aws_s3_bucket_server_side_encryption_configuration" "ingestion_engine_data" {
  bucket = aws_s3_bucket.ingestion_engine_data.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }

  depends_on = [aws_s3_bucket.ingestion_engine_data]
}

# Enable S3 request metrics
resource "aws_s3_bucket_metric" "ingestion_engine_data" {
  bucket = aws_s3_bucket.ingestion_engine_data.id
  name   = "EntireBucket"

  filter {
    prefix = ""  # Empty prefix means entire bucket
  }
}

# Create IAM role for cross-account access
resource "aws_iam_role" "bucket_access_role" {
  name = "${var.bucket_name}-access-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          AWS = [for account_id in var.trusted_account_ids : "arn:aws:iam::${account_id}:root"]
        }
        Condition = {
          StringEquals = {
            "sts:ExternalId" = var.external_id
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket.ingestion_engine_data]
}

# Create IAM policy for S3 bucket access
resource "aws_iam_policy" "bucket_access_policy" {
  name        = "${var.bucket_name}-access-policy"
  description = "Policy for full S3 bucket access"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:*"
        ]
        Resource = [
          aws_s3_bucket.ingestion_engine_data.arn,
          "${aws_s3_bucket.ingestion_engine_data.arn}/*"
        ]
      }
    ]
  })

  depends_on = [aws_s3_bucket.ingestion_engine_data]
}

# Attach the policy to the role
resource "aws_iam_role_policy_attachment" "bucket_access_attachment" {
  policy_arn = aws_iam_policy.bucket_access_policy.arn
  role       = aws_iam_role.bucket_access_role.name

  depends_on = [
    aws_iam_role.bucket_access_role,
    aws_iam_policy.bucket_access_policy
  ]
}
