/**************************
  FDR-XML-TO-JSON API
***************************/

resource "azurerm_api_management_api_version_set" "api_fdr_xml_to_json_api" {
  name                = "${var.env_short}-fdr-xml-to-json-service-api"
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.apim_fdr_xml_to_json_service_api.display_name
  versioning_scheme   = "Segment"
}

module "apim_api_fdr_xml_to_json_api_v1" {
  source = "git::https://github.com/pagopa/terraform-azurerm-v3.git//api_management_api?ref=v6.7.0"

  name                  = "${local.project}-fdr-xml-to-json-service-api"
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  product_ids           = [local.fdr_internal.project_id]
  subscription_required = true

  version_set_id = azurerm_api_management_api_version_set.api_fdr_xml_to_json_api.id
  api_version    = "v1"

  description  = local.apim_fdr_xml_to_json_service_api.description
  display_name = local.apim_fdr_xml_to_json_service_api.display_name
  path         = local.apim_fdr_xml_to_json_service_api.path
  protocols    = ["https"]

  service_url  = local.apim_fdr_xml_to_json_service_api.service_url

  content_format = "openapi"
  content_value  = templatefile("../openapi/openapi.json", {
    service = local.fdr_internal.project_id
  })

  xml_content = templatefile("./policy/_base_policy.xml", {
    hostname = var.hostname
  })
}