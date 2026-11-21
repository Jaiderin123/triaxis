# Azure resources of the deployment (ADR 0010): one resource group, an AKS cluster with one node and the free tier
# of the control plane, the static public IP of the ingress with its DNS label, and an optional budget.
# The workloads are applied afterwards with Kustomize (deploy/k8s/overlays/aks).

locals {
  tags = {
    project = "triaxis"
    purpose = "demonstration"
  }
}

resource "azurerm_resource_group" "triaxis" {
  name     = var.resource_group_name
  location = var.location
  tags     = local.tags
}

resource "azurerm_kubernetes_cluster" "triaxis" {
  name                = var.cluster_name
  location            = azurerm_resource_group.triaxis.location
  resource_group_name = azurerm_resource_group.triaxis.name
  dns_prefix          = "triaxis"
  sku_tier            = "Free"
  kubernetes_version  = var.kubernetes_version

  default_node_pool {
    name                        = "system"
    vm_size                     = var.node_size
    node_count                  = 1
    os_disk_size_gb             = var.node_os_disk_gb
    os_disk_type                = "Managed"
    temporary_name_for_rotation = "systemtmp"

    # The values AKS sets by itself; declared so that a later plan shows no change.
    upgrade_settings {
      max_surge                     = "10%"
      drain_timeout_in_minutes      = 0
      node_soak_duration_in_minutes = 0
    }
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin      = "azure"
    network_plugin_mode = "overlay"
    load_balancer_sku   = "standard"
  }

  tags = local.tags
}

# In the node resource group, so the cluster can attach it to the load balancer of Traefik without an extra role
# (annotation service.beta.kubernetes.io/azure-pip-name in the aks overlay). Static: the address and the host stay
# the same when the cluster is stopped and started.
resource "azurerm_public_ip" "ingress" {
  name                = "triaxis-ingress-ip"
  resource_group_name = azurerm_kubernetes_cluster.triaxis.node_resource_group
  location            = azurerm_resource_group.triaxis.location
  allocation_method   = "Static"
  sku                 = "Standard"
  domain_name_label   = var.dns_label
  tags                = local.tags
}

resource "azurerm_consumption_budget_subscription" "triaxis" {
  count           = var.budget_contact_email == "" ? 0 : 1
  name            = "triaxis-budget"
  subscription_id = "/subscriptions/${var.subscription_id}"
  amount          = var.budget_amount
  time_grain      = "Monthly"

  time_period {
    start_date = formatdate("YYYY-MM-01'T'00:00:00Z", timestamp())
  }

  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      enabled        = true
      threshold      = notification.value
      operator       = "GreaterThanOrEqualTo"
      threshold_type = "Actual"
      contact_emails = [var.budget_contact_email]
    }
  }

  lifecycle {
    # The start date is computed once; later runs must not move it.
    ignore_changes = [time_period]
  }
}
