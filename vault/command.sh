#!/bin/bash

. config.sh

IFS=' '

docker run \
	--rm \
	--link $VAULT_CONTAINER_NAME:vault \
	vault:$VAULT_TAG sh -c 'VAULT_ADDR=http://$VAULT_PORT_8200_TCP_ADDR:$VAULT_PORT_8200_TCP_PORT vault '"$*"
