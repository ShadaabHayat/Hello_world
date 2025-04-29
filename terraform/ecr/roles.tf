resource "aws_iam_role" "deployment_terraform_role" {
  provider = aws.deployment
  name     = "deployment-terraform-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Principal = {
          Federated = "arn:aws:iam::888507318922:oidc-provider/token.actions.githubusercontent.com"
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = [
              "repo:extremenetworks/aicore-*",
              "repo:extremenetworks/ContentLake:*",
              "repo:ShadaabHayat/Hello_world:*"
            ]
          }
        }
      }
    ]
  })
}


# Policy for Deployment Role
resource "aws_iam_policy" "deployment_terraform_policy" {
  provider    = aws.deployment
  name        = "deployment-terraform-policy"
  description = "Policy for management role to manage S3 and Lambda"

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow"
        Action = "s3:ListBucket"
        Resource = [
          "arn:aws:s3:::extr-aicore-deployment-tf-state-us-east-1",
          "arn:aws:s3:::extr-aicore-sandbox-tf-state-us-east-1"
        ]
      },
      {
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject"]
        Resource = [
          "arn:aws:s3:::extr-aicore-deployment-tf-state-us-east-1/*",
          "arn:aws:s3:::extr-aicore-sandbox-tf-state-us-east-1/*"
        ]
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:DescribeTable",
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:DeleteItem"
        ],
        Resource = "arn:aws:dynamodb:us-east-1:438465127823:table/terraform-state-lock"
      },
      {
        Effect = "Allow"
        Action = [
          "sts:GetServiceBearerToken",
          "codeartifact:*",
          "ecr:*"
        ]
        Resource = "*"
      }
    ]
  })
}


# Attach policy to deployment role
resource "aws_iam_role_policy_attachment" "deployment_terraform_role_policy_attachment" {
  provider   = aws.deployment
  role       = aws_iam_role.deployment_terraform_role.name
  policy_arn = aws_iam_policy.deployment_terraform_policy.arn
}

# resource "aws_iam_openid_connect_provider" "githubaction" {
#   provider       = aws.deployment
#   client_id_list = ["sts.amazonaws.com"]
# 
#   thumbprint_list = ["d89e3bd43d5d909b47a18977aa9d5ce36cee184c"]
#   url             = "https://token.actions.githubusercontent.com"
# }
