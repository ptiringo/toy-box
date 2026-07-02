terraform {
  required_version = ">= 1.12"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 7.36.0"
    }
    prisma-postgres = {
      source  = "prisma/prisma-postgres"
      version = "~> 0.2.0"
    }
  }
}
