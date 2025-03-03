# Role ARN and Session variables (used as fallback)
variable "role_arn" {
  description = "Role ARN for cross-account access (used as fallback)"
  type        = string
  default     = ""
}

variable "external_id" {
  description = "External ID for secure cross-account role assumption"
  type        = string
  sensitive   = true
}

variable "session_name" {
  description = "Name of the STS session (used as fallback)"
  type        = string
  default     = "aicore_session"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}

variable "secrets_role_arn" {
  description = "Role ARN for secrets management account"
  type        = string
}

variable "secrets_region" {
  description = "Role ARN for secrets management account"
  type        = string
}

variable "state_region" {
  description = "state management account region"
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
