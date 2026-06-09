resource "oci_containerengine_node_pool" "octotask_ai_node_pool" {
  compartment_id     = var.ociCompartmentOcid
  cluster_id         = var.existingOkeClusterId
  kubernetes_version = var.kubernetesVersion
  name               = "${var.projectName}-node-pool"
  node_shape         = var.nodeShape

  node_shape_config {
    ocpus         = var.nodeOcpus
    memory_in_gbs = var.nodeMemoryGbs
  }

  node_config_details {
    size = var.nodeCount

    placement_configs {
      availability_domain = data.oci_identity_availability_domain.ad1.name
      subnet_id           = var.existingNodePoolSubnetId
    }
  }

  node_source_details {
    source_type = "IMAGE"
    image_id    = local.oracle_linux_images[0]
  }

  ssh_public_key = var.sshPublicKey
}

data "oci_containerengine_node_pool_option" "octotask_ai_node_pool_option" {
  node_pool_option_id = "all"
}

locals {
  all_sources = data.oci_containerengine_node_pool_option.octotask_ai_node_pool_option.sources

  oracle_linux_images = [
    for source in local.all_sources :
    source.image_id
    if length(regexall("Oracle-Linux-[0-9]*.[0-9]*-20[0-9]*", source.source_name)) > 0
  ]
}