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
      version = "6.47.0"
    }
  }

  required_version = ">= 1.12"
}
