locals {
  product = "${var.prefix}-${var.env_short}"
  project = "${var.prefix}-${var.env_short}-${var.location_short}-${var.domain}"

  apim = {
    name       = "${local.product}-apim"
    rg         = "${local.product}-api-rg"
  }

  hostname = var.hostname

  apim_fdr_xml_to_json_service_api = {
    display_name          = "FDR - XML to JSON API REST"
    description           = "FDR - XML to JSON API REST"
    path                  = "fdr-xml-to-json/service"
    subscription_required = true
    service_url           = null
  }

  fdr_internal = {
    project_id   = "fdr_internal"
  }
}
