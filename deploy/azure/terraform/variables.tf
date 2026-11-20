variable "subscription_id" {
  description = "Azure subscription that holds the deployment (az account show --query id -o tsv)."
  type        = string
}

variable "location" {
  description = "Azure region. It must match the host of deploy/k8s/overlays/aks."
  type        = string
  default     = "eastus2"
}

variable "resource_group_name" {
  description = "Resource group of the deployment; deleting it removes everything (ADR 0010)."
  type        = string
  default     = "triaxis-rg"
}

variable "cluster_name" {
  description = "Name of the AKS cluster."
  type        = string
  default     = "triaxis-aks"
}

variable "node_size" {
  description = "Size of the single node: 2 vCPU and 8 GiB is the cheapest size that fits the services, Kafka, PostgreSQL and Redis."
  type        = string
  default     = "Standard_B2ms"
}

variable "node_os_disk_gb" {
  description = "Operating system disk of the node, in GiB."
  type        = number
  default     = 64
}

variable "kubernetes_version" {
  description = "Kubernetes version of AKS; null takes the default version of the region."
  type        = string
  default     = null
}

variable "dns_label" {
  description = "DNS label of the public IP: the host is <dns_label>.<location>.cloudapp.azure.com (A48). It must match the host of deploy/k8s/overlays/aks."
  type        = string
  default     = "triaxis-demo"
}

variable "budget_amount" {
  description = "Monthly budget of the subscription, in its billing currency."
  type        = number
  default     = 30
}

variable "budget_contact_email" {
  description = "Address that receives the budget alerts; empty creates no budget (it can also be created in the portal)."
  type        = string
  default     = ""
}
