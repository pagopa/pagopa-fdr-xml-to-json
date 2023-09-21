# .identity

<!-- BEGINNING OF PRE-COMMIT-TERRAFORM DOCS HOOK -->
## Requirements

| Name | Version |
|------|---------|
| <a name="requirement_terraform"></a> [terraform](#requirement\_terraform) | >=1.3.0 |
| <a name="requirement_azuread"></a> [azuread](#requirement\_azuread) | 2.30.0 |
| <a name="requirement_azurerm"></a> [azurerm](#requirement\_azurerm) | 3.45.0 |
| <a name="requirement_github"></a> [github](#requirement\_github) | 5.18.3 |

## Modules

| Name | Source | Version |
|------|--------|---------|
| <a name="module_github_runner_app"></a> [github\_runner\_app](#module\_github\_runner\_app) | git::https://github.com/pagopa/github-actions-tf-modules.git//app-github-runner-creator | main |

## Resources

| Name | Type |
|------|------|
| [azurerm_key_vault_access_policy.ad_kv_domain_group_policy](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/resources/key_vault_access_policy) | resource |
| [azurerm_key_vault_access_policy.ad_kv_group_policy](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/resources/key_vault_access_policy) | resource |
| [azurerm_role_assignment.environment_key_vault](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/resources/role_assignment) | resource |
| [azurerm_role_assignment.environment_key_vault_domain](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/resources/role_assignment) | resource |
| [azurerm_role_assignment.environment_terraform_resource_group_dashboards](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/resources/role_assignment) | resource |
| [github_actions_environment_secret.github_environment_runner_secrets](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/resources/actions_environment_secret) | resource |
| [github_actions_environment_variable.github_environment_runner_variables](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/resources/actions_environment_variable) | resource |
| [github_actions_secret.secret_bot_token](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/resources/actions_secret) | resource |
| [github_actions_secret.secret_cucumber_token](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/resources/actions_secret) | resource |
| [github_actions_secret.secret_sonar_token](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/resources/actions_secret) | resource |
| [github_repository_environment.github_repository_environment](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/resources/repository_environment) | resource |
| [null_resource.github_runner_app_permissions_to_namespace](https://registry.terraform.io/providers/hashicorp/null/latest/docs/resources/resource) | resource |
| [azurerm_client_config.current](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/client_config) | data source |
| [azurerm_key_vault.domain_key_vault](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/key_vault) | data source |
| [azurerm_key_vault.key_vault](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/key_vault) | data source |
| [azurerm_key_vault_secret.key_vault_bot_token](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/key_vault_secret) | data source |
| [azurerm_key_vault_secret.key_vault_cucumber_token](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/key_vault_secret) | data source |
| [azurerm_key_vault_secret.key_vault_sa_connection_string](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/key_vault_secret) | data source |
| [azurerm_key_vault_secret.key_vault_sonar](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/key_vault_secret) | data source |
| [azurerm_kubernetes_cluster.aks](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/kubernetes_cluster) | data source |
| [azurerm_resource_group.dashboards](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/resource_group) | data source |
| [azurerm_subscription.current](https://registry.terraform.io/providers/hashicorp/azurerm/3.45.0/docs/data-sources/subscription) | data source |
| [github_organization_teams.all](https://registry.terraform.io/providers/integrations/github/5.18.3/docs/data-sources/organization_teams) | data source |

## Inputs

| Name | Description | Type | Default | Required |
|------|-------------|------|---------|:--------:|
| <a name="input_env"></a> [env](#input\_env) | n/a | `string` | n/a | yes |
| <a name="input_env_short"></a> [env\_short](#input\_env\_short) | n/a | `string` | n/a | yes |
| <a name="input_github_repository_environment"></a> [github\_repository\_environment](#input\_github\_repository\_environment) | GitHub Continuous Integration roles | <pre>object({<br>    protected_branches     = bool<br>    custom_branch_policies = bool<br>    reviewers_teams        = list(string)<br>  })</pre> | <pre>{<br>  "custom_branch_policies": true,<br>  "protected_branches": false,<br>  "reviewers_teams": [<br>    "pagopa-team-core"<br>  ]<br>}</pre> | no |
| <a name="input_prefix"></a> [prefix](#input\_prefix) | n/a | `string` | `"pagopa"` | no |

## Outputs

No outputs.
<!-- END OF PRE-COMMIT-TERRAFORM DOCS HOOK -->
