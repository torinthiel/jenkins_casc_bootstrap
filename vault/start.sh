#!/bin/bash

. config.sh

docker run \
	--rm \
	--detach \
	--publish 8200:8200 \
	--name $VAULT_CONTAINER_NAME \
	--mount type=bind,source=`pwd`/vault.hcl,destination=/vault/config/vault.hcl \
	--mount type=volume,source=$VAULT_DATA_VOLUME,destination=/vault/file \
	--mount type=volume,source=$VAULT_LOG_VOLUME,destination=/vault/logs \
	vault:$VAULT_TAG server
