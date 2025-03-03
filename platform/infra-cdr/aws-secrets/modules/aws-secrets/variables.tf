variable "environment" {
  description = "Environment name (e.g., dev, prod)"
  type        = string
}

# Resource DB Variables
variable "resource_db_username" {
  description = "Username for the resource database"
  type        = string
  sensitive   = true
}

variable "resource_db_password" {
  description = "Password for the resource database"
  type        = string
  sensitive   = true
}

variable "resource_db_host" {
  description = "Host for the resource database"
  type        = string
}

variable "resource_db_port" {
  description = "Port for the resource database"
  type        = string
  default     = "5432"
}

variable "resource_db_name" {
  description = "Database name for the resource database"
  type        = string
}

# Auth DB Variables
variable "auth_db_username" {
  description = "Username for the auth database"
  type        = string
  sensitive   = true
}

variable "auth_db_password" {
  description = "Password for the auth database"
  type        = string
  sensitive   = true
}

variable "auth_db_host" {
  description = "Host for the auth database"
  type        = string
}

variable "auth_db_port" {
  description = "Port for the auth database"
  type        = string
  default     = "5432"
}

variable "auth_db_name" {
  description = "Database name for the auth database"
  type        = string
}

# Role ARN Variables
variable "role_arn" {
  description = "Role ARN for cross-account access"
  type        = string
}

variable "external_id" {
  description = "External ID for cross-account role assumption"
  type        = string
  sensitive   = true
}

variable "session_name" {
  description = "Name of the STS session"
  type        = string
  default     = "aicore_session"
}
