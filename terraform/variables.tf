variable "author" {
  type        = string
  description = "Author Name"
}

variable "business_unit" {
  type        = string
  default     = "dps"
  description = "Business Unit"
}

variable "environment" {
  type        = string
  description = "Environment Name"
}

variable "optional_identifier" {
  type        = string
  default     = ""
  description = "Optional Identifier"
}


### -- VPC --

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR block"
  default     = "18.0.0.0/16"
}

variable "enable_dns_hostnames" {
  type        = bool
  description = "A boolean flag to enable/disable DNS hostnames in the VPC."
  default     = true
}

variable "enable_dns_support" {
  type        = bool
  description = "A boolean flag to enable/disable DNS support in the VPC. Defaults true."
  default     = true
}

variable "create_igw" {
  type        = bool
  description = "A boolean flag to create internet gateway in the VPC. Defaults true."
  default     = true
}

variable "enable_nat_gateway" {
  type        = bool
  description = "A boolean flag to create NAT gateway for private subnets. Defaults true."
  default     = true
}

variable "single_nat_gateway" {
  type        = bool
  description = "Enable only one NAT gateway. Defaults true."
  default     = true
}

## -- EKS --

variable "cluster_version" {
  type        = string
  description = "Kubernetes `<major>.<minor>` version to use for the EKS cluster (i.e.: `1.22`)"
  default     = "1.32"
}

variable "eks_access_users" {
  default = {
    faraz = {
      principal_arn = "arn:aws:iam::888507318922:user/faraz.mateen"
      policy        = "AmazonEKSClusterAdminPolicy"
    }
    shadaab = {
      principal_arn = "arn:aws:iam::888507318922:user/shadaab.sikandar"
      policy        = "AmazonEKSClusterAdminPolicy"
    }
  }
}