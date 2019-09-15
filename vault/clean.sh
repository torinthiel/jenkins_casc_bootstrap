#!/bin/bash

. config.sh

docker kill $VAULT_CONTAINER_NAME
docker wait $VAULT_CONTAINER_NAME
docker volume rm $VAULT_DATA_VOLUME $VAULT_LOG_VOLUME
