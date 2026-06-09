output "node_pool_id" {
  description = "OCID del node pool de OctoTaskAI"
  value       = oci_containerengine_node_pool.octotask_ai_node_pool.id
}

output "existing_cluster_id" {
  description = "OCID del cluster OKE existente usado por OctoTaskAI"
  value       = var.existingOkeClusterId
}

output "nodepool_subnet_id" {
  description = "OCID de la subnet usada por el node pool"
  value       = var.existingNodePoolSubnetId
}