terraform {
  required_providers{
    oci = {
      source = "hashicorp/oci"
      version = ">= 7.0.0"
    }
  }
}
provider "oci"{
  region = var.ociRegionIdentifier
}