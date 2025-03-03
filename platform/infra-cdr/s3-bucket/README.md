# S3 Bucket with Cross-Account Access Infrastructure

This Terraform project creates an S3 bucket with cross-account access capabilities, designed for secure data sharing between AWS accounts.

## Infrastructure Components

1. **S3 Bucket**
   - Versioning: Disabled (can be enabled if needed)
   - Public Access: Blocked
   - Server-side encryption: Enabled (AES-256)

2. **IAM Role & Policies**
   - Cross-account access role
   - Full S3 access policy (`s3:*`) for the specific bucket
   - Secure access using External ID

## Prerequisites

1. AWS CLI installed and configured
2. Terraform installed (version 0.12 or later)
3. AWS credentials with permissions to create:
   - S3 buckets
   - IAM roles
   - IAM policies

## Configuration

### Required Variables

Create a `terraform.tfvars` file with the following variables:

```hcl
bucket_name        = "your-bucket-name"
trusted_account_id = "123456789012"    # AWS account ID that needs access
external_id       = "your-external-id" # Secret string for secure access
sts_session_name  = "session-name"     # Name for the STS session
```

> Note: The `terraform.tfvars` file is gitignored for security purposes.

## Usage

1. **Initialize Terraform**

   ```bash
   terraform init
   ```

2. **Review the Plan**

   ```bash
   terraform plan
   ```

3. **Apply the Configuration**

   ```bash
   terraform apply
   ```

4. **View Outputs**
   After successful application, you'll get these outputs:

   ```bash
   terraform output          # Shows all outputs
   terraform output -json    # Shows outputs in JSON format
   ```

   Important outputs:
   - `bucket_access_role_arn`: ARN of the IAM role for cross-account access
   - `external_id`: The External ID needed for role assumption (sensitive)
   - `session_name`: The STS session name

## Cross-Account Access Setup

For the trusted account to access the S3 bucket:

1. Use the role ARN and External ID from the outputs
2. Assume the role using AWS STS with the provided External ID
3. Use the temporary credentials to access the S3 bucket

Example AWS CLI command for assuming the role:

```bash
aws sts assume-role \
  --role-arn <bucket_access_role_arn> \
  --external-id <external_id> \
  --role-session-name <session_name>
```

## Security Features

1. **Public Access Blocking**: All public access to the bucket is blocked
2. **Server-Side Encryption**: All objects are automatically encrypted using AES-256
3. **External ID**: Required for cross-account role assumption
4. **Specific Resource Access**: IAM permissions are limited to this specific bucket

## Clean Up

To destroy the infrastructure:

```bash
terraform destroy
```

> Warning: This will delete the S3 bucket and all its contents. Make sure to backup any important data.

## Files Structure

- `s3-bucket.tf`: Main infrastructure configuration
- `variables.tf`: Variable definitions
- `outputs.tf`: Output definitions
- `terraform.tfvars`: Variable values (gitignored)
- `.gitignore`: Git ignore patterns for Terraform files
