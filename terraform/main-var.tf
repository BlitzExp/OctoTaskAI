//Copyright (c) 2022 Oracle and/or its affiliates.
//Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
variable "ociTenancyOcid" {}
variable "ociUserOcid" {}
variable "ociCompartmentOcid" {}
variable "ociRegionIdentifier" {}

variable "sshPublicKey" {}

variable "projectName" {}

variable "vcnCidr" {
  description = "CIDR principal de la VCN"
  type        = string
  default     = "10.0.0.0/16"
}

variable "publicSubnetCidr" {
  description = "CIDR de la subnet pública"
  type        = string
  default     = "10.0.1.0/24"
}

variable "privateSubnetCidr" {
  description = "CIDR de la subnet privada"
  type        = string
  default     = "10.0.2.0/24"
}

variable "kubernetesVersion" {
  description = "Versión de Kubernetes para OKE"
  type        = string
  default     = "v1.30.1"
}

variable "nodeShape" {
  description = "Shape de los nodos OKE"
  type        = string
  default     = "VM.Standard.E3.Flex"
}

variable "nodeOcpus" {
  description = "OCPUs por nodo"
  type        = number
  default     = 2
}

variable "nodeMemoryGbs" {
  description = "Memoria por nodo en GB"
  type        = number
  default     = 16
}

variable "nodeCount" {
  description = "Número inicial de nodos"
  type        = number
  default     = 3
}

variable "existingOkeClusterId" {
  description = "OCID del cluster OKE existente"
  type        = string
}

variable "existingNodePoolSubnetId" {
  description = "OCID de la subnet existente para el nuevo node pool"
  type        = string
}