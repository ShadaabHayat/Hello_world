resource "aws_ecr_repository" "repos" {
  for_each = toset(var.repositories)
  name     = each.value
  image_scanning_configuration {
    scan_on_push = true
  }
  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_repository_policy" "repo_policy" {
  for_each   = aws_ecr_repository.repos
  repository = each.value.name

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Sid       = "AllowPushPull",
        Effect    = "Allow",
        Principal = { "AWS" = [for account in values(var.allowed_accounts) : "arn:aws:iam::${account}:root"] },
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
      }
    ]
  })
}

variable "repositories" {
  description = "List of ECR repository names to create"
  type        = list(string)
  default     = ["aicore/kafka-connect-custom", "aicore/k6-load-testing"]
}

variable "allowed_accounts" {
  description = "Map of environment names to AWS account IDs allowed to push and pull images"
  type        = map(string)
  default     = { "dev" : "442426878685", "new-sandbox" : "302263043826", "old-sandbox" : "533267427020", "stage" : "039612843683", "prod" : "122610481701" }
}