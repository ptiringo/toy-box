terraform {
  cloud {
    organization = "ptiringo-tech"

    workspaces {
      project = "toy-box-project"
      name    = "toy-box"
    }
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "7.44.0"
    }
    prisma-postgres = {
      source  = "prisma/prisma-postgres"
      version = "~> 0.2.0"
    }
  }

  required_version = ">= 1.15"
}
