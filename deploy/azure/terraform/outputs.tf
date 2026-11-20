output "fqdn" {
  description = "Public host of the deployment; it must equal the host of deploy/k8s/overlays/aks."
  value       = azurerm_public_ip.ingress.fqdn
}

output "ingress_ip" {
  description = "Static public IP of the ingress."
  value       = azurerm_public_ip.ingress.ip_address
}

output "resource_group" {
  value = azurerm_resource_group.triaxis.name
}

output "cluster_name" {
  value = azurerm_kubernetes_cluster.triaxis.name
}

output "get_credentials" {
  description = "Command that points kubectl at the cluster."
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.triaxis.name} --name ${azurerm_kubernetes_cluster.triaxis.name} --overwrite-existing"
}
