# data "azurerm_storage_account" "tf_storage_account"{
#   name                = "pagopainfraterraform${var.env}"
#   resource_group_name = "io-infra-rg"
# }

# data "azurerm_resource_group" "dashboards" {
#   name = "dashboards"
# }

# data "azurerm_kubernetes_cluster" "aks" {
#   name                = local.aks_cluster.name
#   resource_group_name = local.aks_cluster.resource_group_name
# }

data "github_organization_teams" "all" {
  root_teams_only = true
  summary_only    = true
}

data "azurerm_key_vault" "key_vault" {
  name                = "pagopa-${var.env_short}-kv"
  resource_group_name = "pagopa-${var.env_short}-sec-rg"
}

data "azurerm_key_vault" "domain_key_vault" {
  name                = "pagopa-${var.env_short}-${local.domain}-kv"
  resource_group_name = "pagopa-${var.env_short}-${local.domain}-sec-rg"
}

data "azurerm_key_vault_secret" "key_vault_sonar" {
  name         = "sonar-token"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_bot_token" {
  name         = "pagopa-platform-domain-github-bot-cd-pat"
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

data "azurerm_key_vault_secret" "key_vault_deploy_slack_webhook" {
  name         = "pagopa-pagamenti-deploy-slack-webhook"
  key_vault_id = data.azurerm_key_vault.domain_key_vault.id
}

# data "azurerm_resource_group" "fdr_rg" {
#   name  = "pagopa-${var.env_short}-${local.location_short}-fdr-rg"
# }

data "azurerm_user_assigned_identity" "workload_identity_clientid" {
  name                = "fdr-workload-identity"
  resource_group_name = "pagopa-${var.env_short}-weu-${var.env}-aks-rg"
}

data "azurerm_user_assigned_identity" "identity_cd_01"{
  name = "${local.prefix}-${var.env_short}-${local.domain}-job-01-github-cd-identity"
  resource_group_name = "${local.prefix}-${var.env_short}-identity-rg"
}