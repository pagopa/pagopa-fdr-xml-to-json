# pagoPA Functions fdr-xml-to-json-fn

Java fdr-re-to-datastore Azure Function.
The function aims to dump RE sent via Azure Event Hub to a CosmosDB, with a TTL of 120 days, and to an Azure Table Storage with a TTL of 10 years.

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-fdr-re-to-datastore&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-fdr-re-to-datastore)


---

## Run locally with Docker
`docker build -t pagopa-functions-fdr-xml-to-json .`

`docker run -it -rm -p 8999:80 pagopa-functions-fdr-xml-to-json`

### Test
`curl http://localhost:8999/example`

## Run locally with Maven

In order to autogenerate the required classes, please run the command:  
`mvn clean package`

In order to test the Azure Function in local environment, please run the command:
`mvn azure-functions:run`

### Test
`curl http://localhost:7071/example`

---
